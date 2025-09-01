import java.io.IOException;
import java.util.Arrays;
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
    private String[] UNINSTALL_KEEP;
    private String[] UNINSTALL_FULL;
    private String[] DISABLE;
    private String[] LIST_PACKAGES;
    private String[] LIST_PACKAGES_BY_TYPE;
    private String[] DEVICES;
    private String[] PM_PATH;
    private String[] PULL;
    private String[] TAR;
    private String[] EXTRACT_TAR;
    private String[] RM;
    private String[] ADB_PUSH;
    private String[] MK_DIR;
    private String[] RENAME;
    private String[] ADB_INSTALL;
    private String[] ADB_INSTALL_MULTIPLE;
    private String[] ADB_ROOT;
    private String[] ADB_UNROOT;
    private String[] INSTALL_BACK;
    private String[] INSTALL_CREATE;
    private String[] INSTALL_WRITE;
    private String[] INSTALL_COMMIT;
    private String[] MOUNT_READ_ONLY;
    private String[] MOUNT_READ_WRITE;


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
        DISABLE = joinCommand(adbTerms, new String[]{"shell", "pm", "disable-user", ""});
        LIST_PACKAGES = joinCommand(adbTerms, new String[]{"shell", "pm", "list", "packages"});
        INSTALL_BACK = joinCommand(adbTerms, new String[]{"shell", "pm", "install-existing", ""});
        DEVICES = joinCommand(adbTerms, new String[]{"devices"});
        PM_PATH = joinCommand(adbTerms, new String[]{"shell", "pm", "path", ""});
        PULL = joinCommand(adbTerms, new String[]{"pull", "", ""});
        TAR = joinCommand(adbTerms, new String[]{"shell", "tar", "cf", "", "-C", "", ""});
        EXTRACT_TAR = joinCommand(adbTerms, new String[]{"shell", "tar", "xf", "", "-C", ""});
        RM = joinCommand(adbTerms, new String[]{"shell", "rm", "-f", ""});
        ADB_PUSH = joinCommand(adbTerms, new String[]{"push", "", ""});
        MK_DIR = joinCommand(adbTerms, new String[]{"shell", "mkdir", "-p", ""});
        RENAME = joinCommand(adbTerms, new String[]{"shell", "mv", "", ""});
        LIST_PACKAGES_BY_TYPE = joinCommand(adbTerms, new String[]{"shell", "pm", "list", "packages", ""});
        INSTALL_CREATE = joinCommand(adbTerms, new String[]{"shell", "pm", "install-create", "-S", ""});
        INSTALL_WRITE = joinCommand(adbTerms, new String[]{"shell", "pm", "install-write", "-S", "", "", "", ""});
        INSTALL_COMMIT = joinCommand(adbTerms, new String[]{"shell", "pm", "install-commit", ""});
        ADB_INSTALL = joinCommand(adbTerms, new String[]{"install", ""});
        ADB_INSTALL_MULTIPLE = joinCommand(adbTerms, new String[]{"install-multiple"});
        ADB_ROOT = joinCommand(adbTerms, new String[]{"root"});
        ADB_UNROOT = joinCommand(adbTerms, new String[]{"unroot"});
        MOUNT_READ_ONLY = joinCommand(adbTerms, new String[]{"shell", "mount", "-o", "ro,remount", ""});
        MOUNT_READ_WRITE = joinCommand(adbTerms, new String[]{"shell", "mount", "-o", "rw,remount", ""});
    }

    private static String[] joinCommand(String[] terms, String[] command) {
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

    public String executeCommandWithTimeout(String[] commands, long timeoutMs) {
        procBuilder.command(commands);
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
        UNINSTALL_FULL[UNINSTALL_FULL.length - 1] = pkgName;
        return executeCommandWithTimeout(UNINSTALL_FULL, 3000);
    }

    public String uninstallPackage(String pkgName) {
        UNINSTALL_KEEP[UNINSTALL_KEEP.length - 1] = pkgName;
        return executeCommandWithTimeout(UNINSTALL_KEEP, 3000);
    }

    public String disablePackageByName(String pkgName) {
        DISABLE[DISABLE.length - 1] = pkgName;
        return executeCommandWithTimeout(DISABLE, 3000);
    }

    public String installExistingPackage(String pkgName) {
        INSTALL_BACK[INSTALL_BACK.length - 1] = pkgName;
        return executeCommandWithTimeout(INSTALL_BACK, 3000);
    }

    public String getPackagePath(String pkgName) {
        PM_PATH[PM_PATH.length - 1] = pkgName;
        return executeCommandWithTimeout(PM_PATH, 3000);
    }

    // tar commands will override existing files in phone storage
    public String tar(String tarPath, String changedDir, String firstDir) {
        TAR[TAR.length - 4] = tarPath;
        TAR[TAR.length - 2] = changedDir;
        TAR[TAR.length - 1] = firstDir;
        // System.out.println(Arrays.toString(TAR));
        return executeCommandWithTimeout(TAR, 3000);
    }

    public String extractTar(String tarPath, String changedDir) {
        EXTRACT_TAR[EXTRACT_TAR.length - 3] = tarPath;
        EXTRACT_TAR[EXTRACT_TAR.length - 1] = changedDir;
        // System.out.println(Arrays.toString(EXTRACT_TAR));
        return executeCommandWithTimeout(EXTRACT_TAR, 3000);
    }

    public String pullAPK(String apkPath, String toPath) {
        PULL[PULL.length - 2] = apkPath;
        PULL[PULL.length - 1] = toPath;
        return executeCommandWithTimeout(PULL, 3000);
    }

    public String pull(String phonePath, String pcPath) {
        PULL[PULL.length - 2] = phonePath;
        PULL[PULL.length - 1] = pcPath;
        // System.out.println(Arrays.toString(PULL));
        return executeCommandWithTimeout(PULL, 3000);
    }

    public String push(String pcPath, String phonePath) {
        ADB_PUSH[ADB_PUSH.length - 2] = pcPath;
        ADB_PUSH[ADB_PUSH.length - 1] = phonePath;
        return executeCommandWithTimeout(ADB_PUSH, 3000);
    }

    public String mkdir(String phonePath) {
        MK_DIR[MK_DIR.length - 1] = phonePath;
        return executeCommandWithTimeout(MK_DIR, 3000);
    }

    public String rm(String phonePath) {
        RM[RM.length - 1] = phonePath;
        return executeCommandWithTimeout(RM, 3000);
    }

    public String rename(String phonePath) {
        /*RENAME[RENAME.length - 1] = phonePath;
        return executeCommandWithTimeout(RENAME, 3000);*/
        return "Unimplemented";
    }

    public String install(String path) {
        ADB_INSTALL[ADB_INSTALL.length - 1] = path;
        return executeCommandWithTimeout(ADB_INSTALL, 3000);
    }
    public String createInstall(int totalSizeBytes) {
        INSTALL_CREATE[INSTALL_CREATE.length - 1] = String.valueOf(totalSizeBytes);
        return executeCommandWithTimeout(INSTALL_CREATE, 3000);
    }

    public String installMultiple(String[] apks) {
        String[] installMultiple = joinCommand(ADB_INSTALL_MULTIPLE, apks);
        return executeCommandWithTimeout(installMultiple, 3000);
    }
    public String installWrite(long splitApkSize, int sessionId, int index, String path) {
        INSTALL_WRITE[INSTALL_WRITE.length - 4] = String.valueOf(splitApkSize);
        INSTALL_WRITE[INSTALL_WRITE.length - 3] = String.valueOf(sessionId);
        INSTALL_WRITE[INSTALL_WRITE.length - 2] = String.valueOf(index);
        INSTALL_WRITE[INSTALL_WRITE.length - 1] = path;
        return executeCommandWithTimeout(INSTALL_WRITE, 3000);
    }

    public String installCommit(int sessionId) {
        INSTALL_COMMIT[INSTALL_COMMIT.length - 1] = String.valueOf(sessionId);
        return executeCommandWithTimeout(INSTALL_COMMIT, 3000);
    }

    public String installExistingPackage(String pkgName, int maxOutputLen) {
        INSTALL_BACK[INSTALL_BACK.length - 1] = pkgName;
        return executeCommandTrim(INSTALL_BACK, maxOutputLen);
    }

    public String installsAsSystemApp(String apkPath, String appDir) {
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

    public String root() {
        return executeCommandWithTimeout(ADB_ROOT, 3000);
    }

    public String unroot() {
        return executeCommandWithTimeout(ADB_UNROOT, 3000);
    }

    public String remountReadOnly(String partition) {
        MOUNT_READ_ONLY[MOUNT_READ_ONLY.length - 1] = partition;
        return executeCommandWithTimeout(MOUNT_READ_ONLY, 3000);
    }

    public String remountReadWrite(String partition) {
        MOUNT_READ_WRITE[MOUNT_READ_WRITE.length - 1] = partition;
        return executeCommandWithTimeout(MOUNT_READ_WRITE, 3000);
    }

    // These commands don't require a timeout
    public String listDevices() {
        return executeCommandWithTimeout(DEVICES, 50);
    }

    // But it makes them more reliable
    public String listPackages() {
        return executeCommandWithTimeout(LIST_PACKAGES, 50);
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
        }
        LIST_PACKAGES_BY_TYPE[LIST_PACKAGES_BY_TYPE.length - 1] = modifier;
        return executeCommandWithTimeout(LIST_PACKAGES_BY_TYPE, 50);
    }
}

enum PackageType {
    SYSTEM, USER, ALL
}