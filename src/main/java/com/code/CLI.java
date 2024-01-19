
package com.code;

import java.io.*;
import java.net.URL;
import java.util.*;

public class CLI {
    private static final String PACKAGES_SRC = "/packages.txt";
    private static final boolean SIMULATE_DEVICE = false;


    private ADBCommands commands;
    private List<String> bloatedPackages;
    private Scanner scanner;
    private Set<String> packages;
    private boolean error_fallback, missingPackages;


    public static void start(ADBCommands commands) {
        CLI cli = new CLI();
        cli.commands = commands;
        cli.loadAndInit();
        cli.start();
    }

    protected void loadAndInit() {
        scanner = new Scanner(System.in);
        //bloated packages db file
        URL url = ADBMain.class.getResource(PACKAGES_SRC);
        if (url == null) {
            System.err.println("Couldn't find/load: " + PACKAGES_SRC);
            missingPackages = true;
            return;
        }
        try {
            InputStream packagesStream = url.openStream();
            String readLines = Utilities.readFully(packagesStream);
            bloatedPackages = Utilities.readAllLines(readLines);
        } catch (IOException ioException) {
            System.err.println("Error reading packages with file.. exiting");
            return;
        }
        System.out.println(bloatedPackages.size() + " packages loaded from packages.txt:");
        System.out.println(bloatedPackages);
    }

    protected void start() {
        runDevicesStage();
        Mode mode = selectMode();
        while(true) {
            boolean rerun = runMode(mode);
            if (!rerun) {
                mode = selectMode();
            }
        }
    }

    private void runDevicesStage() {
        String output = commands.listDevices();
        System.out.println(output);
        int devices = devicesConnected(output);
        System.out.println(devices + (devices == 1 ? " connected device" : " connected devices"));
        if (SIMULATE_DEVICE) {
            return;
        }
        if (devices == 0) {
            System.out.println("No devices detected (is 'USB debugging' enabled?), press enter to refresh");
            if (scanner.hasNextLine()) {
                scanner.nextLine();
            }
            runDevicesStage();
        } else if (devices > 1) {
            System.err.println("Error: more than one device/emulator");
        }
    }

    private Mode selectMode() {
        System.out.println("Select number:");
        System.out.println("----------------");
        System.out.println("#1 Uninstall package by name");
        if (!missingPackages) {
            System.out.println("#2 Uninstall bloated packages listed in packages.txt (will prompt before proceeding)");
        }
        System.out.println("note: For full app removal - deleting data and cache directories of an app," +
                " include f after the number (1f or 2f).");

        System.out.println("#3 Export many apks. Include a - all, user - u, system - s, after the number (default: u)");
        System.out.println("#4 Export an apk by package name.");

        if (!missingPackages) {
            System.out.println("#5 Install apps from packages.txt one by one (debug broken functionality)," +
                    " include f after the number to install all without prompting (streamline)");
        }

        System.out.println("#6 Install apps from /export one by one");

        Mode mode;
        while (true) {
            String response = scanner.nextLine().toLowerCase();
            while (response.isEmpty()) {
                response = scanner.nextLine().toLowerCase();
            }
            mode = Mode.parse(response, missingPackages);
            if (mode == null) {
                System.out.println("Invalid mode specified");
            } else {
                break;
            }
        }
        System.out.println("Mode selected: " + mode);
        scanner.reset();
        return mode;
    }

    private void mode1(boolean isFull) {
        System.out.println("Provide package name:");
        String pckg = scanner.nextLine();
        String result = isFull ? commands.uninstallPackageFully(pckg) : commands.uninstallPackage(pckg);
        System.out.println(result);
    }

    private int devicesConnected(String output) {
        int newLine = output.indexOf('\n');
        if (newLine == -1) {
            return 0;
        }
        int devices = 0;
        for (int i = newLine + 1, len = output.length() - 6; i < len; i++) {
            if (output.startsWith("device", i)) {
                devices++;
            }
            // else "unauthorized" or "authorizing"
        }
        return devices;
    }

    private boolean runMode(Mode mode) {
        switch (mode.ordinal) {
            case 1:
                mode1(mode.full);
                return true;
            case 2:
                mode2(mode.full);
                break;
            case 3: {
                mode3(mode);
                break;
            }
            case 4: {
                mode4();
                return true;
            }
            case 5: {
                mode5(mode.full);
                break;
            }
            case 6: {
                mode6();
                break;
            }
        }
        return false;
    }

