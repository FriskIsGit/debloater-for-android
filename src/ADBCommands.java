import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class ADBCommands {
    //manually: adb shell pm uninstall -k --user 0 com.x
    public static final String INSTALL_COMMAND_1 = "adb shell cmd package install-existing PACKAGE";
    public static final String INSTALL_COMMAND_2 = "adb shell pm install-existing PACKAGE";
    public static final String DISABLED_APPS_COMMAND = "adb shell pm list packages -d";
    public static final String DISABLE_APP_COMMAND_1 = "pm disable PACKAGE";
    public static final String DISABLE_APP_COMMAND_2 = "pm disable-user PACKAGE";
    public static final String FULL_BACKUP_COMMAND = "adb backup -apk -obb -shared -all -system -f backup.ab";

    PrivilegeType privilege = null;
    private final ProcessBuilder procBuilder = new ProcessBuilder();
    private CommandTemplate PM_UNINSTALL_PER_USER, PM_UNINSTALL_PER_USER_KEEP, DISABLE,
            LIST_PACKAGES_BY_TYPE, LIST_PACKAGES_WITH_UID,
            TAR, CHOWN_RECURSE, EXTRACT_TAR, RESTORECON, RM, RM_DIR, MK_DIR, PM_PATH, DEVICES,
            ADB_PULL, ADB_PUSH, ADB_INSTALL, ADB_INSTALL_MULTIPLE, ADB_ROOT, ADB_UNROOT,
            INSTALL_BACK, INSTALL_CREATE, INSTALL_WRITE, INSTALL_COMMIT, EXISTS,
            MOUNT_READ_ONLY, MOUNT_READ_WRITE, CHECK_SU, MOVE, GET_SELINUX_MODE,
            GET_PROP, SET_PROP, DIRECTORY_SIZE, LS_SIMPLE;

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

    public void ensurePrivileged() {
        if (privilege != null) {
            return;
        }
        String rootResult = root();
        PrivilegeType privilege = PrivilegeType.ADB_ROOT;
        if (!ADBCommands.hasRoot(rootResult)) {
            System.out.println("No adb root. Trying su, answer the request on your phone or grant Shell SU rights");
            if (!checkSU()) {
                Utilities.errExit("No su access.");
            }
            privilege = PrivilegeType.SU;
        }
        this.privilege = privilege;
    }

    private void setupCommands(String... adbTerms) {
        PM_UNINSTALL_PER_USER = new CommandTemplate(adbTerms, "shell", "pm", "uninstall", "--user 0", "");
        PM_UNINSTALL_PER_USER_KEEP = new CommandTemplate(adbTerms, "shell", "pm", "uninstall", "-k", "--user 0", "");
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
        RM_DIR = new CommandTemplate(adbTerms, "shell", "rm", "-rf", "");
        ADB_PUSH = new CommandTemplate(adbTerms, "push", "", "");
        MK_DIR = new CommandTemplate(adbTerms, "shell", "mkdir", "-p", "");
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
        EXISTS = new CommandTemplate(adbTerms, "shell", "test", "-d", "", "&&", "echo", "Yes");
        CHECK_SU = new CommandTemplate(adbTerms, "shell", "id");
        MOVE = new CommandTemplate(adbTerms, "shell", "mv", "", "");
        GET_SELINUX_MODE = new CommandTemplate(adbTerms, "shell", "getenforce");
        GET_PROP = new CommandTemplate(adbTerms, "shell", "getprop", "");
        SET_PROP = new CommandTemplate(adbTerms, "shell", "setprop", "", "");
        DIRECTORY_SIZE = new CommandTemplate(adbTerms, "shell", "du", "-sh", "");
        LS_SIMPLE = new CommandTemplate(adbTerms, "shell", "ls", "-1", "");
    }

    public String executeCommandTrim(String[] commands, int maxLen) {
        procBuilder.command(commands);
        procBuilder.redirectErrorStream(true);
        try {
            Process proc = procBuilder.start();
            proc.waitFor(3, TimeUnit.SECONDS);
            return Utilities.read(proc.getInputStream(), maxLen, false);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
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
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return "";
        }
    }

    public String uninstallPackagePerUser(String pkgName) {
        return executeCommandWithTimeout(PM_UNINSTALL_PER_USER.build(pkgName), 3000);
    }

    public String uninstallPackagePerUserKeepData(String pkgName) {
        return executeCommandWithTimeout(PM_UNINSTALL_PER_USER_KEEP.build(pkgName), 3000);
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
        String[] command = CHOWN_RECURSE.build(isSU(), owner + ":" + group, phonePath);
        System.out.println(Arrays.toString(command));
        return executeCommandWithTimeout(command, 3000);
    }

    // tar commands will override existing files in phone storage
    // firstDir is path relative to changedDir that is put in the archive
    public String tar(String tarPath, String changedDir, String firstDir) {
        String[] command = TAR.build(
                isSU(),
                tarPath,
                changedDir,
                firstDir
        );
        System.out.println(Arrays.toString(command));
        return executeCommandWithTimeout(command, 3000);
    }

    public String extractTar(String tarPath, String changedDir) {
        String[] command = EXTRACT_TAR.build(isSU(), tarPath, changedDir);
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
        String[] command = MK_DIR.build(isSU(), phonePath);
        System.out.println(Arrays.toString(command));
        return executeCommandWithTimeout(command, 3000);
    }

    public String rm(String phonePath) {
        String[] command = RM.build(phonePath);
        System.out.println(Arrays.toString(command));
        return executeCommandWithTimeout(command, 4000);
    }

    public String rmSU(String phonePath) {
        String[] command = RM.buildSU(phonePath);
        return executeCommandWithTimeout(command, 4000);
    }

    public String rmDirectory(String phoneDir) {
        String[] command = RM_DIR.build(isSU(), phoneDir);
        System.out.println(Arrays.toString(command));
        return executeCommandWithTimeout(command, 10_000);
    }

    public boolean exists(String phonePath) {
        String[] command = EXISTS.build(isSU(), phonePath);
        return executeCommandWithTimeout(command, 3000).startsWith("Yes");
    }

    public String install(String path) {
        return executeCommandWithTimeout(ADB_INSTALL.build(path), 3000);
    }
    public String createInstall(int totalSizeBytes) {
        String[] command = INSTALL_CREATE.build(String.valueOf(totalSizeBytes));
        return executeCommandWithTimeout(command, 3000);
    }

    public String installMultiple(String[] apks) {
        String[] installMultiple = ADB_INSTALL_MULTIPLE.build(apks);
        return executeCommandWithTimeout(installMultiple, 30_000);
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
        ensurePrivileged();
        String partition = "/system";
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
        String phoneDir = "/system/priv-app/" + appDir + "/";
        String mkDirResult = mkdir(phoneDir);

        String phonePath = "/system/priv-app/" + appDir + "/" + appDir + ".apk";
        String pushResult = push(apkPath, phonePath);
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
        String[] command = CHECK_SU.buildSU();
        System.out.println(Arrays.toString(command));
        return executeCommandWithTimeout(command, 10_000).contains("uid=0");
    }

    public boolean isSU() {
        return privilege == PrivilegeType.SU;
    }

    public String root() {
        return executeCommandWithTimeout(ADB_ROOT.build(), 3000);
    }

    public String unroot() {
        return executeCommandWithTimeout(ADB_UNROOT.build(), 3000);
    }

    public String remountReadOnly(String partition) {
        String[] command = MOUNT_READ_ONLY.build(isSU(), partition);
        System.out.println(Arrays.toString(command));
        return executeCommandWithTimeout(command, 3000);
    }

    public String remountReadWrite(String partition) {
        String[] command = MOUNT_READ_WRITE.build(isSU(), partition);
        System.out.println(Arrays.toString(command));
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
        return executeCommandWithTimeout(GET_PROP.build("ro.build.version.release"), 50);
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

    public String moveSU(String phoneSrc, String phoneDestination) {
        String[] command = MOVE.buildSU(phoneSrc, phoneDestination);
        System.out.println(Arrays.toString(command));
        return executeCommandWithTimeout(command, 10_000);
    }

    public String getSELinuxMode() {
        String[] command = GET_SELINUX_MODE.build();
        return executeCommandWithTimeout(command, 10_000);
    }

    public String setProp(String key, String value) {
        String[] command = SET_PROP.buildSU(key, value);
        System.out.println(Arrays.toString(command));
        return executeCommandWithTimeout(command, 10_000);
    }

    public String getDirectorySize(String phoneDir) {
        String[] command = DIRECTORY_SIZE.build(phoneDir);
        System.out.println(Arrays.toString(command));
        return executeCommandWithTimeout(command, 10_000);
    }

    public String listFiles(String phoneDir) {
        String[] command = LS_SIMPLE.build(isSU(), phoneDir);
        return executeCommandWithTimeout(command, 10_000);
    }
}

enum PackageType {
    SYSTEM, USER, ALL;
    public static PackageType from(String name) {
        try {
            String pkgType = name.toUpperCase(Locale.ROOT);
            return valueOf(pkgType);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
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

    public String[] buildSU(String... args) {
        return build(true, args);
    }

    public String[] build(boolean su, String... args) {
        List<String> command = new ArrayList<>(adbTerms.length + components.length + 2);
        List<String> filledArgs = new ArrayList<>();

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
                Utilities.errExit("Command structure error - template arguments not satisfied: " + Arrays.toString(components));
            }
            String arg = args[a++];
            filledArgs.add(arg);
        }
        // Append additional remaining arguments
        filledArgs.addAll(Arrays.asList(args).subList(a, args.length));
        if (wrapInner) {
            String innerCommand = String.join(" ", filledArgs);
            command.add(innerCommand);
        } else {
            command.addAll(filledArgs);
        }

        return command.toArray(new String[0]);
    }
}
