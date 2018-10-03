# BIOSTimingsEditor
Java Swing GUI for editing AMD GPU VRAM timings

![Screenshot](https://puu.sh/BDjpP/f2a1bc0313.png)

## Purpose
This program is made for pre-Polaris GPUs which didn't have a GUI for editing VRAM timings and would have to resort to using a hex editor.

## To-do
* ~~Automatically find VRAM_Info offset to make finding the timings more consistent~~ Done
* ~~Identify compatible and incompatible BIOSes~~ Done
* Identify VRAM IC names
* ~~Possibly decode the timings, be able to edit each timing and re-encode them~~ [Done](https://github.com/integralfx/BIOSTimingsDecoder)

## Credits
* [caa82437 - PolarisBiosEditor](https://github.com/caa82437/PolarisBiosEditor)
* [OneB1t - HawaiiBiosReader](https://github.com/OneB1t/HawaiiBiosReader)
* [Hedzin - AtomBiosReader](https://github.com/Hedzin/AtomBiosReader)
