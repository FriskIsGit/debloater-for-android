
package com.code;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ADBExecutor{
    //manually: adb shell pm uninstall -k --user 0 com.x
    private final static String INSTALL_COMMAND = "adb shell cmd package install-existing com.group.example";
    private final static String DISABLED_APPS_COMMAND = "adb shell pm list packages -d";
    private final static List<String> UNINSTALL_COMMAND = new ArrayList<>(Arrays.asList("", "shell", "pm" ,"uninstall", "-k", "--user 0", ""));
    private final static List<String> PACKAGE_LIST_COMMAND = new ArrayList<>(Arrays.asList("", "shell", "pm" ,"list", "packages"));
    private final static List<String> DEVICES_COMMANDS = new ArrayList<>(Arrays.asList("", "devices"));

    private final String adbPath;
    private List<String> bloatedPackages;

    private ProcessBuilder procBuilder;
    private Scanner scanner;

    private InstalledPackages allPackages;
    private boolean onlyAvailablePackages;

    protected ADBExecutor(String adbDirectoryPath){
        this.adbPath = adbDirectoryPath;
        URL url = ADBMain.class.getResource("/packages.txt");
        if(url == null){
            System.err.println("Missing package names");
            return;
        }

        try{
            InputStream packagesStream = url.openStream();
            String readLines = Utilities.readFully(packagesStream);
            bloatedPackages = Utilities.readAllLines(readLines);
        }catch (IOException ioException){
            System.err.println("Error reading packages from file.. exiting");
            return;
        }
        System.out.println(bloatedPackages.size() + " packages loaded");
        System.out.println(bloatedPackages);
        procBuilder = new ProcessBuilder();
        procBuilder.directory(new File(adbDirectoryPath));
        scanner = new Scanner(System.in);
    }

    protected void run(){
        DEVICES_COMMANDS.set(0, adbPath + "/adb");
        PACKAGE_LIST_COMMAND.set(0, adbPath + "/adb");
        UNINSTALL_COMMAND.set(0, adbPath + "/adb");
        runDevicesStage();
        getAvailablePackagesIfPossible();
        runUninstallStage();
    }

    private void runDevicesStage(){
        String output = executeCommand(DEVICES_COMMANDS);
        System.out.println(output);
        int devices = devicesConnected(output);
        System.out.println(devices + (devices == 1 ? " connected device" : " connected devices"));
        if(devices == 0){
            System.out.println("No devices detected (is debugging via USB enabled?), try again? (y/n)");
            while(true){
                String line = scanner.nextLine().toLowerCase();
                if(line.isEmpty()){
                    continue;
                }
                if(line.startsWith("y")){
                    break;
                }
                else if(line.startsWith("n") || line.startsWith("exit")){
                    System.exit(0);
                }
            }
            runDevicesStage();
        }
    }

    private void getAvailablePackagesIfPossible(){
        String output = executeCommand(PACKAGE_LIST_COMMAND);
        if(output.startsWith("java.lang.UnsatisfiedLinkError")){
            System.out.println("'pm list packages' command failed");
        }else if(output.startsWith("package")){
            allPackages = new InstalledPackages(output, bloatedPackages);
            onlyAvailablePackages = true;
        }
    }

    private void runUninstallStage(){
        procBuilder.command(UNINSTALL_COMMAND);
        boolean nameSpecific = false;
        if(onlyAvailablePackages){
            if(allPackages.uninstallableCount() == 0){
                System.out.println("No bloated packages found on the device. Exiting ..");
                restoreCommandInfo();
                return;
            }
            System.out.println(allPackages.uninstallableSet());
            System.out.println("Uninstall " + allPackages.uninstallableCount() + " packages? (y/n)");
        }else{
            System.out.println("Uninstall possibly " + bloatedPackages.size() + " packages? (y/n)");
        }

        String line = scanner.nextLine();
        if(!line.startsWith("y")){
            if(!line.startsWith("n")){
                System.out.println("Exiting");
                System.exit(0);
            }
            System.out.println("Uninstall only those starting with:");
            line = scanner.nextLine();
            nameSpecific = true;
        }
        long start = System.currentTimeMillis();
        int lastIndex = UNINSTALL_COMMAND.size() - 1;
        int fail = 0;
        int success = 0;
        for (String currentPackage : bloatedPackages){
            //skip if not contained because it will fail anyway
            if(onlyAvailablePackages){
                if(!allPackages.isUninstallable(currentPackage)){
                    continue;
                }
            }
            if(nameSpecific){
                if(!currentPackage.startsWith(line)){
                    continue;
                }
            }
            UNINSTALL_COMMAND.set(lastIndex, currentPackage);
            String output = executeCommand(UNINSTALL_COMMAND);
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
        System.out.println(INSTALL_COMMAND);
    }

    private String executeCommand(List<String> commands){
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