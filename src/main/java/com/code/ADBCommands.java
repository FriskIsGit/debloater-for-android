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
    private String[] PUSH;
    private String[] INSTALL_BACK;
    private String[] DEVICES;
    private String[] INSTALL_CREATE;
    private String[] INSTALL_WRITE;
    private String[] INSTALL_COMMIT;

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
        commands.setupCommands("adb");
        return commands;
    }

    public static ADBCommands fromCmdEnv() {
        //execute through cmd because the env var is only recognized by cmd
        ADBCommands commands = new ADBCommands();
        commands.setupCommands("cmd", "/c", "adb");
        return commands;
    }

    private static String formAdbPath(String adbDir) {
        if (adbDir.charAt(adbDir.length() - 1) == '/') {
            return adbDir + "adb";
        } else {
            return adbDir + "/adb";
        }
    }

    private void setupCommands(String... adbTerms) {
        UNINSTALL_KEEP = joinCommand(adbTerms, new String[]{"shell", "pm", "uninstall", "-k", "--user 0", ""});
        UNINSTALL_FULL = joinCommand(adbTerms, new String[]{"shell", "pm", "uninstall", "--user 0", ""});
        LIST_PACKAGES = joinCommand(adbTerms, new String[]{"shell", "pm", "list", "packages"});
        INSTALL_BACK = joinCommand(adbTerms, new String[]{"shell", "pm", "install-existing", ""});
        DEVICES = joinCommand(adbTerms, new String[]{"devices"});
        PM_PATH = joinCommand(adbTerms, new String[]{"shell", "pm", "path", ""});
        PULL = joinCommand(adbTerms, new String[]{"pull", "", ""});
        PUSH = joinCommand(adbTerms, new String[]{"push", "", ""});
        LIST_PACKAGES_BY_TYPE = joinCommand(adbTerms, new String[]{"shell", "pm", "list", "packages", ""});
        INSTALL_CREATE = joinCommand(adbTerms, new String[]{"shell", "pm", "install-create", "-S", ""});
        INSTALL_WRITE = joinCommand(adbTerms, new String[]{"shell", "pm", "install-write", "-S", "", "", "", ""});
        INSTALL_COMMIT = joinCommand(adbTerms, new String[]{"shell", "pm", "install-commit", ""});
    }

    private static String[] joinCommand(String[] terms, String[] command) {
        String[] joined = new String[terms.length + command.length];
        System.arraycopy(terms, 0, joined, 0, terms.length);
        System.arraycopy(command, 0, joined, terms.length, command.length);
        return joined;
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

    public String pullAPK(String apkPath, String toPath) {
        PULL[PULL.length - 2] = apkPath;
        PULL[PULL.length - 1] = toPath;
        return executeCommand(PULL);
    }

    public String push(String pcPath, String phonePath) {
        PUSH[PUSH.length - 2] = pcPath;
        PUSH[PUSH.length - 1] = phonePath;
        return executeCommand(PUSH);
    }

    public String createInstall(int totalSizeBytes) {
        INSTALL_CREATE[INSTALL_CREATE.length - 1] = String.valueOf(totalSizeBytes);
        return executeCommand(INSTALL_CREATE);
    }

    public String installWrite(int splitApkSize, int sessionId, int index, String path) {
        INSTALL_WRITE[INSTALL_WRITE.length - 4] = String.valueOf(splitApkSize);
        INSTALL_WRITE[INSTALL_WRITE.length - 3] = String.valueOf(sessionId);
        INSTALL_WRITE[INSTALL_WRITE.length - 2] = String.valueOf(index);
        INSTALL_WRITE[INSTALL_WRITE.length - 1] = path;
        return executeCommand(INSTALL_WRITE);
    }

    public String installCommit(int sessionId) {
        INSTALL_COMMIT[INSTALL_COMMIT.length - 1] = String.valueOf(sessionId);
        return executeCommand(INSTALL_COMMIT);
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
