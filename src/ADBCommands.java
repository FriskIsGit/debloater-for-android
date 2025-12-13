import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    private CommandTemplate PM_UNINSTALL_PER_USER, PM_UNINSTALL_PER_USER_KEEP, DISABLE_USER,
            LIST_PACKAGES_BY_TYPE, LIST_PACKAGES_WITH_UID, PM_CHANGE_PERM,
            TAR, CHOWN, CHMOD, EXTRACT_TAR, RESTORECON, RM, RM_RECURSE_FORCE, MK_DIR, PM_PATH, DEVICES,
            ADB_PULL, ADB_PUSH, ADB_INSTALL, ADB_INSTALL_MULTIPLE, ADB_ROOT, ADB_UNROOT,
            INSTALL_BACK, INSTALL_CREATE, INSTALL_WRITE, INSTALL_COMMIT, EXISTS,
            REMOUNT_READ_ONLY, REMOUNT_READ_WRITE, MOUNT, CHECK_SU, MOVE, COPY, GET_SELINUX_MODE,
            GET_PROP, SET_PROP, DIRECTORY_SIZE, LS, DISK_FREE, GET_BUILD, DMCTL, TUNE2FS, REBOOT,
            SHELL_LOGCAT, GET_SYSTEM_PROC_MOUNTS, DD;

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
        DISABLE_USER = new CommandTemplate(adbTerms, "shell", "pm", "disable-user", "");
        INSTALL_BACK = new CommandTemplate(adbTerms, "shell", "pm", "install-existing", "");
        DEVICES = new CommandTemplate(adbTerms, "devices");
        PM_PATH = new CommandTemplate(adbTerms, "shell", "pm", "path", "");
        ADB_PULL = new CommandTemplate(adbTerms, "pull", "");
        TAR = new CommandTemplate(adbTerms, "shell", "tar", "cfp", "", "-C", "", "");
        EXTRACT_TAR = new CommandTemplate(adbTerms, "shell", "tar", "xfp", "", "-C", "");
        CHOWN = new CommandTemplate(adbTerms, "shell", "chown", "", "");
        CHMOD = new CommandTemplate(adbTerms, "shell", "chmod", "", "");
        RESTORECON = new CommandTemplate(adbTerms, "shell", "restorecon", "-r", "-n", "v", "");
        RM = new CommandTemplate(adbTerms, "shell", "rm", "-f", "");
        RM_RECURSE_FORCE = new CommandTemplate(adbTerms, "shell", "rm", "-rf", "");
        ADB_PUSH = new CommandTemplate(adbTerms, "push", "", "");
        MK_DIR = new CommandTemplate(adbTerms, "shell", "mkdir", "-p", "");
        LIST_PACKAGES_BY_TYPE = new CommandTemplate(adbTerms, "shell", "pm", "list", "packages", "");
        LIST_PACKAGES_WITH_UID = new CommandTemplate(adbTerms, "shell", "pm", "list", "packages", "-U", "");
        INSTALL_CREATE = new CommandTemplate(adbTerms, "shell", "pm", "install-create", "-S", "");
        INSTALL_WRITE = new CommandTemplate(adbTerms, "shell", "pm", "install-write", "-S", "", "", "", "");
        INSTALL_COMMIT = new CommandTemplate(adbTerms, "shell", "pm", "install-commit", "");
        ADB_INSTALL_MULTIPLE = new CommandTemplate(adbTerms, "install-multiple");
        ADB_ROOT = new CommandTemplate(adbTerms, "root");
        ADB_UNROOT = new CommandTemplate(adbTerms, "unroot");
        REMOUNT_READ_ONLY = new CommandTemplate(adbTerms, "shell", "mount", "-o", "ro,remount", "");
        REMOUNT_READ_WRITE = new CommandTemplate(adbTerms, "shell", "mount", "-o", "rw,remount", "");
        MOUNT = new CommandTemplate(adbTerms, "shell", "mount");
        EXISTS = new CommandTemplate(adbTerms, "shell", "test", "-e", "", "&&", "echo", "Yes");
        CHECK_SU = new CommandTemplate(adbTerms, "shell", "id");
        MOVE = new CommandTemplate(adbTerms, "shell", "mv", "", "");
        COPY = new CommandTemplate(adbTerms, "shell", "cp", "", "");
        GET_SELINUX_MODE = new CommandTemplate(adbTerms, "shell", "getenforce");
        GET_PROP = new CommandTemplate(adbTerms, "shell", "getprop", "");
        SET_PROP = new CommandTemplate(adbTerms, "shell", "setprop", "", "");
        DIRECTORY_SIZE = new CommandTemplate(adbTerms, "shell", "du", "-sh", "");
        LS = new CommandTemplate(adbTerms, "shell", "ls", "");
        DISK_FREE = new CommandTemplate(adbTerms, "shell", "df", "");
        GET_BUILD = new CommandTemplate(adbTerms, "shell", "cat /system/build.prop | grep build.type");
        DMCTL = new CommandTemplate(adbTerms, "shell", "dmctl");
        TUNE2FS = new CommandTemplate(adbTerms, "shell", "tune2fs", "-l", "");
        PM_CHANGE_PERM = new CommandTemplate(adbTerms, "shell", "pm", "", "");
        REBOOT = new CommandTemplate(adbTerms, "reboot");
        SHELL_LOGCAT = new CommandTemplate(adbTerms, "shell", "logcat");
        GET_SYSTEM_PROC_MOUNTS = new CommandTemplate(adbTerms, "shell", "cat /proc/mounts | grep /system");
        DD = new CommandTemplate(adbTerms, "shell", "dd");
        setupLateInitCommands(adbTerms);
    }

    private void setupLateInitCommands(String... adbTerms) {
        int version = getAndroidVersion();
        String bypass = "--bypass-low-target-sdk-block";
        ADB_INSTALL = new CommandTemplate(adbTerms, "install", version >= 14 ? bypass : "");
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
        return executeCommandWithTimeout(DISABLE_USER.build(pkgName), 3000);
    }

    public String installExistingPackage(String pkgName) {
        return executeCommandWithTimeout(INSTALL_BACK.build(pkgName), 3000);
    }

    public String getPackagePath(String pkgName) {
        return executeCommandWithTimeout(PM_PATH.build(pkgName), 3000);
    }

    public String changeOwnership(String owner, String group, String phonePath) {
        return changeOwnership(owner, group, phonePath, false);
    }

    public String changeOwnership(String owner, String group, String phonePath, boolean recurse) {
        String[] command = recurse ?
                CHOWN.build(isSU(), "-R", owner + ":" + group, phonePath) :
                CHOWN.build(isSU(), owner + ":" + group, phonePath);
        System.out.println(Arrays.toString(command));
        return executeCommandWithTimeout(command, 3000);
    }

    public String chmod(String permissions, String phonePath) {
        String[] command = CHMOD.build(isSU(), permissions, phonePath);
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

    public String pull(String phonePath) {
        return executeCommandWithTimeout(ADB_PULL.build(phonePath), 3000);
    }

    public String push(String pcPath, String phonePath) {
        String[] command = ADB_PUSH.build(pcPath, phonePath);
        return executeCommandWithTimeout(command, 3000);
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

    public String rmRecurseForce(String phoneDir) {
        String[] command = RM_RECURSE_FORCE.build(isSU(), phoneDir);
        System.out.println(Arrays.toString(command));
        return executeCommandWithTimeout(command, 10_000);
    }

    public boolean exists(String phonePath) {
        String[] command = EXISTS.build(isSU(), phonePath);
        System.out.println(Arrays.toString(command));
        return executeCommandWithTimeout(command, 3000).startsWith("Yes");
    }

    public String install(String path) {
        return executeCommandWithTimeout(ADB_INSTALL.build(path), 3000);
    }

    public String installReplace(String path) {
        return executeCommandWithTimeout(ADB_INSTALL.build("-r", path), 3000);
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
        String[] command = REMOUNT_READ_ONLY.build(isSU(), partition);
        System.out.println(Arrays.toString(command));
        return executeCommandWithTimeout(command, 3000);
    }

    public String remountReadWrite(String partition) {
        String[] command = REMOUNT_READ_WRITE.build(isSU(), partition);
        System.out.println(Arrays.toString(command));
        return executeCommandWithTimeout(command, 3000);
    }

    public String mountAll() {
        String[] command = MOUNT.build("-a");
        System.out.println(Arrays.toString(command));
        return executeCommandWithTimeout(command, 3000);
    }

    public String mount(String src, String dest) {
        String[] command = MOUNT.build(src, dest);
        System.out.println(Arrays.toString(command));
        return executeCommandWithTimeout(command, 3000);
    }

    public String mount(String fs, String opts, String src, String dest) {
        String[] command = MOUNT.build("-t", fs, "-o", opts, src, dest);
        System.out.println(Arrays.toString(command));
        return executeCommandWithTimeout(command, 3000);
    }

    public List<Device> listDevices() {
        String devicesOutput = executeCommandWithTimeout(DEVICES.build(), 50);
        System.out.println(devicesOutput);
        List<String> lines = splitOutputLines(devicesOutput);
        List<Device> devices = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isEmpty()) {
                continue;
            }
            int space = line.indexOf('\t');
            if (space == -1) {
                space = line.indexOf(' ');
                if (space == -1) {
                    continue;
                }
            }
            String serial = line.substring(0, space).trim();
            String status = line.substring(space + 1).trim();
            devices.add(new Device(serial, status));
        }
        return devices;
    }

    public String listPackagesWithUID(PackageType type) {
        String modifier = getPackageModifier(type);
        String[] command = LIST_PACKAGES_WITH_UID.build(modifier);
        return executeCommandWithTimeout(command, 50);
    }

    public int getAndroidVersion() {
        String v = executeCommandWithTimeout(GET_PROP.build("ro.build.version.release"), 50);
        int dot = v.indexOf('.');
        String major = (dot == -1) ? v : v.substring(0, dot);
        try {
            return Integer.parseInt(major);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public String getBuildType() {
        String[] command = GET_BUILD.build(isSU());
        String buildOutput = executeCommandWithTimeout(command, 10_000);
        List<String> lines = splitOutputLines(buildOutput);
        return lines.isEmpty() ? "" : getPairValue(lines.get(0));
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

    public String move(String phoneSrc, String phoneDestination) {
        String[] command = MOVE.build(isSU(), phoneSrc, phoneDestination);
        System.out.println(Arrays.toString(command));
        return executeCommandWithTimeout(command, 10_000);
    }

    public String copy(String phoneSrc, String phoneDestination) {
        String[] command = COPY.build(isSU(), phoneSrc, phoneDestination);
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

    public String getProp(String key) {
        String[] command = GET_PROP.build(isSU(), key);
        System.out.println(Arrays.toString(command));
        String propOutput = executeCommandWithTimeout(command, 10_000);
        List<String> lines = splitOutputLines(propOutput);
        return lines.isEmpty() ? null : lines.get(0);
    }

    // Returns phone directory's size in bytes or -1 if dir doesn't exist
    public long getDirectorySize(String phoneDir) {
        String[] command = DIRECTORY_SIZE.build(phoneDir);
        System.out.println(Arrays.toString(command));
        String duRes = executeCommandWithTimeout(command, 10_000);
        if (duRes.startsWith("du:") || duRes.isEmpty()) {
            return -1;
        }
        int space = duRes.indexOf('\t');
        if (space == -1) {
            space = duRes.indexOf(' ');
            if (space == -1) {
                return -1;
            }
        }
        String sizeFormat = duRes.substring(0, space).trim();
        if (sizeFormat.isEmpty()) {
            return 0;
        }
        String value = sizeFormat.substring(0, sizeFormat.length()-1);
        double val = Double.parseDouble(value);
        char unit = sizeFormat.charAt(sizeFormat.length()-1);
        switch (unit) {
            case 'T':
                return (long)(val * 1024 * 1024 * 1024 * 1024);
            case 'G':
                return (long)(val * 1024 * 1024 * 1024);
            case 'M':
                return (long)(val * 1024 * 1024);
            case 'K':
                return (long)(val * 1024);
            default:
                return (long)(val);
        }
    }

    public List<String> listItems(String phoneDir) {
        String[] command = LS.build(isSU(), "-1", phoneDir);
        String output = executeCommandWithTimeout(command, 10_000);
        return splitOutputLines(output);
    }

    // Find a way to parse directories with spaces in name
    public List<DirEntry> listDirectorySU(String phoneDir) {
        String[] command = LS.build(isSU(), "-l", phoneDir);
        String output = executeCommandWithTimeout(command, 10_000);
        return splitOutputLines(output).stream().skip(1).map(DirEntry::fromLine).collect(Collectors.toList());
    }

    public long getAvailableSpaceInBytes(String phoneDir) {
        String[] command = DISK_FREE.build(phoneDir);
        String dfResult = executeCommandWithTimeout(command, 10_000);
        if (dfResult.startsWith("df:") || dfResult.startsWith("/system/bin/sh:")) {
            return -1;
        }
        List<String> lines = splitOutputLines(dfResult);
        String headerLine = lines.get(0);
        String valuesLine = lines.get(1);
        int availableIndex = headerLine.indexOf("Available");
        String value = valuesLine.substring(availableIndex, availableIndex + "Available".length());
        // df returns values in KB
        return 1024 * Long.parseLong(value.trim());
    }

    public List<String> dmctlListDevices() {
        String[] command = DMCTL.build(isSU(), "list", "devices");
        String devicesResult = executeCommandWithTimeout(command, 10_000);
        List<String> devices = splitOutputLines(devicesResult);
        return devices.stream()
                .skip(1)
                .filter(dev -> !dev.startsWith("com.android"))
                .map(dev -> dev.substring(0, dev.indexOf(' ')))
                .collect(Collectors.toList());
    }

    public DmctlTable dmctlTable(String device) {
        String[] command = DMCTL.build(isSU(), "table", device);
        String tableResult = executeCommandWithTimeout(command, 10_000);
        List<String> lines = splitOutputLines(tableResult);
        if (lines.size() < 2) {
            return null;
        }
        String deviceDetails = lines.get(1);
        if (deviceDetails.startsWith("Could not query table status of device")) {
            return null;
        }
        return DmctlTable.fromLine(deviceDetails);
    }

    public String dmctlGetPath(String device) {
        String[] command = DMCTL.build(isSU(), "getpath", device);
        return executeCommandWithTimeout(command, 10_000);
    }

    public String dmctlReplace(String device, boolean readOnly, DmctlTable table) {
        String[] command = DMCTL.build(isSU(), "replace", device, readOnly ? "-ro" : "rw", "TODO");
        return executeCommandWithTimeout(command, 10_000);
    }

    public String tune2fsList(String blockPath) {
        String[] command = TUNE2FS.build(isSU(), blockPath);
        System.out.println(Arrays.toString(command));
        return executeCommandWithTimeout(command, 10_000);
    }

    public String pmGrantPermission(String permission, String packageName, boolean force) {
        if (!permission.startsWith("android.permission.")) {
            permission = "android.permission." + permission;
        }
        return pmChangePermission(true, force, packageName, permission);
    }

    public String pmRevokePermission(String permission, String packageName, boolean force) {
        if (!permission.startsWith("android.permission.")) {
            permission = "android.permission." + permission;
        }
        return pmChangePermission(false, force, packageName, permission);
    }

    public String pmChangePermission(boolean grant, boolean force, String packageName, String permission) {
        String action = grant ? "grant" : "revoke";
        String[] command = force ?
                PM_CHANGE_PERM.buildSUWithSUArgs(Arrays.asList("-l", "1000"), action, packageName, permission) :
                PM_CHANGE_PERM.build(action, packageName, permission);
        System.out.println(Arrays.toString(command));
        return executeCommandWithTimeout(command, 10_000);
    }

    public String rebootRecovery() {
        return executeCommandWithTimeout(REBOOT.build("recovery"), 10_000);
    }

    public String reboot() {
        return executeCommandWithTimeout(REBOOT.build(), 10_000);
    }

    public String dumpLogs(String phonePath) {
        String[] command = SHELL_LOGCAT.build("-d", "-f", phonePath);
        System.out.println(Arrays.toString(command));
        return executeCommandWithTimeout(command, 3000);
    }

    public List<MountEntry> getSystemProcMounts() {
        String[] command = GET_SYSTEM_PROC_MOUNTS.build();
        String mountsOutput = executeCommandWithTimeout(command, 3000);
        List<String> lines =  splitOutputLines(mountsOutput);
        List<MountEntry> mounts = new ArrayList<>();
        for (String line : lines) {
            String[] parts = line.split(" ");
            if (parts.length < 4) {
                continue;
            }
            MountEntry entry = new MountEntry(parts[0], parts[1], parts[2], parts[3]);
            mounts.add(entry);
        }
        return mounts;
    }

    public String dd(String input, String output) {
        String[] command = DD.buildSU("if=" + input, "of=" + output);
        System.out.println(Arrays.toString(command));
        return executeCommandWithTimeout(command, 10_000);
    }

    public static List<String> splitOutputLines(String output) {
        return splitOutputLines(output, true);
    }

    private static List<String> splitOutputLines(String output, boolean skipEmpty) {
        List<String> lines = new ArrayList<>();
        int st = 0;
        while (true) {
            int lineEnd = output.indexOf("\n", st);
            if (lineEnd == -1) {
                break;
            }
            int contentEnd = lineEnd;
            boolean hasCarriageReturn = contentEnd - 1 >= 0 && output.charAt(contentEnd-1) == '\r';
            if (hasCarriageReturn) {
                contentEnd--;
            }
            String line = output.substring(st, contentEnd);
            st = lineEnd + 1;

            if (line.isEmpty() && skipEmpty) {
                continue;
            }
            lines.add(line);
        }
        return lines;
    }

    private static String getPairValue(String pair) {
        int eq = pair.indexOf('=');
        if (eq == -1 || eq == pair.length() - 1) {
            return "";
        }
        return pair.substring(eq + 1);
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
        return build(su, Collections.emptyList(), args);
    }

    public String[] buildSUWithSUArgs(List<String> suArgs, String... args) {
        return build(true, suArgs, args);
    }

    public String[] build(boolean su, List<String> suArgs, String... args) {
        List<String> command = new ArrayList<>(adbTerms.length + components.length + 2);
        List<String> filledArgs = new ArrayList<>();

        command.addAll(Arrays.asList(adbTerms));
        boolean isShell = components.length > 0 && components[0].equals("shell");
        boolean wrapInner = su && isShell;
        int startIndex = 0;
        if (wrapInner) {
            command.add("shell");
            command.add("su");
            command.addAll(suArgs);
            command.add("-c");
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

class DmctlTable {
    long start, end;
    String target, targetParams;

    public DmctlTable(long start, long end, String target, String targetParams) {
        this.start = start;
        this.end = end;
        this.target = target;
        this.targetParams = targetParams;
    }

    public static DmctlTable fromLine(String line) {
        int colon = line.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        String[] range = line.substring(0, colon).split("-");
        if (range.length != 2) {
            return null;
        }
        long start = Long.parseLong(range[0]);
        long end = Long.parseLong(range[1]);
        int comma = line.indexOf(',', colon + 1);
        if (comma == -1) {
            return null;
        }
        String target = line.substring(colon + 1, comma).trim();
        String params = line.substring(comma + 1).trim();
        return new DmctlTable(start, end, target, params);
    }

    @Override
    public String toString() {
        return start + "-" + end + ": " + target + ", " + targetParams;
    }
}

class Device {
    String serial;
    String status;

    public Device(String serial, String status) {
        this.serial = serial;
        this.status = status;
    }

    @Override
    public String toString() {
        return serial + " " + status;
    }
}

class MountEntry {
    public String source, target, fs, options;

    public MountEntry(String source, String target, String fs, String options) {
        this.source = source;
        this.target = target;
        this.fs = fs;
        this.options = options;
    }
}

class DirEntry {
    // drwxrwx--- 2 root everybody 3488 2025-07-25 09:17 Alarms -> somewhere
    public String date, time, name, pointsTo;

    public DirEntry(String date, String time, String name, String pointsTo) {
        this.date = date;
        this.time = time;
        this.name = name;
        this.pointsTo = pointsTo;
    }

    public static DirEntry fromLine(String line) {
        List<String> parts = Utilities.splitBy(line, ' ');
       int size = parts.size();
       if (size < 8) {
           return null;
       }
       String date = parts.get(5);
       String time = parts.get(6);
       String name = parts.get(7);
       String pointsTo = null;
       if (parts.get(size-2).equals("->")) {
           pointsTo = parts.get(size-1);
       }
       return new DirEntry(date, time, name, pointsTo);
    }
}


