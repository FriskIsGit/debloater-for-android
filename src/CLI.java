
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class CLI {
    private static final String PACKAGES_SRC = "/packages.txt";
    private static final boolean SKIP_DEVICE_STAGE = false;
    private static final String STORAGE_EMULATED_0 = "/storage/emulated/0/"; // symbolic link to /data/media/0/
    private static final String SYSTEM_PRIV_APP = "/system/priv-app/"; // possible to debloat these apps
    private static final String DATA_USER_0 = "/data/user/0/";
    private static final String EXPORT_TAR = "export.tar";
    private static final String IMPORT_TAR = "import.tar";
    private static final String DATA_EXPORT = "data-export";
    private static final String EXPORT = "export";
    private static final String TEMP_DIR = "temp_unpack";
    private static final String DEV_BLOCK_BY_NAME = "/dev/block/by-name/";

    private ADBCommands commands;
    private List<String> bloatedPackages;
    private final Scanner scanner = new Scanner(System.in);
    private Set<String> packages;


    public static void start(ADBCommands commands, String[] args) {
        CLI cli = new CLI();
        cli.commands = commands;
        cli.loadAndInit(args[0]);
        cli.start(args);
    }

    protected void loadAndInit(String action) {
        if (action.equals("debloat") || action.equals("debloat-full")) {
            URL url = Main.class.getResource(PACKAGES_SRC);
            if (url == null) {
                errorExit("Couldn't find/load: " + PACKAGES_SRC);
                return;
            }
            loadBloatedPackages(url);
        }
    }

    protected void loadBloatedPackages(URL url) {
        try {
            InputStream packagesStream = url.openStream();
            String readLines = Utilities.readFully(packagesStream);
            bloatedPackages = Utilities.readAllLines(readLines);
        } catch (IOException ioException) {
            errorExit("Error reading packages with file.. exiting");
        }
        System.out.println(bloatedPackages.size() + " packages loaded from packages.txt:");
        System.out.println(bloatedPackages);
    }

    protected void start(String[] args) {
        runDevicesStage();
        String action = args[0];
        switch (action) {
            case "debloat":
                debloat(false);
                break;
            case "debloat-full":
                debloat(true);
                break;
            case "debloat-undo":
                ensureArgument(args, 1, "Undoing debloat requires a list of packages");
                debloatUndo(args[1]);
                break;
            case "debloat-cust":
                debloatCust();
                break;

            // Management
            case "disable": {
                ensureArgument(args, 1, "No package name provided.");
                String pkg = args[1];
                String result = commands.disablePackageByName(pkg);
                System.out.println(result);
            } break;

            case "uninstall-keep": {
                ensureArgument(args, 1, "No package name provided.");
                String pkg = args[1];
                String result = commands.uninstallPackagePerUserKeepData(pkg);
                System.out.println(result);
            } break;

            case "uninstall": {
                ensureArgument(args, 1, "No package name provided.");
                String pkg = args[1];
                String result = commands.uninstallPackagePerUser(pkg);
                System.out.println(result);
            } break;

            case "install-existing":
            case "install-back": {
                ensureArgument(args, 1, "No package name provided.");
                String pkg = args[1];
                String result = commands.installExistingPackage(pkg);
                System.out.println(result);
            } break;

            case "install": {
                ensureArgument(args, 1, "No path provided.");
                String appPath = args[1];
                String ext = Utilities.getExtension(appPath);
                if (ext.equals("apkm")) {
                    List<String> apks = Collections.emptyList();
                    try {
                        apks = Utilities.unpackApkm(appPath, TEMP_DIR);
                    } catch (Exception e) {
                        errorExit(e.toString());
                    }
                    String res = commands.installMultiple(apks.toArray(new String[0]));
                    System.out.println(res);
                    System.out.println("Temporary files were put in " + TEMP_DIR);
                } else {
                    String result = commands.install(appPath);
                    System.out.println(result);
                }
            } break;

            case "install-system": {
                ensureArgument(args, 1, "No path given. Provide the path to the apk");
                String apkPath = args[1];
                if (!Utilities.getExtension(apkPath).equals("apk")) {
                    errorExit("The given app does not have .apk extension");
                }
                ensureArgument(args, 2, "No app directory name given where the apk will be put");
                String appDirName = args[2];
                String result = installAsSystemApp(apkPath, appDirName);
                System.out.println(result);
            } break;

            case "install-system-recovery": {
                ensureArgument(args, 1, "No path given. Provide the path to the apk");
                String apkPath = args[1];
                if (!Utilities.getExtension(apkPath).equals("apk")) {
                    errorExit("The given app does not have .apk extension");
                }
                ensureArgument(args, 2, "No app directory name given where the apk will be put");
                String appDirName = args[2];
                String result = installSystemAppInRecovery(apkPath, appDirName);
                System.out.println(result);
            } break;

            case "systemize": {
                ensureArgument(args, 1, "No path given. Provide the path to the apk");
                ensureArgument(args, 2, "No app directory name given where the apk will be put");
                String packageName = args[1];
                String appDirName = args[2];
                String result = systemizeApp(packageName, appDirName);
                System.out.println(result);
            } break;

            case "degoogle": {
                List<Device> devices = commands.listDevices();
                if (devices.isEmpty() || !devices.get(0).status.equals("recovery")) {
                    System.out.println("Device not in recovery mode. It'll be rebooted to recovery");
                    Utilities.askToProceedOrExit(scanner);
                    commands.rebootRecovery();
                    runDevicesStage();
                }
                System.out.println("Uninstalling GMS and Vending");
                commands.uninstallPackagePerUser("com.android.vending");
                commands.uninstallPackagePerUser("com.google.android.gms");
                String result = degoogleInRecovery();
                System.out.println(result);
            } break;
            case "test": {
            } break;

            case "uninstall-system": {
                ensureArgument(args, 1, "No path given.");
                // String name = args[1];
                errorExit("Unimplemented");
            } break;

            // Exports
            case "export": {
                ensureArgument(args, 1, noPackageOrFlagGivenErrorMessage(action));
                String name = args[1];
                if (!name.startsWith("-")) {
                    Options opts = Options.parseOptions(args,2);
                    String outputDir = opts.dir != null ? opts.dir : EXPORT;
                    exportByName(name, outputDir);
                    return;
                }
                Options opts = Options.parseOptions(args,1);
                String outputDir = opts.dir != null ? opts.dir : EXPORT;
                export(opts.packageType, outputDir);
            } break;

            case "export-data": {
                ensureArgument(args, 1, noPackageOrFlagGivenErrorMessage(action));
                String pkgName = args[1];
                if (!pkgName.startsWith("-")) {
                    Options opts = Options.parseOptions(args,2);
                    String outputDir = opts.dir != null ? opts.dir : DATA_EXPORT;
                    exportDataByName(pkgName, outputDir);
                    return;
                }
                Options opts = Options.parseOptions(args,1);
                String outputDir = opts.dir != null ? opts.dir : DATA_EXPORT;
                exportAppsData(opts.packageType, outputDir);
            } break;

            // Imports
            case "import": {
                ensureArgument(args, 1, noPackageOrFlagGivenErrorMessage(action));
                String name = args[1];
                if (!name.startsWith("-")) {
                    Options opts = Options.parseOptions(args,2);
                    String importDir = opts.dir != null ? opts.dir : EXPORT;
                    importApps(name, importDir);
                    return;
                }
                Options opts = Options.parseOptions(args,1);
                String importDir = opts.dir != null ? opts.dir : EXPORT;
                importApps(null, importDir);
            } break;

            case "import-data": {
                ensureArgument(args, 1, noPackageOrFlagGivenErrorMessage(action));
                String pkgName = args[1];
                if (!pkgName.startsWith("-")) {
                    Options opts = Options.parseOptions(args,2);
                    String dataDir = opts.dir != null ? opts.dir : DATA_EXPORT;
                    importDataByName(pkgName, dataDir);
                    return;
                }
                Options opts = Options.parseOptions(args,1);
                String dataDir = opts.dir != null ? opts.dir : DATA_EXPORT;
                importAppsData(dataDir);
            } break;

            // Limited by pm checks - is not a changeable permission type
            case "grant": {
                Options opts = Options.parseOptions(args, 3);
                ensureArgument(args, 2, "Usage: grant <permission> <package> [options]");
                String permName = args[1];
                String packageName = args[2];
                Permission perm = Permission.from(permName);
                if (perm == null) {
                    System.out.println("Warning: Unrecognized permission name: " + permName);
                }
                String res = commands.pmGrantPermission(perm == null ? permName : perm.name, packageName, opts.force);
                System.out.println(res);
            } break;

            // Limited by pm checks - is not a changeable permission type
            case "revoke": {
                Options opts = Options.parseOptions(args, 3);
                ensureArgument(args, 2, "Usage: revoke <permission> <package> [options]");
                String permName = args[1];
                String packageName = args[2];
                Permission perm = Permission.from(permName);
                if (perm == null) {
                    System.out.println("Warning: Unrecognized permission name: " + permName);
                }
                String res = commands.pmRevokePermission(perm == null ? permName : perm.name, packageName, opts.force);
                System.out.println(res);
            } break;

            case "get-logs": {
                String logFile = "logs.txt";
                String phonePath = STORAGE_EMULATED_0 + logFile;
                String logcatResult = commands.dumpLogs(phonePath);
                if (logcatResult.startsWith("logcat:")) {
                    errorExit(logcatResult);
                }
                String pullResult = commands.pull(phonePath);
                System.out.println(pullResult);
                commands.rm(phonePath);
            } break;

            case "ab-info":
            case "AB-info": {
                String abUpdate = commands.getProp("ro.build.ab_update");
                String slotSuffix = commands.getProp("ro.boot.slot_suffix");
                System.out.println("A/B partitioned: " + abUpdate);
                System.out.println("Slot suffix: " + slotSuffix);
            } break;

            case "list": {
                Options opts = Options.parseOptions(args, 1);
                String res = commands.listPackagesWithUID(opts.packageType);
                List<App> apps = Packages.parseWithUID(res);
                apps.forEach(System.out::println);
                System.out.println("Count: " + apps.size());
            } break;

            case "Android":
            case "android": {
                System.out.println("Android " + commands.getAndroidVersion());
            } break;

            case "checkSU": {
                boolean res = commands.checkSU();
                System.out.println("SU access: " + res);
            } break;

            case "SELinux":
            case "selinux":
                System.out.println("SE Linux mode: " + commands.getSELinuxMode());
                break;

            case "get-build":
                commands.ensurePrivileged();
                System.out.println("Build type: " + commands.getBuildType());
                break;

            case "get-img": {
                commands.ensurePrivileged();
                ensureArgument(args, 1, "Provide img name to pull from " + DEV_BLOCK_BY_NAME);
                String name = args[1];
                String srcBlock = DEV_BLOCK_BY_NAME + name;
                if (commands.privilege == PrivilegeType.ADB_ROOT) {
                    System.out.println("ADB root available, pulling straight from " + srcBlock);
                    String pullResult = commands.pull(srcBlock, name + ".img");
                    System.out.println(pullResult);
                    return;
                } else if (commands.privilege == PrivilegeType.SU) {
                    String phoneDest = STORAGE_EMULATED_0 + name + ".img";
                    String ddRes = commands.dd(srcBlock, phoneDest);
                    if (ddRes.startsWith("dd:")) {
                        System.out.println(ddRes);
                        return;
                    }
                    System.out.println(ddRes);
                    System.out.println("Pulling image from " + phoneDest);
                    commands.pull(phoneDest);
                    commands.rm(phoneDest);
                }
            } break;

            case "adbInstall": {
                ensureArgument(args, 1, "Select 'on' or 'off'");
                String value = args[1].equals("on") ? "1" : "0";
                String propRes = commands.setProp("persist.security.adbinstall", value);
                System.out.println(propRes);
            } break;

            case "adbInput": {
                ensureArgument(args, 1, "Select 'on' or 'off'");
                String value = args[1].equals("on") ? "1" : "0";
                String propRes = commands.setProp("persist.security.adbinput", value);
                System.out.println(propRes);
            } break;

            case "get-data-size": {
                commands.ensurePrivileged();
                long size = commands.getDirectorySize(DATA_USER_0);
                System.out.println(DATA_USER_0 + " = " + Utilities.formatBtoMB(size));
            } break;

            case "mount-system": {
                String mountRes = commands.mount("/dev/block/bootdevice/by-name/system", "/system_root");
                if (mountRes.startsWith("mount: ")) {
                    errorExit(mountRes);
                }
            } break;

            case "to-recovery": {
                commands.rebootRecovery();
            } break;

            case "test-dm": {
                commands.ensurePrivileged();
                String remountRootRes = commands.remountReadWrite("/");
                System.out.println(remountRootRes);
                List<String> devices = commands.dmctlListDevices();
                Optional<String> maybeDevice = devices.stream().filter(e -> e.equals("system")).findFirst();
                String device = null;
                if (maybeDevice.isPresent()) {
                    device = maybeDevice.get();
                }
                if (device == null && !devices.isEmpty()) {
                    device = devices.get(0);
                }
                if (device == null) {
                    return;
                }
                DmctlTable table = commands.dmctlTable(device);
                System.out.println("Picked device: " + device);
                System.out.println("Device table: " + table);
                String mountPath = commands.dmctlGetPath(device);
                System.out.println("Mount path: " + mountPath);
                System.out.println("Replace with read-write?");
                Utilities.askToProceedOrExit(scanner);
                String replaceResult = commands.dmctlReplace(device, table);
                System.out.println(replaceResult);
                if (replaceResult.contains("Failed to replace")) {
                    return;
                }
            } break;

            default:
                errorExit("Unrecognized action command: " + action);
                break;
        }
    }

    private String degoogleInRecovery() {
        String systemRoot = "/system_root";

        List<MountEntry> mounts = commands.getSystemProcMounts();
        if (mounts.stream().noneMatch(m -> m.target.equals(systemRoot))) {
            System.out.println(systemRoot  + " isn't mounted, mounting");
            String mountRes = commands.mount("/dev/block/bootdevice/by-name/system", systemRoot);
            if (mountRes.startsWith("mount: ")) {
                return mountRes;
            }
        }
        final List<String> GOOGLE_APP_DIRECTORIES = Arrays.asList(
                "GmsCore", "Phonesky", "GoogleServicesFramework", "GooglePartnerSetup", "Velvet", "GoogleOneTimeInitializer",
                "GoogleBackupTransport", "PrebuiltGmsCore", "PrebuiltGmsCorePi", "Maps", "Youtube");
        final List<String> SYSTEM_APP_LOCATIONS = Arrays.asList(
                "/system/product/priv-app/", "/system/product/app/", "/system/priv-app/", "/system/app/",
                "/system/system_ext/priv-app/");

        List<String> removableLocations = new ArrayList<>();
        for (String appLocation : SYSTEM_APP_LOCATIONS) {
            String fullAppLocation = systemRoot + appLocation;
            List<String> appDirs = commands.listItems(fullAppLocation).stream()
                    .filter(GOOGLE_APP_DIRECTORIES::contains)
                    .map(dir -> fullAppLocation + dir)
                    .collect(Collectors.toList());
            removableLocations.addAll(appDirs);
        }

        String splitPermsXml = systemRoot + "/system/product/etc/permissions/split-permissions-google.xml";
        if (commands.exists(splitPermsXml)) {
            removableLocations.add(splitPermsXml);
        }

        if (removableLocations.isEmpty()) {
            System.out.println("Nothing to remove.");
            return "";
        }

        System.out.println("Removable locations:");
        for(String location : removableLocations) {
            System.out.println("  " + location);
        }

        if (!Utilities.askToDelete(scanner)) {
            return "";
        }

        for(String location : removableLocations) {
            String rmRes = commands.rmRecurseForce(location);
            if (rmRes.startsWith("rm: ")) {
                System.err.println(rmRes);
            }
        }
        return "";
    }

    private String noPackageOrFlagGivenErrorMessage(String action) {
        return "No package name or flag given. Usage: " + action + " <name> [options]";
    }

    private void importDataByName(String pkgName, String importDir) {
        commands.ensurePrivileged();
        File importFrom = new File(importDir);
        if (!importFrom.exists()) {
            errorExit("There's no directory of name " + importDir);
            return;
        }
        String appTar = pkgName + ".tar";
        Path localTar = importFrom.toPath().resolve(appTar);
        if (!Files.exists(localTar)) {
            errorExit("There's no file at " + localTar);
            return;
        }

        String packagesResult = commands.listPackagesBy(PackageType.ALL);
        Set<String> installed = Packages.parseToSet(packagesResult);
        if (!installed.contains(pkgName)) {
            errorExit("The app is not installed, install it first.");
            return;
        }
        String phoneTar = STORAGE_EMULATED_0 + IMPORT_TAR;
        String pushResult = commands.push(localTar.toString(), phoneTar);
        System.out.println(pushResult);
        commands.extractTar(phoneTar, DATA_USER_0);
        commands.rm(phoneTar);
        String packagesWithUID = commands.listPackagesWithUID(PackageType.ALL);
        List<App> apps = Packages.parseWithUID(packagesWithUID);
        App targetApp = apps.stream().filter(app -> app.name.equals(pkgName)).findFirst().get();
        System.out.println("Target app: " + targetApp);
        commands.changeOwnership(targetApp.uid, targetApp.uid, DATA_USER_0 + pkgName, true);
    }

    private void importAppsData(String outputDir) {
        String packagesWithUID = commands.listPackagesWithUID(PackageType.ALL);
        List<App> apps = Packages.parseWithUID(packagesWithUID);
        errorExit("Unimplemented");
    }

    private void exportDataByName(String pkgName, String outputDir) {
        commands.ensurePrivileged();
        ensureDirectory(outputDir);

        String phoneDataDir = DATA_USER_0 + pkgName;
        if (!commands.exists(phoneDataDir)) {
            errorExit(phoneDataDir + " does not exist");
        }

        String appDataTar = STORAGE_EMULATED_0 + EXPORT_TAR;
        commands.tar(appDataTar, DATA_USER_0, pkgName);

        String pullOutput = commands.pull(appDataTar, outputDir + "/" + pkgName + ".tar");
        System.out.println(pullOutput);
        commands.rm(appDataTar);
    }

    private void exportAppsData(PackageType type, String outputDir) {
        commands.ensurePrivileged();
        ensureDirectory(outputDir);
        String packagesRes = commands.listPackagesBy(type);
        List<String> packages = Packages.parseToList(packagesRes);
        System.out.println("Backing up data from " + packages.size() + " packages");
        boolean tarredAny = false;
        for (String pkgName : packages) {
            String phoneDataDir = DATA_USER_0 + pkgName;
            if (!commands.exists(phoneDataDir)) {
                System.err.println(phoneDataDir + " does not exist, skipping");
                continue;
            }

            String appDataTar = STORAGE_EMULATED_0 + EXPORT_TAR;
            commands.tar(appDataTar, DATA_USER_0, pkgName);
            tarredAny = true;
            String pullOutput = commands.pull(appDataTar, outputDir + "/" + pkgName + ".tar");
            System.out.println(pullOutput);
        }

        if (tarredAny) {
            String rmResult = commands.rm(STORAGE_EMULATED_0 + EXPORT_TAR);
            System.out.println(rmResult);
        }
    }

    private void ensureDirectory(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists() && !dir.mkdirs()) {
            errorExit("Unable to create " + dir + " directory");
        }
    }

    private void ensureArgument(String[] args, int index, String errorMessage) {
        if (index < args.length) {
            return;
        }
        errorExit(errorMessage);
    }

    private void runDevicesStage() {
        if (SKIP_DEVICE_STAGE) {
            System.out.println("Warning: Skipping device stage");
            return;
        }
        List<Device> devices = commands.listDevices();
        long connected = devices.stream()
                .filter(device -> device.status.equals("device") || device.status.equals("recovery"))
                .count();
        System.out.println(connected + (connected == 1 ? " connected device" : " connected devices"));

        if (devices.isEmpty()) {
            System.out.println("No devices detected (is 'USB debugging' enabled?), press enter to refresh");
            if (scanner.hasNextLine()) {
                scanner.nextLine();
            }
            runDevicesStage();
        } else if (devices.size() > 1) {
            System.err.println("Error: more than one device/emulator");
        }
    }

    private void importApps(String name, String exportDir) {
        File export = new File(exportDir);
        if (!export.exists()) {
            System.err.println(export.getAbsolutePath() + " not found");
            errorExit("Create an export or put an .apk in " + EXPORT + "/com.app.name/");
            return;
        }

        Set<String> allPackages = Packages.parseToSet(commands.listPackagesBy(PackageType.ALL));
        File[] apkDirs = name == null ?
                export.listFiles(f -> f.isDirectory() && !allPackages.contains(f.getName())) :
                export.listFiles(f -> f.isDirectory() && f.getName().equals(name));

        if (apkDirs == null || apkDirs.length == 0) {
            errorExit("There's nothing to import or imported apps are already installed.");
            return;
        }

        if (name == null) {
            System.out.println("Install possibly " + apkDirs.length + " APKs?");
            Utilities.askToProceedOrExit(scanner);
        }

        int i = 0;
        for (File apkDir : apkDirs) {
            System.out.println("Installing " + apkDir.getName());
            File[] apks = apkDir.listFiles(file -> file.isFile() && file.getName().endsWith(".apk"));
            assert apks != null;

            String installOutput;
            if (apks.length == 1) {
                installOutput = commands.install(apks[0].getPath());
            } else {
                String[] apkPaths = Utilities.filesToPaths(apkDirs);
                installOutput = commands.installMultiple(apkPaths);
            }

            System.out.println(installOutput);
            System.out.println("[" + (++i) + "/" + apkDirs.length + "]");
        }
    }

    private void exportByName(String pkgName, String outputDir) {
        File export = new File(outputDir);
        if (!export.exists() && !export.mkdirs()) {
            errorExit("Unable to create export directory");
            return;
        }

        String output = commands.getPackagePath(pkgName);
        if (output.isEmpty()) {
            errorExit(pkgName + " doesn't exist?");
            return;
        }
        List<String> apks = ADBCommands.splitOutputLines(output).stream()
                .filter(path -> path.length() > 8)
                .map(path -> path.substring(8)) // trim package: prefix
                .collect(Collectors.toList());
        if (apks.isEmpty()) {
            errorExit("Nothing to export.");
            return;
        }

        File pkgExport = Paths.get(outputDir).resolve(pkgName).toFile();
        if (!pkgExport.exists() && !pkgExport.mkdirs()) {
            errorExit("Unable to create " + pkgName + " directory");
            return;
        }
        for (String apk : apks) {
            if (apk.isEmpty()) {
                continue;
            }
            String pullOutput = commands.pullAPK(apk, pkgExport.toString());
            System.out.println(pullOutput);
        }
    }

    private void export(PackageType type, String outputDir) {
        String output = commands.listPackagesBy(type);
        if (output.startsWith("package")) {
            packages = Packages.parseToSet(output);
        } else if (output.startsWith("java.lang.UnsatisfiedLinkError")) {
            errorExit("'pm list packages' command failed - can't export");
            return;
        } else {
            errorExit(output);
            return;
        }
        int pulled = 0, errors = 0;
        File export = new File(outputDir);
        if (!export.exists() && !export.mkdirs()) {
            errorExit("Unable to create export directory");
            return;
        }
        System.out.println(packages);
        System.out.println("Backing up " + packages.size() + " packages");
        Utilities.askToProceedOrExit(scanner);
        int counter = 1;
        long st = System.currentTimeMillis();
        for (String pkg : packages) {
            output = commands.getPackagePath(pkg);
            if (output.isEmpty()) {
                System.err.println(pkg + " is incorrectly displayed by the package manager as an existing package");
                continue;
            }
            List<String> apks = Packages.parseToList(output);

            File pkgExport = Paths.get(outputDir).resolve(pkg).toFile();
            if (!pkgExport.exists() && !pkgExport.mkdirs()) {
                System.err.println("Unable to create " + pkg + " directory");
                return;
            }
            for (String apk : apks) {
                String pullOutput = commands.pullAPK(apk, pkgExport.toString());
                System.out.println(pullOutput);
                if (pullOutput.startsWith("adb: error:")) {
                    errors++;
                } else {
                    pulled++;
                }
            }
            long now = System.currentTimeMillis();
            System.out.println("Packages exported: " + counter + " | Pulls: " + pulled + " | Errors: " + errors + " | " + (now - st) + " ms elapsed");
            counter++;
        }
        long end = System.currentTimeMillis();
        System.out.println("Time taken: " + (end - st) + " ms");
    }

    private String getAPKName(String apkPath) {
        int appIndex = apkPath.indexOf("app");
        if (apkPath.charAt(appIndex + 4) == '~') {
            // base64 path
            int nameStart = apkPath.indexOf('/', appIndex + 4);
            int nameEnd = apkPath.indexOf('-', nameStart + 2);
            return apkPath.substring(nameStart + 1, nameEnd);
        } else {
            // unobfuscated
            int nameEnd = apkPath.indexOf('/', appIndex + 4);
            return apkPath.substring(appIndex + 4, nameEnd);
        }
    }

    private void debloatUndo(String path) {
        String content = Utilities.readToString(path);
        List<String> packages = Utilities.readAllLines(content);

        int success = 0, fail = 0;
        for (String pkg : packages) {
            System.out.println("Attempting install of: " + pkg);
            String output = commands.installExistingPackage(pkg, 56);
            if (output.contains("Success") || output.startsWith("Package")) {
                success++;
            } else if (output.startsWith("android.content.pm.PackageManager$NameNotFoundException")) {
                fail++;
            } else if (output.isEmpty()) {
                System.err.println("Unauthorized/timed out");
                break;
            } else if (output.startsWith("Error: unknown command") || output.startsWith("/system/bin/sh: cmd: not found")) {
                System.err.println("Install-existing is not a command recognized by Android");
                break;
            } else {
                System.err.println(output);
            }
        }
        System.out.println("Results[success:" + success + ',' + " fail:" + fail + ']');
    }

    private void debloat(boolean full) {
        String output = commands.listPackagesBy(PackageType.ALL);
        optimizePackagesAndPrompt(output, full);

        boolean usePrefix = false;
        System.out.println("Proceed? (y/n)");
        String prefix = scanner.nextLine();
        if (!prefix.startsWith("y")) {
            if (!prefix.startsWith("n")) {
                Utilities.okExit("Exiting");
            }
            System.out.println("Uninstall only those starting with:");
            prefix = scanner.nextLine();
            if (prefix.isEmpty()) {
                Utilities.okExit("Exiting");
            }
            usePrefix = true;
        }

        List<String> uninstalled = new ArrayList<>();
        long start = System.currentTimeMillis();
        int fail = 0;

        for (String currentPackage : bloatedPackages) {
            if (usePrefix && !currentPackage.startsWith(prefix)) {
                continue;
            }

            output = full ? commands.uninstallPackagePerUser(currentPackage) : commands.uninstallPackagePerUserKeepData(currentPackage);
            if (output.startsWith("Success")) {
                System.out.println("Deleted: " + currentPackage);
                uninstalled.add(currentPackage);
                continue;
            } else if (output.startsWith("Error") || output.startsWith("Failure")) {
                fail++;
            }

            if (!output.isEmpty()) {
                System.out.println(output);
            }
        }
        long end = System.currentTimeMillis();
        System.out.println("Completed in: " + (double) (end - start) / 1000 + " seconds");
        System.out.println("Packages uninstalled: " + uninstalled.size());
        System.out.println("Failures: " + fail);

        if (!uninstalled.isEmpty()) {
            long unixSec = System.currentTimeMillis() / 1000;
            Path debloatDump = Paths.get("debloat-" + LocalDate.now() + "-" + unixSec + ".txt");
            byte[] uninstalledAsBytes = String.join("\n", uninstalled).getBytes(StandardCharsets.UTF_8);
            try {
                Files.write(debloatDump, uninstalledAsBytes);
                System.out.println("Dumped uninstalled list to " + debloatDump);
            } catch (IOException e) {
                errorExit(e.getMessage());
            }
        }
        printRestoreCommandInfo();
    }

    private void debloatCust() {
        commands.ensurePrivileged();
        final String CUST_APP = "/cust/app", CUSTPACK_APP = "/custpack/app";
        long custAppSize = commands.getDirectorySize(CUST_APP);
        long custpackAppSize = commands.getDirectorySize(CUSTPACK_APP);
        if (custAppSize == -1 && custpackAppSize == -1) {
            System.out.println("No cust directories found");
            return;
        }

        long custSize;
        String custDir, lsDir;
        if (custAppSize == -1) {
            custDir = CUSTPACK_APP;
            custSize = custpackAppSize;
        } else {
            custDir = CUST_APP;
            custSize = custAppSize;
        }
        lsDir = custDir;
        if (custAppSize > 0) {
            String custCustomizedDir = custDir + "/customized";
            if(commands.exists(custCustomizedDir)) {
                lsDir = custCustomizedDir;
            }
        }
        System.out.println(custDir + " = " + Utilities.formatBtoMB(custSize));
        List<String> appList = commands.listItems(lsDir);
        System.out.println(appList);
        System.out.println(custDir + " will be removed");
        Utilities.askToProceedOrExit(scanner);
        commands.remountReadWrite("/cust");
        String rmRes = commands.rmRecurseForce(custDir);
        System.out.println(rmRes);
        commands.remountReadOnly("/cust");
    }

    private void optimizePackagesAndPrompt(String output, boolean full) {
        boolean errorFallback = false;
        if (output.startsWith("package")) {
            packages = Packages.parseToSet(output);
            System.out.println("Found " + packages.size() + " packages installed on device.");
            // retain these that are installed
            bloatedPackages.retainAll(packages);
        } else if (output.startsWith("java.lang.UnsatisfiedLinkError")) {
            errorFallback = true;
            System.out.println("'pm list packages' command failed");
        } else {
            errorFallback = true;
            System.err.println(output);
        }
        if (errorFallback) {
            System.out.println("Attempt to blind-uninstall " + bloatedPackages.size() + " packages?");
        } else {
            if (bloatedPackages.isEmpty()) {
                System.out.println("No bloated packages found on the device. Exiting ..");
                printRestoreCommandInfo();
                System.exit(0);
            }
            System.out.println(bloatedPackages);
            System.out.println("Uninstall " + (full ? "fully " : "") + bloatedPackages.size() + " packages?");
        }
    }

    public String installAsSystemApp(String apkPath, String appDir) {
        commands.ensurePrivileged();
        String partition = "/";
        long freeSpace = commands.getAvailableSpaceInBytes(partition);
        if (freeSpace != -1) {
            Path path = Paths.get(apkPath);
            long apkSize = -1;
            try {
                apkSize = Files.size(path);
            } catch (IOException e) {
                errorExit(e.toString());
            }
            if (apkSize > freeSpace) {
                errorExit("There's not enough space on " + partition + " to install this apk.\n" +
                        "Available space:  " + Utilities.formatBtoMB(freeSpace) + "\n" +
                        "Application size: " + Utilities.formatBtoMB(apkSize));
            }
        }
        String rwResult = commands.remountReadWrite(partition);
        if (rwResult.startsWith("mount:") || rwResult.contains("is read-only")) {
            System.out.println(rwResult);
            return promptInstallationThroughRecovery(apkPath, appDir);
        }
        String phoneDir = SYSTEM_PRIV_APP + appDir + "/";
        String mkDirResult = commands.mkdir(phoneDir);
        if (mkDirResult.startsWith("mkdir:")) {
            return mkDirResult;
        }

        String apkName = appDir + ".apk";
        String phoneTempPath = STORAGE_EMULATED_0 + apkName;
        String pushResult = commands.push(apkPath, phoneTempPath);
        if (pushResult.startsWith("adb: error:")) {
            return pushResult;
        }

        String phoneDestPath = phoneDir + apkName;
        String moveResult = commands.move(phoneTempPath, phoneDestPath);
        if (moveResult.startsWith("mv:")) {
            return pushResult;
        }

        String chmodDirRes = commands.chmod("755", phoneDir);
        if (chmodDirRes.startsWith("chmod:")) {
            return chmodDirRes;
        }

        String chmodApkRes = commands.chmod("644", phoneDestPath);
        if (chmodApkRes.startsWith("chmod:")) {
            return chmodApkRes;
        }
        String chownRes = commands.changeOwnership("root", "root", phoneDestPath);
        if (chownRes.startsWith("chown:")) {
            return chownRes;
        }

        String roResult = commands.remountReadOnly(partition);
        return rwResult + pushResult + roResult;
    }

    // Alternatively explore using 'dmctl' to set /system or all partitions to rw
    private String installSystemAppInRecovery(String apkPath, String appDir) {
        if (appDir.isEmpty()) {
            errorExit("Provide an installation directory name");
        }
        String systemRoot = "/system_root";
        List<MountEntry> mounts = commands.getSystemProcMounts();
        if (mounts.stream().noneMatch(m -> m.target.equals(systemRoot))) {
            System.out.println(systemRoot  + " isn't mounted, mounting");
            String mountRes = commands.mount("/dev/block/bootdevice/by-name/system", systemRoot);
            if (mountRes.startsWith("mount: ")) {
                return mountRes;
            }
        }

        long freeSpace = commands.getAvailableSpaceInBytes(systemRoot);
        if (freeSpace != -1) {
            Path path = Paths.get(apkPath);
            long apkSize = -1;
            try {
                apkSize = Files.size(path);
            } catch (IOException e) {
                errorExit(e.toString());
            }
            if (apkSize > freeSpace) {
                errorExit("There's not enough space to install this apk.\n" +
                        "Available space:  " + Utilities.formatBtoMB(freeSpace) + "\n" +
                        "Application size: " + Utilities.formatBtoMB(apkSize));
            }
        }

        String phoneDir = systemRoot + SYSTEM_PRIV_APP + appDir + "/";
        String mkDirResult = commands.mkdir(phoneDir);
        if (mkDirResult.startsWith("mkdir:")) {
            return mkDirResult;
        }

        String phoneDestPath = phoneDir + appDir + ".apk";
        String pushResult = commands.push(apkPath, phoneDestPath);
        if (pushResult.startsWith("adb: error:")) {
            return pushResult;
        }

        String chmodRes = commands.chmod("644", phoneDestPath);
        if (chmodRes.startsWith("chmod:")) {
            return chmodRes;
        }
        String chownRes = commands.changeOwnership("root", "root", phoneDestPath);
        if (chownRes.startsWith("chown:")) {
            return chownRes;
        }
        System.out.println("If the device fails to boot, delete the newly created apk. The device will now boot to system.");
        Utilities.askToProceedOrExit(scanner);
        commands.reboot();
        return "";
    }

    private String systemizeApp(String pkgName, String appDir) {
        commands.ensurePrivileged();
        String partition = "/";
        String output = commands.getPackagePath(pkgName);
        if (output.isEmpty()) {
            errorExit(pkgName + " doesn't exist!");
        }
        List<String> apks = ADBCommands.splitOutputLines(output).stream()
                .filter(path -> path.length() > 8)
                .map(path -> path.substring(8)) // trim package: prefix
                .collect(Collectors.toList());
        if (apks.isEmpty()) {
            errorExit("No apks found in the app's installation dir.");
        }

        // Get size of apks only
        /*String userAppDir = Utilities.getParentDirectory((apks.get(0)));
        long freeSpace = commands.getAvailableSpaceInBytes(partition);
        if (freeSpace != -1) {
            long requiredSpace = commands.getDirectorySize(userAppDir);
            if (requiredSpace > freeSpace) {
                errorExit("There's not enough space on " + partition + " to install this app.\n" +
                        "Available space:  " + Utilities.formatBtoMB(freeSpace) + "\n" +
                        "Required space: " + Utilities.formatBtoMB(requiredSpace));
            }
        }*/

        String rwResult = commands.remountReadWrite(partition);
        if (rwResult.startsWith("mount:") || rwResult.contains("is read-only")) {
            return rwResult;
        }

        String phoneDestDir = SYSTEM_PRIV_APP + appDir + "/";
        String mkDirResult = commands.mkdir(phoneDestDir);
        if (mkDirResult.startsWith("mkdir:")) {
            return mkDirResult;
        }
        String chmodDirRes = commands.chmod("755", phoneDestDir);
        if (chmodDirRes.startsWith("chmod:")) {
            return chmodDirRes;
        }

        for (int i = 0; i < apks.size(); i++) {
            String apk = apks.get(i);
            if (apk.isEmpty()) {
                continue;
            }
            String moveResult;
            String destApkPath;
            if (apk.endsWith("base.apk")) {
                destApkPath = phoneDestDir + appDir + ".apk";
                moveResult = commands.copy(apk, destApkPath);
            } else {
                destApkPath = phoneDestDir + apk;
                moveResult = commands.copy(apk, destApkPath);
            }
            if (moveResult.startsWith("mv:")) {
                return moveResult;
            }
            String chmodApkRes = commands.chmod("644", destApkPath);
            if (chmodApkRes.startsWith("chmod:")) {
                return chmodApkRes;
            }
            String chownRes = commands.changeOwnership("root", "root", destApkPath);
            if (chownRes.startsWith("chown:")) {
                return chownRes;
            }
        }
        return commands.remountReadOnly(partition);
    }

    private void printRestoreCommandInfo() {
        System.out.println("If you wish to install back any deleted system packages try running command below:");
        System.out.println(ADBCommands.INSTALL_COMMAND_1 + " or " + ADBCommands.INSTALL_COMMAND_2);
    }

    private String promptInstallationThroughRecovery(String apkPath, String appDir) {
        System.out.println("Unable to remount as read/write.\n" +
                "The installation can be done through recovery that supports ADB (such as TWRP/OrangeFox).\n" +
                "The device will now reboot to recovery.\n");
        Utilities.askToProceedOrExit(scanner);
        commands.rebootRecovery();
        runDevicesStage();
        return installSystemAppInRecovery(apkPath, appDir);
    }

    public static void displayHelp() {
        System.out.println("run.sh/run.bat <action>");
        System.out.println();
        System.out.println("Debloat (packages.txt) (will prompt)");
        System.out.println("  debloat              Uninstalls packages listed in packages.txt");
        System.out.println("  debloat-full         \"debloat\" but also deletes package data");
        System.out.println("  debloat-undo <file>  \"debloat\" but reversed");
        System.out.println("  debloat-cust         [ROOT] remove cust bloatware");
        System.out.println();
        System.out.println("Manage apps:");
        System.out.println("  disable          <name>            Disables package by name");
        System.out.println("  uninstall-keep   <name>            Uninstalls package by name per user (keeps data)");
        System.out.println("  uninstall        <name>            Uninstalls package by name per user");
        System.out.println("  install-back     <name>            Installs an existing sys package by name");
        System.out.println("  install          <path>            Installs app from local path (apk, apkm)");
        System.out.println("  revoke    <perm> <name> [options]  Revokes permission from app (aliases supported)");
        System.out.println("  grant     <perm> <name> [options]  Grants permission to app (aliases supported)");
        System.out.println("[ROOT]:");
        System.out.println("  systemize <package> <app_dir>      Turns existing app into a system app");
        System.out.println("  install-system   <path> <app_dir>  Installs app from local path as system app");
        System.out.println("  degoogle                           Removes stock GMS, PlayStore and GSF (in recovery)");
        System.out.println();
        System.out.println("EXPORT (PHONE -> PC):");
        System.out.println("  export <name> [options]            Exports package by name");
        System.out.println("  export [options]                   Exports many packages");
        System.out.println("  export-data <name> [options]       [ROOT] Export app's data directory");
        System.out.println("  export-data [options]              [ROOT] Export many apps' data directories");
        System.out.println();
        System.out.println("IMPORT (PC -> PHONE):");
        System.out.println("  import <name> [options]            Imports package by name from given directory");
        System.out.println("  import [options]                   Imports all packages from given directory");
        System.out.println("  import-data <name> [options]       [ROOT] Import app's data directory");
        System.out.println("  import-data [options]              [TODO] Import all apps' data");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --type, -pt, --package-type        Package scope: user, system, all");
        System.out.println("  --no-cache, --skip-cache           [TODO] Skip cache during import or export");
        System.out.println("  --force, -f                        Force permission grant or revoke");
        System.out.println("  --dir, -d <dir>                    Directory to export to or import from");
        System.out.println();
        System.out.println("Other commands:");
        System.out.println("  android                            Display Android version");
        System.out.println("  AB-info                            Fetch information related to device A/B partitioning");
        System.out.println("  get-logs                           Dump recent logs to local file - logs.txt");
        System.out.println("  list [options]                     List packages");
        System.out.println("  get-img [img]                      [ROOT] Fetch any image from /dev/block/by-name/ to desktop");
        System.out.println("  get-data-size                      [ROOT] Get /data/user/0/ directory size (apps data dir)");
        System.out.println("  checkSU                            Check super user access (su binary)");
        System.out.println("  adbInstall [on/off]                [ROOT] Enable/Disable app installation via ADB on Xiaomi");
        System.out.println("  adbInput   [on/off]                [ROOT] Enable/Disable input simulation via ADB on Xiaomi");
    }

    public static void errorExit(String message) {
        System.err.println(message);
        System.exit(1);
    }
}
