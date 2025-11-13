import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ADBCommands {
    //manually: adb shell pm uninstall -k --user 0 com.x
    public static final String INSTALL_COMMAND_1 = "adb shell cmd package install-existing PACKAGE";
    public static final String INSTALL_COMMAND_2 = "adb shell pm install-existing PACKAGE";
    public static final String DISABLED_APPS_COMMAND = "adb shell pm list packages -d";
    public static final String DISABLE_APP_COMMAND_1 = "pm disable PACKAGE";
    public static final String DISABLE_APP_COMMAND_2 = "pm disable-user PACKAGE";
    public static final String FULL_BACKUP_COMMAND = "adb backup -apk -obb -shared -all -system -f backup.ab";

    private final ProcessBuilder procBuilder = new ProcessBuilder();
    private CommandTemplate UNINSTALL_KEEP, UNINSTALL_FULL, DISABLE,
            LIST_PACKAGES_BY_TYPE, LIST_PACKAGES_WITH_UID,
            TAR, CHOWN_RECURSE, EXTRACT_TAR, RESTORECON, RM, MK_DIR, RENAME, PM_PATH, DEVICES,
            ADB_PULL, ADB_PUSH, ADB_INSTALL, ADB_INSTALL_MULTIPLE, ADB_ROOT, ADB_UNROOT,
            INSTALL_BACK, INSTALL_CREATE, INSTALL_WRITE, INSTALL_COMMIT, EXISTS,
            MOUNT_READ_ONLY, MOUNT_READ_WRITE, ANDROID_VERSION, CHECK_SU;


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
        UNINSTALL_KEEP = new CommandTemplate(adbTerms, "shell", "pm", "uninstall", "-k", "--user 0", "");
        UNINSTALL_FULL = new CommandTemplate(adbTerms, "shell", "pm", "uninstall", "--user 0", "");
        DISABLE = new CommandTemplate(adbTerms, "shell", "pm", "disable-user", "");
        INSTALL_BACK = new CommandTemplate(adbTerms, "shell", "pm", "install-existing", "");
        DEVICES = new CommandTemplate(adbTerms, "devices");
        PM_PATH = new CommandTemplate(adbTerms, "shell", "pm", "path", "");
        ADB_PULL = new CommandTemplate(adbTerms, "pull", "", "");
        TAR = new CommandTemplate(adbTerms, "shell", "tar", "cfp", "", "-C", "", "");
        EXTRACT_TAR = new CommandTemplate(adbTerms, "shell", "tar", "xfp", "", "-C", "");
        CHOWN_RECURSE = new CommandTemplate(adbTerms, "shell", "chown", "-R", "", "");
        RESTORECON = new CommandTemplate(adbTerms, "shell", "restorecon", "-r", "-n", "v", "");
        RM = new CommandTemplate(adbTerms, "shell", "rm", "-f", "");
        ADB_PUSH = new CommandTemplate(adbTerms, "push", "", "");
        MK_DIR = new CommandTemplate(adbTerms, "shell", "mkdir", "-p", "");
        RENAME = new CommandTemplate(adbTerms, "shell", "mv", "", "");
        LIST_PACKAGES_BY_TYPE = new CommandTemplate(adbTerms, "shell", "pm", "list", "packages", "");
        LIST_PACKAGES_WITH_UID = new CommandTemplate(adbTerms, "shell", "pm", "list", "packages", "-U", "");
        INSTALL_CREATE = new CommandTemplate(adbTerms, "shell", "pm", "install-create", "-S", "");
        INSTALL_WRITE = new CommandTemplate(adbTerms, "shell", "pm", "install-write", "-S", "", "", "", "");
        INSTALL_COMMIT = new CommandTemplate(adbTerms, "shell", "pm", "install-commit", "");
        ADB_INSTALL = new CommandTemplate(adbTerms, "install", "");
        ADB_INSTALL_MULTIPLE = new CommandTemplate(adbTerms, "install-multiple");
        ADB_ROOT = new CommandTemplate(adbTerms, "root");
        ADB_UNROOT = new CommandTemplate(adbTerms, "unroot");
        MOUNT_READ_ONLY = new CommandTemplate(adbTerms, "shell", "mount", "-o", "ro,remount", "");
        MOUNT_READ_WRITE = new CommandTemplate(adbTerms, "shell", "mount", "-o", "rw,remount", "");
        ANDROID_VERSION = new CommandTemplate(adbTerms, "shell", "getprop", "ro.build.version.release");
        EXISTS = new CommandTemplate(adbTerms, "shell", "test", "-d", "", "&&", "echo", "Yes");
        CHECK_SU = new CommandTemplate(adbTerms, "shell", "id");
    }

    private static String[] joinCommand(String[] terms, String... command) {
        String[] joined = new String[terms.length + command.length];
        System.arraycopy(terms, 0, joined, 0, terms.length);
        System.arraycopy(command, 0, joined, terms.length, command.length);
        return joined;
    }

    public String executeCommandTrim(String[] commands, int maxLen) {
        procBuilder.command(commands);
        procBuilder.redirectErrorStream(true);
        try {
            Process proc = procBuilder.start();
            proc.waitFor(3, TimeUnit.SECONDS);
            return Utilities.read(proc.getInputStream(), maxLen, false);
        } catch (IOException | InterruptedException exceptions) {
            exceptions.printStackTrace();
            return "";
        }
    }

    public String executeCommandWithTimeout(String[] command, long timeoutMs) {
        procBuilder.command(command);
        procBuilder.redirectErrorStream(true);
        try {
            Process proc = procBuilder.start();
            proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            return Utilities.readFully(proc.getInputStream());
        } catch (IOException | InterruptedException exceptions) {
            exceptions.printStackTrace();
            return "";
        }
    }

    public String uninstallPackageFully(String pkgName) {
        return executeCommandWithTimeout(UNINSTALL_FULL.build(pkgName), 3000);
    }

    public String uninstallPackage(String pkgName) {
        return executeCommandWithTimeout(UNINSTALL_KEEP.build(pkgName), 3000);
    }

    public String disablePackageByName(String pkgName) {
        return executeCommandWithTimeout(DISABLE.build(pkgName), 3000);
    }

    public String installExistingPackage(String pkgName) {
        return executeCommandWithTimeout(INSTALL_BACK.build(pkgName), 3000);
    }

    public String getPackagePath(String pkgName) {
        return executeCommandWithTimeout(PM_PATH.build(pkgName), 3000);
    }

    public String changeOwnership(String owner, String group, String phonePath) {
        String[] command = CHOWN_RECURSE.build(owner + ":" + group, phonePath);
        System.out.println(Arrays.toString(command));
        return executeCommandWithTimeout(command, 3000);
    }

    // tar commands will override existing files in phone storage
    public String tar(String tarPath, String changedDir, String firstDir) {
        String[] command = TAR.build(
                tarPath,
                changedDir,
                firstDir
        );
        System.out.println(Arrays.toString(command));
        return executeCommandWithTimeout(command, 3000);
    }

    public String extractTar(String tarPath, String changedDir) {
        String[] command = EXTRACT_TAR.build(tarPath, changedDir);
        System.out.println(Arrays.toString(command));
        return executeCommandWithTimeout(command, 3000);
    }

    public String pullAPK(String apkPath, String toPath) {
        return executeCommandWithTimeout(ADB_PULL.build(apkPath, toPath), 3000);
    }

    public String pull(String phonePath, String pcPath) {
        return executeCommandWithTimeout(ADB_PULL.build(phonePath, pcPath), 3000);
    }

    public String push(String pcPath, String phonePath) {
        return executeCommandWithTimeout(ADB_PUSH.build(pcPath, phonePath), 3000);
    }

    public String mkdir(String phonePath) {
        return executeCommandWithTimeout(MK_DIR.build(phonePath), 3000);
    }

    public String rm(String phonePath) {
        return executeCommandWithTimeout(RM.build(phonePath), 3000);
    }

    public boolean exists(String phonePath) {
        String[] command = EXISTS.build(phonePath);
        return executeCommandWithTimeout(command, 3000).startsWith("Yes");
    }

    public String rename(String phonePath) {
        /*RENAME[RENAME.length - 1] = phonePath;
        return executeCommandWithTimeout(RENAME, 3000);*/
        return "Unimplemented";
    }

    public String install(String path) {
        return executeCommandWithTimeout(ADB_INSTALL.build(path), 3000);
    }
    public String createInstall(int totalSizeBytes) {
        String[] command = INSTALL_CREATE.build(String.valueOf(totalSizeBytes));
        return executeCommandWithTimeout(command, 3000);
    }

    public String installMultiple(String[] apks) {
        String[] installMultiple = joinCommand(ADB_INSTALL_MULTIPLE.build(), apks);
        return executeCommandWithTimeout(installMultiple, 3000);
    }
    public String installWrite(long splitApkSize, int sessionId, int index, String path) {
        String[] command = INSTALL_WRITE.build(
            String.valueOf(splitApkSize),
            String.valueOf(sessionId),
            String.valueOf(index),
            path
        );
        return executeCommandWithTimeout(command, 3000);
    }

    public String installCommit(int sessionId) {
        String[] command = INSTALL_COMMIT.build(String.valueOf(sessionId));
        return executeCommandWithTimeout(command, 3000);
    }

    public String installExistingPackage(String pkgName, int maxOutputLen) {
        String[] command = INSTALL_BACK.build(pkgName);
        return executeCommandTrim(command, maxOutputLen);
    }

    public String installAsSystemApp(String apkPath, String appDir) {
        String partition = "/";
        String rwResult = remountReadWrite(partition);
        if (rwResult.startsWith("adb: error:")) {
            return rwResult;
        }
        if (rwResult.startsWith("mount:")) {
            String rootResult = root();
            if (!hasRoot(rootResult)) {
                return rwResult + rootResult;
            }
        }
        String phoneDir = "system/priv-app/" + appDir + "/";
        String mkDirResult = mkdir(phoneDir);
        String pushResult = push(apkPath, phoneDir);
        if (pushResult.startsWith("adb: error:")) {
            return mkDirResult + pushResult;
        }
        String roResult = remountReadOnly(partition);
        return rwResult + pushResult + roResult;
    }

    public static boolean hasRoot(String output) {
        return output.startsWith("restarting adbd as root") || output.startsWith("adbd is already running as root");
    }

    public boolean checkSU() {
        String[] command = CHECK_SU.buildPrivileged();
        System.out.println(Arrays.toString(command));
        return executeCommandWithTimeout(command, 10_000).contains("uid=0");
    }

    public String root() {
        return executeCommandWithTimeout(ADB_ROOT.build(), 3000);
    }

    public String unroot() {
        return executeCommandWithTimeout(ADB_UNROOT.build(), 3000);
    }

    public String remountReadOnly(String partition) {
        String[] command = MOUNT_READ_ONLY.build(partition);
        return executeCommandWithTimeout(command, 3000);
    }

    public String remountReadWrite(String partition) {
        String[] command = MOUNT_READ_WRITE.build(partition);
        return executeCommandWithTimeout(command, 3000);
    }

    // These commands don't require a timeout, but it makes them more reliable
    public String listDevices() {
        return executeCommandWithTimeout(DEVICES.build(), 50);
    }

    public String listPackagesWithUID(PackageType type) {
        String modifier = getPackageModifier(type);
        String[] command = LIST_PACKAGES_WITH_UID.build(modifier);
        return executeCommandWithTimeout(command, 50);
    }

    public String getAndroidVersion() {
        return executeCommandWithTimeout(ANDROID_VERSION.build(), 50);
    }

    public String listPackagesBy(PackageType type) {
        String modifier = getPackageModifier(type);
        String[] command = LIST_PACKAGES_BY_TYPE.build(modifier);
        return executeCommandWithTimeout(command, 50);
    }
    private static String getPackageModifier(PackageType type) {
        switch (type) {
            case ALL:
                return "";
            case USER:
                return "-3";
            case SYSTEM:
                return "-s";
        }
        throw new IllegalStateException("Unreachable");
    }
}