    private void mode6() {
        File export = new File("export");
        if (!export.exists()) {
            System.err.println("Create an export or put an .apk in export/com.app.name/");
            return;
        }
        File[] apkDirs = export.listFiles(File::isDirectory);
        if (apkDirs == null || apkDirs.length == 0) {
            System.err.println("There's nothing to install back.");
            return;
        }
        System.out.println("Install possibly " + apkDirs.length + " APKs? (y/n)");
        if (!scanner.nextLine().toLowerCase().startsWith("y")) {
            return;
        }


        for (File apkDir : apkDirs) {
            System.out.println("Installing " + apkDir.getName());
            File[] apks = apkDir.listFiles(file -> file.isFile() && file.getName().endsWith(".apk"));
            assert apks != null;
            if (apks.length == 1) {
                String output = commands.install(apks[0].getPath());
                System.out.println(output);
                continue;
            }

            String[] apkPaths = new String[apks.length];
            int index = 0;
            for(File apk : apks) {
                apkPaths[index] = apk.getPath();
                index++;
            }
            String installOutput = commands.installMultiple(apkPaths);
            System.out.println(installOutput);
        }
    }

    private void mode4() {
        File export = new File("export");
        if (!export.exists() && !export.mkdirs()) {
            System.err.println("Unable to create export directory");
            return;
        }
        System.out.println("Provide package name to export: ");
        String pckg = scanner.nextLine();
        String output = commands.getPackagePath(pckg);
        if (output.isEmpty()) {
            System.err.println(pckg + " doesn't exist?");
            return;
        }
        String[] apks = output.split("\\r?\\n");
        if (apks.length == 0) {
            System.err.println("Nothing to export.");
            return;
        }
        for (int i = 0; i < apks.length; i++) {
            // remove package: prefix from apk path
            if(apks[i].length() < 8) {
                continue;
            }
            apks[i] = apks[i].substring(8);
        }
        File packageExport = new File("./export/" + pckg);
        if (!packageExport.exists() && !packageExport.mkdirs()) {
            System.err.println("Unable to create " + pckg + " directory");
            return;
        }
        for (String apk : apks) {
            if(apk.isEmpty()) {
                continue;
            }
            String pullOutput = commands.pullAPK(apk, "./export/" + pckg);
            System.out.println(pullOutput);
        }
    }

    private void mode3(Mode mode) {
        if (mode.type == PackageType.INAPPLICABLE) {
            System.err.println("Invalid package type.. exiting");
            System.exit(0);
        }
        String output = commands.listPackagesBy(mode.type);
        if (output.startsWith("package")) {
            packages = Packages.parse(output);
        }
        else if (output.startsWith("java.lang.UnsatisfiedLinkError")) {
            System.err.println("'pm list packages' command failed - can't export");
            this.start();
            return;
        } else {
            System.err.println(output);
            this.start();
            return;
        }
        int pulled = 0, errors = 0;
        File export = new File("export");
        if (!export.exists() && !export.mkdirs()) {
            System.err.println("Unable to create export directory");
            return;
        }
        System.out.println(packages);
        System.out.println("Backing up " + packages.size() + " packages, proceed? (y/n)");
        if (!scanner.nextLine().toLowerCase().startsWith("y")) {
            return;
        }
        int counter = 1;
        long st = System.currentTimeMillis();
        for (String pckg : packages) {
            output = commands.getPackagePath(pckg);
            if (output.isEmpty()) {
                System.err.println(pckg + " is incorrectly displayed by the package manager as an existing package");
                continue;
            }
            String[] apks = output.split("\\r?\\n");
            for (int i = 0; i < apks.length; i++) {
                // remove package: prefix from apk path
                apks[i] = apks[i].substring(8);
            }
            File packageExport = new File("./export/" + pckg);
            if (!packageExport.exists() && !packageExport.mkdirs()) {
                System.err.println("Unable to create " + pckg + " directory");
                return;
            }
            for (String apk : apks) {
                String pullOutput = commands.pullAPK(apk, "./export/" + pckg);
                System.out.println(pullOutput);
                if (pullOutput.startsWith("adb: error:")) {
                    errors++;
                }
                else {
                    pulled++;
                }
            }
            long now = System.currentTimeMillis();
            System.out.println("Packages exported: " + counter + " | Pulls: " + pulled + " | Errors: " + errors + " | " + (now-st) + " ms elapsed");
            counter++;
        }
        long end = System.currentTimeMillis();
        System.out.println("Time taken: " + (end-st) + " ms");
    }

    private String getAPKName(String apkPath) {
        int appIndex = apkPath.indexOf("app");
        if (apkPath.charAt(appIndex + 4) == '~') {
            // base64 path
            int nameStart = apkPath.indexOf('/', appIndex + 4);
            int nameEnd = apkPath.indexOf('-', nameStart + 2);
            return apkPath.substring(nameStart + 1, nameEnd);
        } else {
            // unobfuscated
            int nameEnd = apkPath.indexOf('/', appIndex + 4);
            return apkPath.substring(appIndex + 4, nameEnd);
        }
    }

