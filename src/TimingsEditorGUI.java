import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class TimingsEditorGUI extends JFrame
{
	public static void main(String[] args)
	{
		new TimingsEditorGUI();
	}

	public TimingsEditorGUI()
	{
		super("Timings Editor");

		add_menu_bar();

		BoxLayout layout = new BoxLayout(main_panel, BoxLayout.Y_AXIS);
		main_panel.setLayout(layout);

		JPanel p = new JPanel();
		p.add(lbl_file);
		main_panel.add(p);

		setSize(300, 200);
		setResizable(false);
		setVisible(true);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		// open in center of screen
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice defaultScreen = ge.getDefaultScreenDevice();
		Rectangle rect = defaultScreen.getDefaultConfiguration().getBounds();
		int x = (int)(rect.getMaxX() - getWidth()) / 2;
		int y = (int)(rect.getMaxY() - getHeight()) / 2;
		setLocation(x, y);
	}

	private void add_menu_bar()
	{
		JMenuBar menu_bar = new JMenuBar();

		ActionListener listener = new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				JFileChooser fc = new JFileChooser();

				// start in current directory
				Path path = Paths.get("");
				fc.setCurrentDirectory(path.toAbsolutePath().toFile());

				if(e.getSource() == menu_item_open)
				{
					if(fc.showOpenDialog(main_panel) == JFileChooser.APPROVE_OPTION)
					{
						try {
							File file = fc.getSelectedFile();

							timings_editor = new TimingsEditor(file.getAbsolutePath());
							timings = timings_editor.get_timings();

							lbl_file.setText(file.getName());

							SwingUtilities.invokeLater(new Runnable()
							{
								@Override
								public void run()
								{
									if(panel_timings != null)
									{
										main_panel.remove(panel_timings);
										panel_timings = null;
									}

									if(panel_indices == null)
										add_indices_panel();
									
									add_timings_panel(timings.get(0).ucIndex);
									revalidate();
									repaint();
									pack();

									update_indices_cbox();
								}
							});
						}
						catch(IllegalArgumentException ex)
						{
							show_error_dialog(ex.getMessage());
						}
					}
				}
				else if(e.getSource() == menu_item_saveas)
				{
					if(timings_editor == null)
					{
						show_error_dialog("Please open a BIOS first");
						return;
					}

					if(fc.showSaveDialog(main_panel) == JFileChooser.APPROVE_OPTION)
					{
						String bios_file_path = fc.getSelectedFile().getAbsolutePath();

						if(timings_editor.save_bios(bios_file_path))
							show_success_dialog("Successfully saved to " + bios_file_path);
						else show_error_dialog("Failed to save BIOS");
					}
				}
			}

			private void show_error_dialog(String msg)
			{
				JOptionPane.showMessageDialog(
					main_panel,
					msg,
					"Error",
					JOptionPane.ERROR_MESSAGE
				);
			}
			
			private void show_success_dialog(String msg)
			{
				JOptionPane.showMessageDialog(
					main_panel,
					msg,
					"Success",
					JOptionPane.INFORMATION_MESSAGE
				);
			}
		};

		JMenu menu_file = new JMenu("File");
		menu_bar.add(menu_file);

		menu_item_open = new JMenuItem("Open");
		menu_item_open.addActionListener(listener);
		menu_file.add(menu_item_open);

		menu_item_saveas = new JMenuItem("Save As");
		menu_item_saveas.addActionListener(listener);
		menu_file.add(menu_item_saveas);

		setJMenuBar(menu_bar);
	}

	private void add_indices_panel()
	{
		panel_indices = new JPanel();
		BoxLayout layout = new BoxLayout(panel_indices, BoxLayout.Y_AXIS);
		panel_indices.setLayout(layout);

		JPanel p = new JPanel();
		JLabel lbl_indices = new JLabel("RAM IC Index: ");
		p.add(lbl_indices);
		cbox_indices = new JComboBox<>();
		p.add(cbox_indices);
		panel_indices.add(p);

		p = new JPanel();
		txt_vram_ic.setEditable(false);
		p.add(txt_vram_ic);
		panel_indices.add(p);

		main_panel.add(panel_indices);
	}

	private void update_indices_cbox()
	{
		if(timings == null || timings.isEmpty())
			return;

		// get unique indices
		ArrayList<String> indices = new ArrayList<>();
		for(TimingsEditor.ATOM_VRAM_TIMING_ENTRY e : timings)
		{
			String index = String.valueOf(Byte.toUnsignedInt(e.ucIndex));

			if(!indices.contains(index)) 
				indices.add(index);
		}

		DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>(indices.toArray(new String[indices.size()]));
		cbox_indices.setModel(model);
		
		cbox_indices.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e) 
			{
				byte selected = (byte)Integer.parseInt(cbox_indices.getSelectedItem().toString());

				/*
				 * remove and add as the selected RAM IC may have
				 * different frequencies compared to the current
				 */
				SwingUtilities.invokeLater(new Runnable()
				{
					@Override
					public void run()
					{
						if(panel_timings != null)
						{
							main_panel.remove(panel_timings);
							panel_timings = null;
						}
						add_timings_panel(selected);
						revalidate();
						repaint();
						pack();
					}
				});

				// update RAM IC name
				TimingsEditor.ATOM_VRAM_INFO vram_info = timings_editor.get_vram_info();
				ArrayList<String> vram_ics = new ArrayList<>();
				for(int i = 0; i < vram_info.sModules.length; i++)
				{
					switch(vram_info.ucVramModuleVer)
					{
					case 7:
					{
						TimingsEditor.ATOM_VRAM_MODULE_V7 m = (TimingsEditor.ATOM_VRAM_MODULE_V7)(vram_info.sModules[i]);
						vram_ics.add(m.strMemPNString);
						break;
					}
					case 8:
					{
						TimingsEditor.ATOM_VRAM_MODULE_V8 m = (TimingsEditor.ATOM_VRAM_MODULE_V8)(vram_info.sModules[i]);
						vram_ics.add(m.strMemPNString);
						break;
					}
					}
				}
				txt_vram_ic.setText(vram_ics.get(selected));
			}
		});

		cbox_indices.setSelectedIndex(0);
	}

	private void add_timings_panel(byte ram_ic_index)
	{
		panel_timings = new JPanel();
		BoxLayout layout = new BoxLayout(panel_timings, BoxLayout.Y_AXIS);
		panel_timings.setLayout(layout);

		for(TimingsEditor.ATOM_VRAM_TIMING_ENTRY e : timings)
		{
			if(e.ucIndex == ram_ic_index)
			{
				int freq = e.ulClkRange / 100;	// frequency in MHz
				JPanel panel_row = new JPanel(new FlowLayout());

				JLabel lbl_frequency = new JLabel(String.format("%d MHz: ", freq));
				set_width(lbl_frequency, 70);
				panel_row.add(lbl_frequency);

				JTextArea txt_timings = new JTextArea(1, 20);
				StringBuilder sb = new StringBuilder();
				for(byte b : e.ucLatency)
					sb.append(String.format("%02X", b));
				txt_timings.setText(sb.toString());
				txt_timings.setCaretPosition(0);
				txt_timings.getDocument().addDocumentListener(new DocumentListener()
				{
					@Override
					public void removeUpdate(DocumentEvent e) 
					{
						changedUpdate(e);
					}
				
					@Override
					public void insertUpdate(DocumentEvent e) 
					{
						changedUpdate(e);
					}
				
					@Override
					public void changedUpdate(DocumentEvent e) 
					{
						String input = txt_timings.getText();
			
						if(input.isEmpty()) return;
			
						if(input.matches("^[0-9A-Fa-f]{96}$"))
						{
							txt_timings.setBackground(Color.WHITE);
			
							byte selected_index = (byte)Integer.parseInt(cbox_indices.getSelectedItem().toString());
			
							TimingsEditor.ATOM_VRAM_TIMING_ENTRY new_timings = null;
							// find the timings
							for(TimingsEditor.ATOM_VRAM_TIMING_ENTRY t : timings)
							{
								if(t.ulClkRange == freq * 100 && t.ucIndex == selected_index)
								{
									new_timings = t;
									break;
								}
							}
			
							// set_timings() uses the ulClkRange and ucIndex as the "key"
							byte[] new_timings_bytes = string_to_bytes(input);
							System.arraycopy(new_timings_bytes, 0, new_timings.ucLatency, 0, new_timings_bytes.length);
							timings_editor.set_timings(new_timings);
						}
						else
						{
							txt_timings.setBackground(new Color(0xFFFFAFAF));
						}
					}
			
					private byte[] string_to_bytes(String s)
					{
						int len = s.length();
						byte[] bytes = new byte[len / 2];
						for(int i = 0; i < len ; i += 2)
						{
							bytes[i / 2] = (byte)((Character.digit(s.charAt(i), 16) << 4) +
													Character.digit(s.charAt(i + 1), 16));
						}
			
						return bytes;
					}
				});
				JScrollPane scroll = new JScrollPane(txt_timings, JScrollPane.VERTICAL_SCROLLBAR_NEVER, 
													 JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
				panel_row.add(scroll);

				panel_timings.add(panel_row);
			}
		}

		panel_timings.setBorder(BorderFactory.createTitledBorder("Straps"));

		main_panel.add(panel_timings);
	}

	private void set_width(Component c, int width)
	{
		c.setPreferredSize(new Dimension(width, c.getPreferredSize().height));
	}

	private Container main_panel = getContentPane();
	private JPanel panel_indices, panel_timings;
	private JMenuItem menu_item_open, menu_item_saveas;
	private TimingsEditor timings_editor;
	private ArrayList<TimingsEditor.ATOM_VRAM_TIMING_ENTRY> timings;
	private JComboBox<String> cbox_indices = new JComboBox<>();
	private JLabel lbl_file = new JLabel("No BIOS opened");
	private JTextField txt_vram_ic = new JTextField();
}