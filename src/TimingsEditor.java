import java.io.IOException;
import java.lang.IllegalArgumentException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class TimingsEditor
{
    // TODO: handle VRAM_MODULE_V7
    public static void main(String[] args)
    {
        final String bios_name = "Sapphire.R9380X.4096.151101.rom";

        TimingsEditor te = new TimingsEditor(bios_name);
        te.get_timings();
    }

    private static void print_timings(ATOM_VRAM_TIMING_ENTRY e)
    {
        System.out.println(String.format("%d\t%d", e.ulClkRange, e.ucIndex));
        for(byte b : e.ucLatency)
            System.out.print(String.format("0x%02X ", b));
    }

    public TimingsEditor(String bios_file) throws IllegalArgumentException
    {
        Path path = Paths.get(bios_file);
        try
        {
            bios_bytes = Files.readAllBytes(path);
            if(!init())
                throw new IllegalArgumentException("Invalid BIOS file");
        }
        catch(IOException e)
        {
            System.err.println("failed to read " + bios_file);
            e.printStackTrace();
        }
    }

    private boolean init()
    {
        // find rom header
        byte[] rom_header_needle = { (byte)0x24, (byte)0x00, (byte)0x01, (byte)0x01 };
        int rom_header_offset = find_bytes(bios_bytes, rom_header_needle);
        if(rom_header_offset == -1)
        {
            System.err.println("failed to find ATOM_ROM_HEADER");
            return false;
        }
        byte[] rom_header_bytes = new byte[ATOM_ROM_HEADER.size];
        System.arraycopy(bios_bytes, rom_header_offset, rom_header_bytes, 0, ATOM_ROM_HEADER.size);
        rom_header = new ATOM_ROM_HEADER(rom_header_bytes);

        // get master data table
        byte[] master_data_table_bytes = new byte[ATOM_MASTER_DATA_TABLE.size];
        System.arraycopy(bios_bytes, rom_header.usMasterDataTableOffset, 
                            master_data_table_bytes, 0, ATOM_MASTER_DATA_TABLE.size);                                                            
        master_data_table = new ATOM_MASTER_DATA_TABLE(master_data_table_bytes);

        return true;
    }

    public ArrayList<ATOM_VRAM_TIMING_ENTRY> get_timings()
    {
        ATOM_VRAM_INFO vram_info = get_vram_info();
        // for(ATOM_VRAM_MODULE m : vram_info.sModules)
        // {
        //     switch(vram_info.ucVramModuleVer)
        //     {
        //     case 7:
        //         System.out.println(((ATOM_VRAM_MODULE_V7)m).strMemPNString);
        //         break;
        //     case 8:
        //         System.out.println(((ATOM_VRAM_MODULE_V8)m).strMemPNString);
        //         break;
        //     }
        // }

        // all of vram_info structure
        byte[] vram_info_bytes = new byte[vram_info.sHeader.usStructureSize];
        System.arraycopy(bios_bytes, master_data_table.VRAM_Info, 
                         vram_info_bytes, 0, vram_info.sHeader.usStructureSize);

        // find the 400MHz strap
        byte[] needle = { (byte)0x40, (byte)0x9C, (byte)0x00 };
        VRAM_Timings_offset = find_bytes(vram_info_bytes, needle);
        if(VRAM_Timings_offset == -1)
        {
            System.err.println("failed to find 400MHz strap in BIOS");
            return null;
        }
        /* 
         * + master_data_table.VRAM_Info, as find_bytes will return the offset relative to vram_info_bytes
         * but VRAM_Timings_offset is absolute
         */
        VRAM_Timings_offset += master_data_table.VRAM_Info;

        ArrayList<ATOM_VRAM_TIMING_ENTRY> vram_timing_entries = new ArrayList<>();
        // unknown length, 32 should be more than enough
        for(int i = 0, offset = VRAM_Timings_offset; i < 32; i++, offset += ATOM_VRAM_TIMING_ENTRY.size)
        {
            byte[] vram_timing_entry_bytes = new byte[ATOM_VRAM_TIMING_ENTRY.size];
            System.arraycopy(bios_bytes, offset, vram_timing_entry_bytes, 0, ATOM_VRAM_TIMING_ENTRY.size);

            ATOM_VRAM_TIMING_ENTRY vram_timing_entry = new ATOM_VRAM_TIMING_ENTRY(vram_timing_entry_bytes);

            if(vram_timing_entry.ulClkRange == 0) break;

            vram_timing_entries.add(vram_timing_entry);
        }

        return vram_timing_entries;
    }

    public ATOM_VRAM_INFO get_vram_info()
    {
        // get vram info
        byte[] vram_info_header_bytes = new byte[ATOM_COMMON_TABLE_HEADER.size];
        System.arraycopy(bios_bytes, master_data_table.VRAM_Info, 
                            vram_info_header_bytes, 0, ATOM_COMMON_TABLE_HEADER.size);
        ATOM_COMMON_TABLE_HEADER vram_info_header = new ATOM_COMMON_TABLE_HEADER(vram_info_header_bytes);
        byte[] vram_info_full_bytes = new byte[vram_info_header.usStructureSize];
        System.arraycopy(bios_bytes, master_data_table.VRAM_Info, 
                            vram_info_full_bytes, 0, vram_info_header.usStructureSize);
        
        return new ATOM_VRAM_INFO(vram_info_full_bytes);
    }

    /*
     * finds timings.ulClkRange and timings.ucIndex in bios_bytes and
     * overwrites ucLatency in bios_bytes with timings.ucLatency
     * returns false, if it isn't found
     * otherwise, returns true
     */
    public boolean set_timings(ATOM_VRAM_TIMING_ENTRY timings)
    {
        ArrayList<ATOM_VRAM_TIMING_ENTRY> curr_timings = get_timings();
        if(curr_timings == null) return false;

        boolean found = false;
        for(ATOM_VRAM_TIMING_ENTRY e : curr_timings)
        {
            if(e.ulClkRange == timings.ulClkRange && e.ucIndex == timings.ucIndex)
                found = true;
        }
        if(!found)
        {
            System.err.println(String.format("failed to find timings for index %d %dkHz", 
                timings.ucIndex, timings.ulClkRange));
            return false;
        }

        // find the strap
        byte[] needle = {
            (byte)(timings.ulClkRange & 0xFF),
            (byte)((timings.ulClkRange >> 8) & 0xFF),
            (byte)((timings.ulClkRange >> 16) & 0xFF),
            timings.ucIndex
        };
        int offset = find_bytes(bios_bytes, needle);
        if(offset == -1)
        {
            System.err.println(String.format("failed to find the needle for index %d %dkHz", 
                timings.ucIndex, timings.ulClkRange));
            return false;
        }
        else
        {
            // overwrite timings
            System.arraycopy(timings.ucLatency, 0, bios_bytes, offset + needle.length, timings.ucLatency.length);
            return true;
        }
    }

    /*
     * writes bios_bytes to new_bios_file
     * returns true if succesful, false otherwise
     */
    public boolean save_bios(String new_bios_file)
    {
        Path path = Paths.get(new_bios_file);
        try
        {
            fix_checksum();

            Files.write(path, bios_bytes);
        }
        catch(IOException e)
        {
            System.err.println("failed to write to " + new_bios_file);
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private void fix_checksum()
    {
        final int atom_rom_checksum_offset = 0x21;

        int size = Byte.toUnsignedInt(bios_bytes[2]) * 512;
        byte new_checksum = 0;
        for(int i = 0; i < size; i++)
            new_checksum += bios_bytes[i];

        if(new_checksum != 0)
            bios_bytes[atom_rom_checksum_offset] -= (byte)new_checksum;
    }

    /*
     * converts the 2 bytes at offset to an unsigned 16 bit value
     * bytes are in little endian
     * returns an int as java doesn't have an unsigned 16 bit value
     */
    private int bytes_to_uint16(byte[] bytes, int offset)
    {
        return Byte.toUnsignedInt(bytes[offset + 1]) << 8 | Byte.toUnsignedInt(bytes[offset]);
    }

    private long bytes_to_uint32(byte[] bytes, int offset)
    {
        return Byte.toUnsignedLong(bytes[offset + 3]) << 24 |
               Byte.toUnsignedLong(bytes[offset + 2]) << 16 |
               Byte.toUnsignedLong(bytes[offset + 1]) << 8 |
               Byte.toUnsignedLong(bytes[offset]);
    }

    /*
     * finds the first occurrence of needle in haystack
     * returns the index to the start of needle in haystack
     * returns -1 if not found
     * maybe replace with KMP algorithm?
     */
    private int find_bytes(byte[] haystack, byte[] needle)
    {
        if(haystack == null || needle == null)
            return -1;

        if(haystack.length == 0 || needle.length == 0)
            return -1;

        if(haystack.length < needle.length)
            return -1;

        for(int i = 0; i < haystack.length - needle.length; i++)
        {
            boolean found = true;
            for(int j = 0; j < needle.length; j++)
            {
                if(haystack[i + j] != needle[j])
                {
                    found = false; break;
                }
            }

            if(found) return i;
        }

        return -1;
    }

    class ATOM_ROM_HEADER
    {
        public static final int size = 36, ATOM_BIOS_SIGNATURE = 0x4D4F5441;

        public ATOM_COMMON_TABLE_HEADER sHeader;
        public int ulFirmwareSignature;             // 4 bytes
        // vvv 2 bytes vvv
        public int usBIOSRuntimeSegmentAddress;
        public int usProtectedModeInfoOffset;
        public int usConfigFilenameOffset;
        public int usCRCBlockOffset;
        public int usBIOSBootupMessageOffset;
        public int usInt10Offset;
        public int usPCIBusDevInitiCode;
        public int usIOBaseAddress;
        public int usSubsystemVendorID;
        public int usSubsystemID;
        public int usPCIInfoOffset;
        public int usMasterCommandTableOffset;
        public int usMasterDataTableOffset;
        // ^^^ 2 bytes ^^^
        public short ucExtendedFunctionCode;
        public short ucReserved;

        public ATOM_ROM_HEADER(byte[] bytes) throws IllegalArgumentException
        {
            if(bytes.length != size)
            {
                throw new IllegalArgumentException(
                    String.format(
                        "ATOM_ROM_HEADER: expected %d bytes, got %d bytes", 
                        size, bytes.length
                    )
                );
            }

            sHeader = new ATOM_COMMON_TABLE_HEADER(Arrays.copyOf(bytes, ATOM_COMMON_TABLE_HEADER.size));
            int i = ATOM_COMMON_TABLE_HEADER.size;
            ulFirmwareSignature = (int)bytes_to_uint32(bytes, i); i += 4;
            if(ulFirmwareSignature != ATOM_BIOS_SIGNATURE)
                throw new IllegalArgumentException("invalid BIOS");
            usBIOSRuntimeSegmentAddress = bytes_to_uint16(bytes, i); i += 2;
            usProtectedModeInfoOffset = bytes_to_uint16(bytes, i); i += 2;
            usConfigFilenameOffset = bytes_to_uint16(bytes, i); i += 2;
            usCRCBlockOffset = bytes_to_uint16(bytes, i); i += 2;
            usBIOSBootupMessageOffset = bytes_to_uint16(bytes, i); i += 2;
            usInt10Offset = bytes_to_uint16(bytes, i); i += 2;
            usPCIBusDevInitiCode = bytes_to_uint16(bytes, i); i += 2;
            usIOBaseAddress = bytes_to_uint16(bytes, i); i += 2;
            usSubsystemVendorID = bytes_to_uint16(bytes, i); i += 2;
            usSubsystemID = bytes_to_uint16(bytes, i); i += 2;
            usPCIInfoOffset = bytes_to_uint16(bytes, i); i += 2;
            usMasterCommandTableOffset = bytes_to_uint16(bytes, i); i += 2;
            usMasterDataTableOffset = bytes_to_uint16(bytes, i); i += 2;
            ucExtendedFunctionCode = bytes[i++];
            ucReserved = bytes[i++];
        }
    }

    class ATOM_MASTER_DATA_TABLE
    {
        public static final int size = 74;

        public ATOM_COMMON_TABLE_HEADER sHeader;
        // vvv 2 bytes vvv
        public int UtilityPipeLine;
        public int MultimediaCapabilityInfo;
        public int MultimedaConfigInfo;
        public int StandardVESATiming;
        public int FirmwareInfo;
        public int PaletteData;
        public int LCD_Info;
        public int DIGTransmitterInfo;
        public int AnalogTV_Info;
        public int SupportedDevicesInfo;
        public int GPIO_I2C_Info;
        public int VRAMUsageByFirmware;
        public int GPIO_Pin_LUT;
        public int VESAToInternalModeLUT;
        public int ComponentVideoInfo;
        public int PowerPlayInfo;
        public int GPUVirtualizationInfo;
        public int SaveRestoreInfo;
        public int PPLL_SS_Info;
        public int OEMInfo;
        public int XTMDS_Info;
        public int MclkSS_Info;
        public int Object_Header;
        public int IndirectIOAccess;
        public int MC_InitParameter;
        public int ASIC_VDDC_Info;
        public int ASIC_InternalSS_Info;
        public int TV_VideoMode;
        public int VRAM_Info;
        public int MemoryTrainingInfo;
        public int IntegratedSystemInfo;
        public int ASIC_ProfilingInfo;
        public int VoltageObjectInfo;
        public int PowerSourceInfo;
        public int ServiceInfo;

        public ATOM_MASTER_DATA_TABLE(byte[] bytes)
        {
            if(bytes.length != size) 
            {
                throw new IllegalArgumentException(
                    String.format(
                        "ATOM_MASTER_DATA_TABLE: expected %d bytes, got %d bytes", 
                        size, bytes.length
                    )
                );
            }

            sHeader = new ATOM_COMMON_TABLE_HEADER(Arrays.copyOf(bytes, ATOM_COMMON_TABLE_HEADER.size));
            int i = ATOM_COMMON_TABLE_HEADER.size;
            UtilityPipeLine = bytes_to_uint16(bytes, i); i += 2;
            MultimediaCapabilityInfo = bytes_to_uint16(bytes, i); i += 2;
            MultimedaConfigInfo = bytes_to_uint16(bytes, i); i += 2;
            StandardVESATiming = bytes_to_uint16(bytes, i); i += 2;
            FirmwareInfo = bytes_to_uint16(bytes, i); i += 2;
            PaletteData = bytes_to_uint16(bytes, i); i += 2;
            LCD_Info = bytes_to_uint16(bytes, i); i += 2;
            DIGTransmitterInfo = bytes_to_uint16(bytes, i); i += 2;
            AnalogTV_Info = bytes_to_uint16(bytes, i); i += 2;
            SupportedDevicesInfo = bytes_to_uint16(bytes, i); i += 2;
            GPIO_I2C_Info = bytes_to_uint16(bytes, i); i += 2;
            VRAMUsageByFirmware = bytes_to_uint16(bytes, i); i += 2;
            GPIO_Pin_LUT = bytes_to_uint16(bytes, i); i += 2;
            VESAToInternalModeLUT = bytes_to_uint16(bytes, i); i += 2;
            ComponentVideoInfo = bytes_to_uint16(bytes, i); i += 2;
            PowerPlayInfo = bytes_to_uint16(bytes, i); i += 2;
            GPUVirtualizationInfo = bytes_to_uint16(bytes, i); i += 2;
            SaveRestoreInfo = bytes_to_uint16(bytes, i); i += 2;
            PPLL_SS_Info = bytes_to_uint16(bytes, i); i += 2;
            OEMInfo = bytes_to_uint16(bytes, i); i += 2;
            XTMDS_Info = bytes_to_uint16(bytes, i); i += 2;
            XTMDS_Info = bytes_to_uint16(bytes, i); i += 2;
            MclkSS_Info = bytes_to_uint16(bytes, i); i += 2;
            Object_Header = bytes_to_uint16(bytes, i); i += 2;
            IndirectIOAccess = bytes_to_uint16(bytes, i); i += 2;
            MC_InitParameter = bytes_to_uint16(bytes, i); i += 2;
            ASIC_VDDC_Info = bytes_to_uint16(bytes, i); i += 2;
            TV_VideoMode = bytes_to_uint16(bytes, i); i += 2;
            VRAM_Info = bytes_to_uint16(bytes, i); i += 2;
            MemoryTrainingInfo = bytes_to_uint16(bytes, i); i += 2;
            IntegratedSystemInfo = bytes_to_uint16(bytes, i); i += 2;
            ASIC_ProfilingInfo = bytes_to_uint16(bytes, i); i += 2;
            VoltageObjectInfo = bytes_to_uint16(bytes, i); i += 2;
            PowerSourceInfo = bytes_to_uint16(bytes, i); i += 2;
            ServiceInfo = bytes_to_uint16(bytes, i); i += 2;
        }
    }

    class ATOM_COMMON_TABLE_HEADER
    {
        public static final int size = 4;

        public int usStructureSize;     // 2 bytes
        public byte ucTableFormatRevision;
        public byte ucTableContentRevision;

        public ATOM_COMMON_TABLE_HEADER(byte[] bytes) throws IllegalArgumentException
        {
            if(bytes.length != size)
            {
                throw new IllegalArgumentException(
                    String.format(
                        "ATOM_COMMON_TABLE_HEADER: expected %d bytes, got %d bytes", 
                        size, bytes.length
                    )
                );
            }

            usStructureSize = bytes_to_uint16(bytes, 0);
            ucTableFormatRevision = bytes[2];
            ucTableContentRevision = bytes[3];
        }
    }

    // v2.2
    class ATOM_VRAM_INFO
    {
        public final int size;

        public ATOM_COMMON_TABLE_HEADER sHeader;
        // vvv 2 bytes vvv -> java doesn't have unsigned :/
        public int usMemAdjustTblOffset;
        public int usMemClkPatchTblOffset;
        public int usMcAdjustPerTileTblOffset;
        public int usMcPhyInitTableOffset;
        public int usDramDataRemapTblOffset;
        public int usReserved1;
        // ^^^ 2 bytes ^^^
        public byte ucNumOfVRAMModule;
        public byte ucMemoryClkPatchTblVer;
        public byte ucVramModuleVer;
        public byte ucMcPhyTileNum;
        public ATOM_VRAM_MODULE[] sModules;

        public ATOM_VRAM_INFO(byte[] bytes) throws IllegalArgumentException
        {
            sHeader = new ATOM_COMMON_TABLE_HEADER(Arrays.copyOf(bytes, ATOM_COMMON_TABLE_HEADER.size));
            int i = ATOM_COMMON_TABLE_HEADER.size;
            usMemAdjustTblOffset = bytes_to_uint16(bytes, i); i += 2;
            usMemClkPatchTblOffset = bytes_to_uint16(bytes, i); i += 2;
            usMcAdjustPerTileTblOffset = bytes_to_uint16(bytes, i); i += 2;
            usMcPhyInitTableOffset = bytes_to_uint16(bytes, i); i += 2;
            usDramDataRemapTblOffset = bytes_to_uint16(bytes, i); i += 2;
            usReserved1 = bytes_to_uint16(bytes, i); i += 2;
            ucNumOfVRAMModule = bytes[i++];
            ucMemoryClkPatchTblVer = bytes[i++];
            ucVramModuleVer = bytes[i++];
            ucMcPhyTileNum = bytes[i++];
            sModules = new ATOM_VRAM_MODULE[ucNumOfVRAMModule];
            int total = 0;
            for(int j = 0; j < ucNumOfVRAMModule; j++)
            {
                ATOM_VRAM_MODULE_HEADER header = new ATOM_VRAM_MODULE_HEADER(
                    Arrays.copyOfRange(bytes, i, i + 6)
                );

                switch(ucVramModuleVer)
                {
                case 7:
                    sModules[j] = new ATOM_VRAM_MODULE_V7(Arrays.copyOfRange(bytes, i, i + header.usModuleSize));
                    break;
                case 8:
                    sModules[j] = new ATOM_VRAM_MODULE_V8(Arrays.copyOfRange(bytes, i, i + header.usModuleSize));
                    break;
                default:
                    throw new IllegalArgumentException("ATOM_VRAM_INFO: unknown module version: " + ucVramModuleVer);
                }
                
                i += header.usModuleSize;
                total += header.usModuleSize;
            }
            size = ATOM_COMMON_TABLE_HEADER.size + 16 + total;
        }
    }
    
    // v8
    class ATOM_VRAM_MODULE_HEADER
    {
        public static final int size = 6;

        public long ulChannelMapCfg;
        public int usModuleSize;

        public ATOM_VRAM_MODULE_HEADER(byte[] bytes) throws IllegalArgumentException
        {
            if(bytes.length != size)
            {
                throw new IllegalArgumentException(
                    String.format(
                        "ATOM_VRAM_MODULE_HEADER: expected %d bytes, got %d bytes", 
                        size, bytes.length
                    )
                );
            }

            int i = 0;
            ulChannelMapCfg = bytes_to_uint32(bytes, i); i += 4;
            usModuleSize = bytes_to_uint16(bytes, i); i += 2;
        }
    }

    class ATOM_VRAM_MODULE
    {

    }

    class ATOM_VRAM_MODULE_V7 extends ATOM_VRAM_MODULE
    {
        // size is sHeader.usModuleSize
        public final int size;

        ATOM_VRAM_MODULE_HEADER sHeader;
        public int usPrivateReserved;
        public int usEnableChannels;
        public byte ucExtMemoryID;
        public byte ucMemoryType;
        public byte ucChannelNum;
        public byte ucChannelWidth;
        public byte ucDensity;
        public byte ucReserve;            
        public byte ucMisc;
        public byte ucVREFI;
        public byte ucNPL_RT;
        public byte ucPreamble;
        public byte ucMemorySize;
        public int usSEQSettingOffset;
        public byte ucReserved;
        public int usEMRS2Value;
        public int usEMRS3Value;
        public byte ucMemoryVenderID;
        public byte ucRefreshRateFactor;
        public byte ucFIFODepth;
        public byte ucCDR_Bandwidth;
        public String strMemPNString;  // up to 20 bytes

        public ATOM_VRAM_MODULE_V7(byte[] bytes)
        {
            int i = 0;
            sHeader = new ATOM_VRAM_MODULE_HEADER(Arrays.copyOf(bytes, ATOM_VRAM_MODULE_HEADER.size)); 
            size = sHeader.usModuleSize;
            i += ATOM_VRAM_MODULE_HEADER.size;
            usPrivateReserved = bytes_to_uint16(bytes, i); i += 2;
            usEnableChannels = bytes_to_uint16(bytes, i); i += 2;
            ucExtMemoryID = bytes[i++];
            ucMemoryType = bytes[i++];
            ucChannelNum = bytes[i++];
            ucChannelWidth = bytes[i++];
            ucDensity = bytes[i++];
            ucReserve = bytes[i++];
            ucMisc = bytes[i++];
            ucVREFI = bytes[i++];
            ucNPL_RT = bytes[i++];
            ucPreamble = bytes[i++];
            ucMemorySize = bytes[i++];
            usSEQSettingOffset = bytes_to_uint16(bytes, i); i += 2;
            ucReserved = bytes[i++];
            usEMRS2Value = bytes_to_uint16(bytes, i); i += 2;
            usEMRS3Value = bytes_to_uint16(bytes, i); i += 2;
            ucMemoryVenderID = bytes[i++];
            ucRefreshRateFactor = bytes[i++];
            ucFIFODepth = bytes[i++];
            ucCDR_Bandwidth = bytes[i++];
            // read VRAM IC if there is one
            int delta = sHeader.usModuleSize - i;
            if(delta > 1) strMemPNString = new String(bytes, i, delta);
        }
    }

    class ATOM_VRAM_MODULE_V8 extends ATOM_VRAM_MODULE
    {
        // size is sHeader.usModuleSize
        public final int size;

        public ATOM_VRAM_MODULE_HEADER sHeader;
        public int usMcRamCfg;
        public int usEnableChannels;
        public byte ucExtMemoryID;
        public byte ucMemoryType;
        public byte ucChannelNum;
        public byte ucChannelWidth;
        public byte ucDensity;
        public byte ucBankCol;
        public byte ucMisc;
        public byte ucVREFI;
        public int usReserved;
        public int usMemorySize;
        public byte ucMcTunningSetId;
        public byte ucRowNum;
        public int usEMRS2Value;
        public int usEMRS3Value;
        public byte ucMemoryVendorID;
        public byte ucRefreshRateFactor;
        public byte ucFIFODepth;
        public byte ucCDR_Bandwidth;
        public long ulChannelMapCfg1;
        public long ulBankMapCfg;
        public long ulReserved;
        public String strMemPNString;   // 12 bytes

        public ATOM_VRAM_MODULE_V8(byte[] bytes) throws IllegalArgumentException
        {
            int i = 0;
            sHeader = new ATOM_VRAM_MODULE_HEADER(Arrays.copyOf(bytes, ATOM_VRAM_MODULE_HEADER.size)); 
            size = sHeader.usModuleSize;
            i += ATOM_VRAM_MODULE_HEADER.size;
            usMcRamCfg = bytes_to_uint16(bytes, i); i += 2;
            usEnableChannels = bytes_to_uint16(bytes, i); i += 2;
            ucExtMemoryID = bytes[i++];
            ucMemoryType = bytes[i++];
            ucChannelNum = bytes[i++];
            ucChannelWidth = bytes[i++];
            ucDensity = bytes[i++];
            ucBankCol = bytes[i++];
            ucMisc = bytes[i++];
            ucVREFI = bytes[i++];
            usReserved = bytes_to_uint16(bytes, i); i += 2;
            usMemorySize = bytes_to_uint16(bytes, i); i += 2;
            ucMcTunningSetId = bytes[i++];
            ucRowNum = bytes[i++];
            usEMRS2Value = bytes_to_uint16(bytes, i); i += 2;
            usEMRS3Value = bytes_to_uint16(bytes, i); i += 2;
            ucMemoryVendorID = bytes[i++];
            ucRefreshRateFactor = bytes[i++];
            ucFIFODepth = bytes[i++];
            ucCDR_Bandwidth = bytes[i++];
            ulChannelMapCfg1 = bytes_to_uint32(bytes, i); i += 4;
            ulBankMapCfg = bytes_to_uint32(bytes, i); i += 4;
            ulReserved = bytes_to_uint32(bytes, i); i += 4;
            // read VRAM IC if there is one
            int delta = sHeader.usModuleSize - i;
            if(delta > 1) strMemPNString = new String(bytes, i, delta);
        }
    }

    // don't think this is for pre-polaris BIOSes
    class ATOM_VRAM_ENTRY
    {
        public static final int size = 64;

        public long ulChannelMapCfg;    // uint32
        public int usModuleSize;        // uint16
        public int usMcRamCfg;          // uint16
        public int usEnableChannels;    // uint16
        public byte ucExtMemoryID;
        public byte ucMemoryType;
        public byte ucChannelNum;
        public byte ucChannelWidth;
        public byte ucDensity;
        public byte ucBankCol;
        public byte ucMisc;
        public byte ucVREFI;
        public int usReserved;          // uint16
        public int usMemorySize;        // uint16
        public byte ucMcTunningSetId;
        public byte ucRowNum;
        public int usEMRS2Value;        // uint16
        public int usEMRS3Value;        // uint16
        public byte ucMemoryVenderID;
        public byte ucRefreshRateFactor;
        public byte ucFIFODepth;
        public byte ucCDR_Bandwidth;
        public long ulChannelMapCfg1;   // uint32
        public long ulBankMapCfg;       // uint32
        public long ulReserved;         // uint32
        public final byte[] strMemPNString = new byte[20];   // 20 bytes

        public ATOM_VRAM_ENTRY(byte[] bytes) throws IllegalArgumentException
        {
            if(bytes.length != size)
            {
                throw new IllegalArgumentException(
                    String.format(
                        "ATOM_VRAM_ENTRY: expected %d bytes, got %d bytes", 
                        size, bytes.length
                    )
                );
            }

            int i = 0;
            ulChannelMapCfg = bytes_to_uint32(bytes, i); i += 4;
            usModuleSize = bytes_to_uint16(bytes, i); i += 2;
            usMcRamCfg = bytes_to_uint16(bytes, i); i += 2;
            usEnableChannels = bytes_to_uint16(bytes, i); i += 2;
            ucExtMemoryID = bytes[i++];
            ucMemoryType = bytes[i++];
            ucChannelNum = bytes[i++];
            ucChannelWidth = bytes[i++];
            ucDensity = bytes[i++];
            ucBankCol = bytes[i++];
            ucMisc = bytes[i++];
            ucVREFI = bytes[i++];
            usReserved = bytes_to_uint16(bytes, i); i += 2;
            usMemorySize = bytes_to_uint16(bytes, i); i += 2;
            ucMcTunningSetId = bytes[i++];
            ucRowNum = bytes[i++];
            usEMRS2Value = bytes_to_uint16(bytes, i); i += 2;
            usEMRS3Value = bytes_to_uint16(bytes, i); i += 2;
            ucMemoryVenderID = bytes[i++];
            ucRefreshRateFactor = bytes[i++];
            ucFIFODepth = bytes[i++];
            ucCDR_Bandwidth = bytes[i++];
            ulChannelMapCfg1 = bytes_to_uint32(bytes, i); i += 4;
            ulBankMapCfg = bytes_to_uint32(bytes, i); i += 4;
            ulReserved = bytes_to_uint32(bytes, i); i += 4;
            System.arraycopy(bytes, i, strMemPNString, 0, 20);
        }
    }

    class ATOM_VRAM_TIMING_ENTRY
    {
        public static final int size = 0x34;

        public int ulClkRange;  // unsigned int, 3 bytes, in 10kHz
        public byte ucIndex;
        public final byte[] ucLatency = new byte[0x30];

        public ATOM_VRAM_TIMING_ENTRY(byte[] bytes) throws IllegalArgumentException
        {
            if(bytes.length != size)
            {
                throw new IllegalArgumentException(
                    String.format(
                        "ATOM_VRAM_TIMING_ENTRY: expected %d bytes, got %d bytes", 
                        size, bytes.length
                    )
                );
            }

            ulClkRange = Byte.toUnsignedInt(bytes[2]) << 16 | 
                         Byte.toUnsignedInt(bytes[1]) << 8 | 
                         Byte.toUnsignedInt(bytes[0]);
            ucIndex = bytes[3];
            System.arraycopy(bytes, 4, ucLatency, 0, 0x30);
        }
    }

    private byte[] bios_bytes;
    private int VRAM_Timings_offset;
    private ATOM_ROM_HEADER rom_header;
    private ATOM_MASTER_DATA_TABLE master_data_table;
}