    private void mode5(boolean full) {
        int success = 0, fail = 0;
        for (String bloated : bloatedPackages) {
            System.out.println("Attempting install of: " + bloated);
            String output = commands.installPackage(bloated, 56);
            if (output.contains("Success") || output.startsWith("Package")) {
                success++;
                if (full)
                    continue;
            } else if (output.startsWith("android.content.pm.PackageManager$NameNotFoundException")) {
                fail++;
                continue;
            } else if (output.isEmpty()) {
                System.err.println("Unauthorized/timed out");
            } else if (output.startsWith("Error: unknown command") || output.startsWith("/system/bin/sh: cmd: not found")) {
                System.err.println("Install-existing is not a command recognized by Android");
            } else {
                System.err.println(output);
            }

            System.out.println("Results[success:" + success + ',' + " fail:" + fail + ']');
            if (scanner.hasNextLine()) {
                scanner.nextLine();
            }
        }
    }

    private void mode2(boolean full) {
        String output = commands.listPackages();
        if (output.startsWith("package")) {
            packages = Packages.parse(output);
            System.out.println("Found " + packages.size() + " packages installed on device.");
            // retain these that are installed
            bloatedPackages.retainAll(packages);
        }
        else if (output.startsWith("java.lang.UnsatisfiedLinkError")) {
            System.out.println("'pm list packages' command failed");
            error_fallback = true;
        } else {
            error_fallback = true;
            System.err.println(output);
        }
        if (error_fallback) {
            System.out.println("Uninstall possibly " + bloatedPackages.size() + " packages? (y/n)");
        } else {
            if (bloatedPackages.isEmpty()) {
                System.out.println("No bloated packages found on the device. Exiting ..");
                restoreCommandInfo();
                return;
            }
            System.out.println(bloatedPackages);
            System.out.println("Uninstall " + (full ? "fully " : "") + bloatedPackages.size() + " packages? (y/n)");
        }

        boolean usePrefix = false;

        String prefix = scanner.nextLine();
        if (!prefix.startsWith("y")) {
            if (!prefix.startsWith("n")) {
                System.out.println("Exiting");
                System.exit(0);
            }
            System.out.println("Uninstall only those starting with:");
            prefix = scanner.nextLine();
            if (prefix.isEmpty()) {
                System.out.println("Exiting");
                System.exit(0);
            }
            usePrefix = true;
        }
        long start = System.currentTimeMillis();
        int fail = 0;
        int success = 0;

        for (String currentPackage : bloatedPackages) {
            if (usePrefix && !currentPackage.startsWith(prefix)) {
                continue;
            }

            output = full ? commands.uninstallPackageFully(currentPackage) : commands.uninstallPackage(currentPackage);
            if (output.startsWith("Success")) {
                success++;
                System.out.println("Deleted: " + currentPackage);
                continue;
            } else if (output.startsWith("Error") || output.startsWith("Failure")) {
                fail++;
            }

            if (!output.isEmpty()) {
                System.out.println(output);
            }
        }
        long end = System.currentTimeMillis();
        System.out.println("Completed in: " + (double) (end - start) / 1000 + " seconds");
        System.out.println("Packages uninstalled: " + success);
        System.out.println("Failures: " + fail);
        restoreCommandInfo();
    }

    private void restoreCommandInfo() {
        System.out.println("If you wish to install back any deleted system packages try running command below:");
        System.out.println(ADBCommands.INSTALL_COMMAND_1 + " or " + ADBCommands.INSTALL_COMMAND_2);
    }
}

class Mode {
    private static final int MIN_MODE = 1;
    private static final int MAX_MODE = 6;
    public int ordinal;
    public boolean full = false;
    public PackageType type = PackageType.USER;

    public static Mode parse(String response, boolean missingPackages) {
        if (response.isEmpty() || !Character.isDigit(response.charAt(0))) {
            return null;
        }
        if (response.length() == 1) {
            return new Mode(response.charAt(0) - 48);
        }

        int modeNum = response.charAt(0) - 48;
        if (modeNum < MIN_MODE || modeNum > MAX_MODE) {
            return null;
        }
        // don't allow selection of modes for which packages.txt is required
        if ((missingPackages && (modeNum == 2 || modeNum == 5))) {
            return null;
        }

        char secondChr = response.charAt(1);
        PackageType type;
        switch (secondChr) {
            case 's':
                type = PackageType.SYSTEM;
                break;
            case 'u':
                type = PackageType.USER;
                break;
            case 'a':
                type = PackageType.ALL;
                break;
            default:
                type = PackageType.INAPPLICABLE;
        }
        return new Mode(modeNum, secondChr == 'f', type);
    }

    private Mode(int ordinal) {
        this.ordinal = ordinal;
    }

    private Mode(int ordinal, boolean full, PackageType pckgType) {
        this.ordinal = ordinal;
        this.full = full;
        this.type = pckgType;
    }

    @Override
    public String toString() {
        String fullLabel = (full ? " full" : "");
        String packageLabel = (type != PackageType.INAPPLICABLE ? type.toString() : "");
        return "#" + ordinal +  fullLabel + " " + packageLabel;
    }
}
