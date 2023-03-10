
package com.code;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ADBExecutor{
    //manually: adb shell pm uninstall -k --user 0 com.x
    private final static String INSTALL_COMMAND_1 = "adb shell cmd package install-existing com.group.example";
    private final static String INSTALL_COMMAND_2 = "adb shell pm install-existing com.group.example";
    private final static String DISABLED_APPS_COMMAND = "adb shell pm list packages -d";
    private final static String DISABLE_APP_COMMAND = "pm disable com.group.example";

    private final static String[] UNINSTALL_COMMAND = {"", "shell", "pm" ,"uninstall", "-k", "--user 0", ""};
    private final static String[] PACKAGE_LIST_COMMAND = {"", "shell", "pm" ,"list", "packages"};
    private final static String[] DEVICES_COMMANDS = {"", "devices"};

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
        UNINSTALL_COMMAND[0] = adb;

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
        int mode = selectMode();
        runUninstallStage(mode);
    }

    private void runDevicesStage(){
        String output = executeCommand(DEVICES_COMMANDS);
        System.out.println(output);
        int devices = devicesConnected(output);
        System.out.println(devices + (devices == 1 ? " connected device" : " connected devices"));
        if(devices == 0){
            System.out.println("No devices detected (is debugging via USB enabled?), press enter to refresh");
            while(scanner.hasNextLine()){
                scanner.nextLine();
            }
            runDevicesStage();
        }
    }

    private int selectMode(){
        System.out.println("Select number:");
        System.out.println("#1 Uninstall package by name");
        if(!missingPackages){
            System.out.println("#2 Find all installed bloated packages to cut down on waiting time");
        }

        String response = scanner.nextLine().toLowerCase();
        boolean isValid = response.length() == 1 && Character.isDigit(response.charAt(0));
        if(!isValid){
            return selectMode();
        }
        int mode = response.charAt(0) - 48;
        if((missingPackages && mode != 1) || mode < 1 || mode > 2){
            return selectMode();
        }
        scanner.reset();
        return mode;
    }

    private void mode1(){
        System.out.println("Provide package name:");
        String pckg = scanner.nextLine();
        String result = uninstallPackage(pckg);
        System.out.println(result);
        mode1();
    }

    private void runUninstallStage(int mode){
        switch (mode){
            case 1:
                mode1();
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
            System.out.println("Uninstall " + allPackages.bloatedCount() + " packages? (y/n)");
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

            String output = uninstallPackage(currentPackage);
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
        UNINSTALL_COMMAND[UNINSTALL_COMMAND.length - 1] = pckgName;
        return executeCommand(UNINSTALL_COMMAND);
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
            if(output.charAt(i) == 'd' && output.charAt(i+1) == 'e' && output.charAt(i+2) == 'v'
            && output.charAt(i+3) == 'i' && output.charAt(i+4) == 'c' && output.charAt(i+5) == 'e'){
                devices++;
            }
        }
        return devices;
    }
}