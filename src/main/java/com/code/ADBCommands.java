package com.code;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ADBCommands{
    //manually: adb shell pm uninstall -k --user 0 com.x
    public static final String INSTALL_COMMAND_1 = "adb shell cmd package install-existing PACKAGE";
    public static final String INSTALL_COMMAND_2 = "adb shell pm install-existing PACKAGE";
    public static final String DISABLED_APPS_COMMAND = "adb shell pm list packages -d";
    public static final String DISABLE_APP_COMMAND_1 = "pm disable PACKAGE";
    public static final String DISABLE_APP_COMMAND_2 = "pm disable-user PACKAGE";

    private final ProcessBuilder procBuilder = new ProcessBuilder();
    private String[] UNINSTALL_KEEP_COMMAND;
    private String[] UNINSTALL_FULL_COMMAND;
    private String[] LIST_PACKAGES_COMMAND;
    private String[] INSTALL_BACK_COMMAND;
    private String[] DEVICES_COMMANDS;

    public static ADBCommands fromDir(String adbDir) {
        //we must include the entire path to avoid: CreateProcess error=2 The system cannot find the file specified
        ADBCommands commands = new ADBCommands();
        adbDir = Utilities.normalizeStringPath(adbDir);
        String adbPath = formAdbPath(adbDir);
        commands.setupCommands(adbPath);
        return commands;
    }

    public static ADBCommands fromEnv() {
        //execute with anywhere since adb is seen as a program, usually Linux
        ADBCommands commands = new ADBCommands();
        commands.setupCommands(false);
        return commands;
    }
    public static ADBCommands fromCmdEnv() {
        //execute through cmd because the env var is only recognized by cmd
        ADBCommands commands = new ADBCommands();
        commands.setupCommands(true);
        return commands;
    }

    private static String formAdbPath(String adbDir){
        if (adbDir.charAt(adbDir.length() - 1) == '/'){
            return adbDir + "adb";
        } else {
            return adbDir + "/adb";
        }
    }

    private void setupCommands(boolean cmd){
        if (cmd) {
            UNINSTALL_KEEP_COMMAND = new String[]{"cmd", "/C", "adb", "shell", "pm", "uninstall", "-k", "--user 0", ""};
            UNINSTALL_FULL_COMMAND = new String[]{"cmd", "/C", "adb", "shell", "pm", "uninstall", "--user 0", ""};
            LIST_PACKAGES_COMMAND = new String[]{"cmd", "/C", "adb", "shell", "pm", "list", "packages"};
            INSTALL_BACK_COMMAND = new String[]{"cmd", "/C", "adb", "shell", "pm", "install-existing", ""};
            DEVICES_COMMANDS = new String[]{"cmd", "/C", "adb", "devices"};
            return;
        }

        UNINSTALL_KEEP_COMMAND = new String[]{"adb", "shell", "pm", "uninstall", "-k", "--user 0", ""};
        UNINSTALL_FULL_COMMAND = new String[]{"adb", "shell", "pm", "uninstall", "--user 0", ""};
        LIST_PACKAGES_COMMAND = new String[]{"adb", "shell", "pm", "list", "packages"};
        INSTALL_BACK_COMMAND = new String[]{"adb", "shell", "pm", "install-existing", ""};
        DEVICES_COMMANDS = new String[]{"adb", "devices"};
    }

    private void setupCommands(String adbPath){
        UNINSTALL_KEEP_COMMAND = new String[]{adbPath, "shell", "pm", "uninstall", "-k", "--user 0", ""};
        UNINSTALL_FULL_COMMAND = new String[]{adbPath, "shell", "pm", "uninstall", "--user 0", ""};
        LIST_PACKAGES_COMMAND = new String[]{adbPath, "shell", "pm", "list", "packages"};
        INSTALL_BACK_COMMAND = new String[]{adbPath, "shell", "pm", "install-existing", ""};
        DEVICES_COMMANDS = new String[]{adbPath, "devices"};
    }

    public String executeCommand(String[] commands){
        procBuilder.command(commands);
        try{
            Process proc = procBuilder.start();
            proc.waitFor(3, TimeUnit.SECONDS);
            return Utilities.readFully(proc.getInputStream());
        }catch (IOException | InterruptedException exceptions){
            exceptions.printStackTrace();
            return "";
        }
    }

    public String executeCommand(String[] commands, int maxLen){
        procBuilder.command(commands);
        try{
            Process proc = procBuilder.start();
            proc.waitFor(3, TimeUnit.SECONDS);
            return Utilities.read(proc.getInputStream(), maxLen, false);
        }catch (IOException | InterruptedException exceptions){
            exceptions.printStackTrace();
            return "";
        }
    }

    public String uninstallPackageFully(String pckgName){
        UNINSTALL_FULL_COMMAND[UNINSTALL_FULL_COMMAND.length - 1] = pckgName;
        return executeCommand(UNINSTALL_FULL_COMMAND);
    }

    public String uninstallPackage(String pckgName){
        UNINSTALL_KEEP_COMMAND[UNINSTALL_KEEP_COMMAND.length - 1] = pckgName;
        return executeCommand(UNINSTALL_KEEP_COMMAND);
    }

    public String installPackage(String pckgName){
        INSTALL_BACK_COMMAND[INSTALL_BACK_COMMAND.length - 1] = pckgName;
        return executeCommand(INSTALL_BACK_COMMAND);
    }

    public String installPackage(String pckgName, int maxOutputLen){
        INSTALL_BACK_COMMAND[INSTALL_BACK_COMMAND.length - 1] = pckgName;
        return executeCommand(INSTALL_BACK_COMMAND, maxOutputLen);
    }

    public String listDevices(){
        return executeCommand(DEVICES_COMMANDS);
    }

    public String listPackages(){
        return executeCommand(LIST_PACKAGES_COMMAND);
    }
}
