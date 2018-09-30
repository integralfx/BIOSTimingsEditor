# BIOSTimingsEditor
Java Swing GUI for editing AMD GPU VRAM timings

## Purpose
This program is made for pre-Polaris GPUs which didn't have a GUI for editing VRAM timings and would have to resort to using a hex editor.

## To-do
* Automatically find VRAM_Info offset to make finding the timings more consistent
* Identify compatible and incompatible BIOSes
* Identify VRAM IC names
* Possibly decode the timings, be able to edit each timing and re-encode them

## Credits
* [OneB1t - HawaiiBiosReader](https://github.com/OneB1t/HawaiiBiosReader)
