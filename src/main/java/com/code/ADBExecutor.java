
package com.code;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ADBExecutor{
    //manually: adb shell pm uninstall -k --user 0 com.x
    private final String INSTALL_COMMAND_1 = "adb shell cmd package install-existing PACKAGE";
    private final String INSTALL_COMMAND_2 = "adb shell pm install-existing PACKAGE";
    private final String DISABLED_APPS_COMMAND = "adb shell pm list packages -d";
    private final String DISABLE_APP_COMMAND_1 = "pm disable PACKAGE";
    private final String DISABLE_APP_COMMAND_2 = "pm disable-user PACKAGE";

    private final String[] UNINSTALL_KEEP_COMMAND = {"", "shell", "pm" ,"uninstall", "-k", "--user 0", ""};
    private final String[] UNINSTALL_FULL_COMMAND = {"", "shell", "pm", "uninstall", "--user 0", ""};
    private final String[] PACKAGE_LIST_COMMAND = {"", "shell", "pm" ,"list", "packages"};
    private final String[] DEVICES_COMMANDS = {"", "devices"};

    private List<String> allBloatedPackages;

    private ProcessBuilder procBuilder;
    private Scanner scanner;

    private InstalledPackages allPackages;
    private boolean fallback, missingPackages;

    protected ADBExecutor(String adbDir){
        adbDir = Utilities.normalizeStringPath(adbDir);
        String adbSuffix = "/adb";
        if(adbDir.endsWith("/")){
            adbSuffix = "adb";
        }
        String adb = adbDir + adbSuffix;
        DEVICES_COMMANDS[0] = adb;
        PACKAGE_LIST_COMMAND[0] =  adb;
        UNINSTALL_KEEP_COMMAND[0] = adb;
        UNINSTALL_FULL_COMMAND[0] = adb;

        procBuilder = new ProcessBuilder();
        procBuilder.directory(new File(adbDir));
        scanner = new Scanner(System.in);
        //bloated packages file
        URL url = ADBMain.class.getResource("/packages.txt");
        if(url == null){
            System.err.println("Missing package names");
            missingPackages = true;
            return;
        }
        try{
            InputStream packagesStream = url.openStream();
            String readLines = Utilities.readFully(packagesStream);
            allBloatedPackages = Utilities.readAllLines(readLines);
        }catch (IOException ioException){
            System.err.println("Error reading packages from file.. exiting");
            return;
        }
        System.out.println(allBloatedPackages.size() + " packages loaded");
        System.out.println(allBloatedPackages);
    }

    protected void run(){
        runDevicesStage();
        Mode mode = selectMode();
        runUninstallStage(mode);
    }

    private void runDevicesStage(){
        String output = executeCommand(DEVICES_COMMANDS);
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
        System.out.println("For full app removal not keeping data and cache directories around after package removal," +
                " include f after the number (1f or 2f).");

        String response = scanner.nextLine().toLowerCase();
        boolean isValid = Character.isDigit(response.charAt(0));
        if(!isValid){
            return selectMode();
        }

        boolean isFull = response.length() == 2 && response.charAt(1) == 'f';
        int mode = response.charAt(0) - 48;
        if((missingPackages && mode != 1) || mode < 1 || mode > 2){
            return selectMode();
        }
        scanner.reset();
        return new Mode(mode, isFull);
    }

    private void mode1(boolean isFull){
        System.out.println("Provide package name:");
        String pckg = scanner.nextLine();
        String result = isFull ? uninstallPackageFully(pckg) : uninstallPackage(pckg);
        System.out.println(result);
        mode1(isFull);
    }

    private void runUninstallStage(Mode mode){
        switch (mode.ordinal){
            case 1:
                mode1(mode.full);
                break;
            case 2:
                String output = executeCommand(PACKAGE_LIST_COMMAND);
                if(output.startsWith("java.lang.UnsatisfiedLinkError")){
                    System.out.println("'pm list packages' command failed");
                    fallback = true;
                }else if(output.startsWith("package")){
                    allPackages = new InstalledPackages(output);
                    if(!missingPackages){
                        allPackages.resolveBloated(allBloatedPackages);
                    }
                }else{
                    fallback = true;
                    System.err.println(output);
                }
                break;
        }

        if(fallback){
            System.out.println("Uninstall possibly " + allBloatedPackages.size() + " packages? (y/n)");
        }else{
            if(allPackages.bloatedCount() == 0){
                System.out.println("No bloated packages found on the device. Exiting ..");
                restoreCommandInfo();
                return;
            }
            System.out.println(allPackages.bloatedSet());
            System.out.println("Uninstall " + (mode.full ? "fully " : "") + allPackages.bloatedCount() + " packages? (y/n)");
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

        for (String currentPackage : allBloatedPackages){
            if(usePrefix && !currentPackage.startsWith(prefix)){
                continue;
            }

            if(!fallback && !allPackages.isBloated(currentPackage)){
                continue;
            }

            String output = mode.full ? uninstallPackageFully(currentPackage) : uninstallPackage(currentPackage);
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
        System.out.println(INSTALL_COMMAND_1 + " or " + INSTALL_COMMAND_2);
    }

    public String uninstallPackage(String pckgName){
        UNINSTALL_KEEP_COMMAND[UNINSTALL_KEEP_COMMAND.length - 1] = pckgName;
        return executeCommand(UNINSTALL_KEEP_COMMAND);
    }
    public String uninstallPackageFully(String pckgName){
        UNINSTALL_FULL_COMMAND[UNINSTALL_FULL_COMMAND.length - 1] = pckgName;
        return executeCommand(UNINSTALL_FULL_COMMAND);
    }
    private String executeCommand(String[] commands){
        procBuilder.command(commands);
        try{
            Process processAlgo = procBuilder.start();
            processAlgo.waitFor(3, TimeUnit.SECONDS);
            return Utilities.readFully(processAlgo.getInputStream());
        }catch (IOException | InterruptedException exceptions){
            exceptions.printStackTrace();
        }
        return "";
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