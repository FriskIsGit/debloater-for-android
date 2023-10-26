
package com.code;

import java.io.*;
import java.net.URL;
import java.util.*;

public class CLI{
    private static final String PACKAGES_SRC = "/packages.txt";

    private ADBCommands commands;
    private List<String> bloatedPackages;
    private Scanner scanner;
    private Packages packages;
    private boolean error_fallback, missingPackages;


    public static void run(ADBCommands commands){
        CLI cli = new CLI();
        cli.commands = commands;
        cli.loadAndInit();
        cli.run();
    }

    protected void loadAndInit(){
        scanner = new Scanner(System.in);
        //bloated packages db file
        URL url = ADBMain.class.getResource(PACKAGES_SRC);
        if(url == null){
            System.err.println("Couldn't find/load: " + PACKAGES_SRC);
            missingPackages = true;
            return;
        }
        try{
            InputStream packagesStream = url.openStream();
            String readLines = Utilities.readFully(packagesStream);
            bloatedPackages = Utilities.readAllLines(readLines);
        }catch (IOException ioException){
            System.err.println("Error reading packages with file.. exiting");
            return;
        }
        System.out.println(bloatedPackages.size() + " loaded with ");
        System.out.println(bloatedPackages);
    }

    protected void run(){
        runDevicesStage();
        Mode mode = selectMode();
        runMode(mode);
    }

    private void runDevicesStage(){
        String output = commands.listDevices();
        System.out.println(output);
        int devices = devicesConnected(output);
        System.out.println(devices + (devices == 1 ? " connected device" : " connected devices"));
        if(devices == 0){
            System.out.println("No devices detected (is debugging via USB enabled?), press enter to refresh");
            if(scanner.hasNextLine()){
                scanner.nextLine();
            }
            runDevicesStage();
        }
    }

    private Mode selectMode(){
        System.out.println("Select number:");
        System.out.println("#1 Uninstall package by name");
        if(!missingPackages){
            System.out.println("#2 Find all installed bloated packages to cut down on waiting time");
        }
        System.out.println("For full app removal - deleting data and cache directories of an app," +
                " include f after the number (1f or 2f).");

        System.out.println("#3 Install apps (debug issues associated with broken functionalities) one by one," +
                " include f after the number to install all without prompting (streamline)");

        String response = scanner.nextLine().toLowerCase();
        boolean isValid = Character.isDigit(response.charAt(0));
        if(!isValid){
            return selectMode();
        }

        boolean isFull = response.length() == 2 && response.charAt(1) == 'f';
        int mode = response.charAt(0) - 48;
        if(mode < 1 || mode > 3 || (missingPackages && (mode == 2 || mode == 3))){
            return selectMode();
        }
        scanner.reset();
        return new Mode(mode, isFull);
    }

    private void mode1(boolean isFull){
        System.out.println("Provide package name:");
        String pckg = scanner.nextLine();
        String result = isFull ? commands.uninstallPackageFully(pckg) : commands.uninstallPackage(pckg);
        System.out.println(result);
        mode1(isFull);
    }

    private int devicesConnected(String output){
        int newLine = output.indexOf('\n');
        if(newLine == -1){
            return 0;
        }
        int devices = 0;
        for (int i = newLine + 1, len = output.length()-6; i < len; i++){
            if(output.startsWith("device", i)){
                devices++;
            }
        }
        return devices;
    }

    private void runMode(Mode mode){
        switch (mode.ordinal){
            case 1:
                mode1(mode.full);
                break;
            case 2:
                mode2(mode.full);
                break;
            case 3: {
                mode3(mode.full);
                System.exit(0);
            }
        }
    }

    private void mode3(boolean full) {
        int success = 0, fail = 0, unknown = 0;
        for(String bloated : bloatedPackages) {
            System.out.println("Attempting install of: " + bloated);
            String output = commands.installPackage(bloated, 56);
            if (output.contains("Success") || output.startsWith("Package")){
                success++;
                if (full)
                    continue;
            }else if(output.startsWith("android.content.pm.PackageManager$NameNotFoundException")){
                fail++;
                continue;
            }
            else if (output.isEmpty()) {
                System.out.println("Unauthorized/timed out");
            }
            else if(output.startsWith("Error: unknown command") || output.startsWith("/system/bin/sh: cmd: not found")){
                System.out.println("Install-existing is not a command recognized by Android");
            }else{
                System.out.println(output);
            }

            System.out.println("Results[success:" + success + ',' + " fail:" + fail + ']');
            if(scanner.hasNextLine()) {
                scanner.nextLine();
            }
        }
    }

    private void mode2(boolean full){
        String output = commands.listPackages();
        if(output.startsWith("java.lang.UnsatisfiedLinkError")){
            System.out.println("'pm list packages' command failed");
            error_fallback = true;
        }else if(output.startsWith("package")){
            packages = Packages.parse(output);
            packages.resolveExisting(bloatedPackages);
        }else{
            error_fallback = true;
            System.err.println(output);
        }
        if(error_fallback){
            System.out.println("Uninstall possibly " + bloatedPackages.size() + " packages? (y/n)");
        }else{
            if(packages.bloatedCount() == 0){
                System.out.println("No bloated packages found on the device. Exiting ..");
                restoreCommandInfo();
                return;
            }
            System.out.println(packages.getInstalledBloated());
            System.out.println("Uninstall " + (full ? "fully " : "") + packages.bloatedCount() + " packages? (y/n)");
        }

        boolean usePrefix = false;

        String prefix = scanner.nextLine();
        if(!prefix.startsWith("y")){
            if(!prefix.startsWith("n")){
                System.out.println("Exiting");
                System.exit(0);
            }
            System.out.println("Uninstall only those starting with:");
            prefix = scanner.nextLine();
            if(prefix.isEmpty()){
                System.out.println("Exiting");
                System.exit(0);
            }
            usePrefix = true;
        }
        long start = System.currentTimeMillis();
        int fail = 0;
        int success = 0;

        for (String currentPackage : bloatedPackages){
            if(usePrefix && !currentPackage.startsWith(prefix)){
                continue;
            }

            if(!error_fallback && !packages.isBloated(currentPackage)){
                continue;
            }

            output = full ? commands.uninstallPackageFully(currentPackage) : commands.uninstallPackage(currentPackage);
            if(output.startsWith("Success")){
                success++;
                System.out.println("Deleted: " + currentPackage);
                continue;
            }
            else if(output.startsWith("Error") || output.startsWith("Failure")){
                fail++;
            }
            System.out.println(output);
        }
        long end = System.currentTimeMillis();
        System.out.println("Completed in: " + (double)(end-start)/1000 + " seconds");
        System.out.println("Packages uninstalled: " + success);
        System.out.println("Failures: " + fail);
        restoreCommandInfo();
    }

    private void restoreCommandInfo(){
        System.out.println("If you wish to install back any deleted system packages try running command below:");
        System.out.println(ADBCommands.INSTALL_COMMAND_1 + " or " + ADBCommands.INSTALL_COMMAND_2);
    }
}

class Mode{
    public int ordinal;
    public boolean full = false;

    public Mode(int ordinal){
        this.ordinal = ordinal;
    }

    public Mode(int ordinal, boolean full){
        this.ordinal = ordinal;
        this.full = full;
    }
}