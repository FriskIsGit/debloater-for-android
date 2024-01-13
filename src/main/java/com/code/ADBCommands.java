package com.code;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ADBCommands {
    //manually: adb shell pm uninstall -k --user 0 com.x
    public static final String INSTALL_COMMAND_1 = "adb shell cmd package install-existing PACKAGE";
    public static final String INSTALL_COMMAND_2 = "adb shell pm install-existing PACKAGE";
    public static final String DISABLED_APPS_COMMAND = "adb shell pm list packages -d";
    public static final String DISABLE_APP_COMMAND_1 = "pm disable PACKAGE";
    public static final String DISABLE_APP_COMMAND_2 = "pm disable-user PACKAGE";

    private final ProcessBuilder procBuilder = new ProcessBuilder();
    private String[] UNINSTALL_KEEP;
    private String[] UNINSTALL_FULL;
    private String[] LIST_PACKAGES;
    private String[] LIST_PACKAGES_BY_TYPE;
    private String[] PM_PATH;
    private String[] PULL;
    private String[] INSTALL_BACK;
    private String[] DEVICES;

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

    private static String formAdbPath(String adbDir) {
        if (adbDir.charAt(adbDir.length() - 1) == '/') {
            return adbDir + "adb";
        } else {
            return adbDir + "/adb";
        }
    }

    private void setupCommands(boolean cmd) {
        if (cmd) {
            UNINSTALL_KEEP = new String[]{"cmd", "/C", "adb", "shell", "pm", "uninstall", "-k", "--user 0", ""};
            UNINSTALL_FULL = new String[]{"cmd", "/C", "adb", "shell", "pm", "uninstall", "--user 0", ""};
            LIST_PACKAGES = new String[]{"cmd", "/C", "adb", "shell", "pm", "list", "packages"};
            INSTALL_BACK = new String[]{"cmd", "/C", "adb", "shell", "pm", "install-existing", ""};
            DEVICES = new String[]{"cmd", "/C", "adb", "devices"};
            PM_PATH = new String[]{"cmd", "/C", "adb", "shell", "pm", "path", ""};
            PULL = new String[]{"cmd", "/C", "adb", "pull", ""};
            LIST_PACKAGES_BY_TYPE = new String[]{"cmd", "/C", "adb", "shell", "pm", "list", "packages", ""};
            return;
        }

        UNINSTALL_KEEP = new String[]{"adb", "shell", "pm", "uninstall", "-k", "--user 0", ""};
        UNINSTALL_FULL = new String[]{"adb", "shell", "pm", "uninstall", "--user 0", ""};
        LIST_PACKAGES = new String[]{"adb", "shell", "pm", "list", "packages"};
        INSTALL_BACK = new String[]{"adb", "shell", "pm", "install-existing", ""};
        DEVICES = new String[]{"adb", "devices"};
        PM_PATH = new String[]{"adb", "shell", "pm", "path", ""};
        PULL = new String[]{"adb", "pull", ""};
        LIST_PACKAGES_BY_TYPE = new String[]{"adb", "shell", "pm", "list", "packages", ""};
    }

    private void setupCommands(String adbPath) {
        UNINSTALL_KEEP = new String[]{adbPath, "shell", "pm", "uninstall", "-k", "--user 0", ""};
        UNINSTALL_FULL = new String[]{adbPath, "shell", "pm", "uninstall", "--user 0", ""};
        LIST_PACKAGES = new String[]{adbPath, "shell", "pm", "list", "packages"};
        INSTALL_BACK = new String[]{adbPath, "shell", "pm", "install-existing", ""};
        DEVICES = new String[]{adbPath, "devices"};
        PM_PATH = new String[]{adbPath, "shell", "pm", "path", ""};
        PULL = new String[]{adbPath, "pull", ""};
        LIST_PACKAGES_BY_TYPE = new String[]{adbPath, "shell", "pm", "list", "packages", ""};
    }

    public String executeCommand(String[] commands) {
        procBuilder.command(commands);
        try {
            Process proc = procBuilder.start();
            proc.waitFor(3, TimeUnit.SECONDS);
            return Utilities.readFully(proc.getInputStream());
        } catch (IOException | InterruptedException exceptions) {
            exceptions.printStackTrace();
            return "";
        }
    }

    public String executeCommand(String[] commands, int maxLen) {
        procBuilder.command(commands);
        try {
            Process proc = procBuilder.start();
            proc.waitFor(3, TimeUnit.SECONDS);
            return Utilities.read(proc.getInputStream(), maxLen, false);
        } catch (IOException | InterruptedException exceptions) {
            exceptions.printStackTrace();
            return "";
        }
    }

    public String uninstallPackageFully(String pckgName) {
        UNINSTALL_FULL[UNINSTALL_FULL.length - 1] = pckgName;
        return executeCommand(UNINSTALL_FULL);
    }

    public String uninstallPackage(String pckgName) {
        UNINSTALL_KEEP[UNINSTALL_KEEP.length - 1] = pckgName;
        return executeCommand(UNINSTALL_KEEP);
    }

    public String installPackage(String pckgName) {
        INSTALL_BACK[INSTALL_BACK.length - 1] = pckgName;
        return executeCommand(INSTALL_BACK);
    }

    public String getPackagePath(String pckgName) {
        PM_PATH[PM_PATH.length - 1] = pckgName;
        return executeCommand(PM_PATH);
    }
    public String pullAPK(String apkPath) {
        PULL[PULL.length - 1] = apkPath;
        return executeCommand(PULL);
    }

    public String installPackage(String pckgName, int maxOutputLen) {
        INSTALL_BACK[INSTALL_BACK.length - 1] = pckgName;
        return executeCommand(INSTALL_BACK, maxOutputLen);
    }

    public String listDevices() {
        return executeCommand(DEVICES);
    }

    public String listPackages() {
        return executeCommand(LIST_PACKAGES);
    }
    public String listPackagesBy(PackageType type) {
        String modifier = null;
        switch (type) {
            case ALL:
                modifier = "";
                break;
            case USER:
                modifier = "-3";
                break;
            case SYSTEM:
                modifier = "-s";
                break;
            case INAPPLICABLE:
                throw new RuntimeException("How did we get here?");
        }
        LIST_PACKAGES_BY_TYPE[LIST_PACKAGES_BY_TYPE.length - 1] = modifier;
        return executeCommand(LIST_PACKAGES_BY_TYPE);
    }
}