enum PackageType {
    SYSTEM, USER, ALL
}

enum PrivilegeType {
    ADB_ROOT, SU
}

class CommandTemplate {
    private static final List<String> SU_TERMS = Arrays.asList("su", "-c");
    private final String[] adbTerms;
    private final String[] components; // Empty components are placeholders for arguments

    public CommandTemplate(String[] adbTerms, String... components) {
        this.adbTerms = adbTerms;
        this.components = components;
    }

    public String[] build(String... args) {
        return build(false, args);
    }

    public String[] buildPrivileged(String... args) {
        return build(true, args);
    }

    private String[] build(boolean su, String... args) {
        List<String> command = new ArrayList<>(adbTerms.length + components.length + 2);
        List<String> filledArgs = new ArrayList<>(components.length + 2);

        command.addAll(Arrays.asList(adbTerms));
        boolean isShell = components.length > 0 && components[0].equals("shell");
        boolean wrapInner = su && isShell;
        int startIndex = 0;
        if (wrapInner) {
            command.add("shell");
            command.addAll(SU_TERMS);
            startIndex = 1;
        }

        int a = 0;
        for (int i = startIndex; i < components.length; i++) {
            String component = components[i];
            if (!component.isEmpty()) {
                filledArgs.add(component);
                continue;
            }
            if (a == args.length) {
                Utilities.errExit("Command structure error. Too many arguments given for template: " + Arrays.toString(components));
            }
            String arg = args[a++];
            filledArgs.add(arg);
        }
        if (wrapInner) {
            String innerCommand = String.join(" ", filledArgs);
            command.add(innerCommand);
        } else {
            command.addAll(filledArgs);
        }

        return command.toArray(new String[0]);
    }
}
