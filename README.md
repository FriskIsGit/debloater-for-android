## Package uninstaller for android
Root privileges are not required for the uninstaller to work <br>
Most packages in the list are from google, xiaomi and samsung.
### Requirements:
```
- JDK 8 or higher
- adb (Android Debug Bridge, part of platform tools)
```

### Platform tools download links:
From:
https://developer.android.com/studio/releases/platform-tools

 - Windows: https://dl.google.com/android/repository/platform-tools-latest-windows.zip
 - Linux: https://dl.google.com/android/repository/platform-tools-latest-linux.zip
 - Mac: https://dl.google.com/android/repository/platform-tools-latest-darwin.zip

**Important:**
> 
> Before running make sure that your device has USB debugging enabled (found in developer options),
> the device will prompt for authorization, check `Always allow from this computer` and select `OK`
> 
> If you've done this correctly your device should appear in the list of connected devices (run  `adb devices`)
> 
> If your system does not have proper drivers to communicate with or recognize an Android device
> you can download them from this website: https://developer.android.com/studio/run/win-usb

## How to run from terminal
```bash
# Clone this repository
$ git clone https://github.com/FriskIsGit/debloater-for-android

# Navigate to project's root
$ cd debloater-for-android

# On Linux run
$ ./rerun.sh
# On Windows run
$ rerun.bat
```

## Commands

## ADB compatibility
Refer to release notes: https://developer.android.com/tools/releases/platform-tools
<br>You can download specific versions by modifying this url:
https://dl.google.com/android/repository/platform-tools_r34.0.4-windows.zip

version 34.0.5 (October 2023) and above come bundled with new DbC interface for ChromeOS
which may cause adb to crash on older systems

## Exports/Imports
It's possible to export app data
```shell
./run export-data com.package.name
```
and import with
```shell
./run import-data com.package.name
```
but the use is limited if the app uses (hardware-backed) Android Keystore.
Simply clearing app's data from settings causes the OS to reset the data for this app in the keystore as well, making the backup useless.
