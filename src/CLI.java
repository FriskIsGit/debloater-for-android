
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;

public class CLI {
    private static final String PACKAGES_SRC = "/packages.txt";
    private static final boolean SIMULATE_DEVICE = false;
    private static final String DATA_USER_0 = "/data/user/0/";
    private static final String OUTPUT_TAR = "output.tar";
    private static final String IMPORT_TAR = "import.tar";
    private static final String DATA_EXPORT = "data-export";
    private static final String EXPORT = "export";

    private ADBCommands commands;
    private List<String> bloatedPackages;
    private final Scanner scanner = new Scanner(System.in);
    private Set<String> packages;
    private boolean error_fallback;


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

            // Management
            case "disable": {
                ensureArgument(args, 1, "No package name provided.");
                String pkg = args[1];
                String result = commands.disablePackageByName(pkg);
                System.out.println(result);
            } break;

            case "uninstall": {
                ensureArgument(args, 1, "No package name provided.");
                String pkg = args[1];
                String result = commands.uninstallPackage(pkg);
                System.out.println(result);
            } break;

            case "uninstall-full": {
                ensureArgument(args, 1, "No package name provided.");
                String pkg = args[1];
                String result = commands.uninstallPackageFully(pkg);
                System.out.println(result);
            } break;

            case "install-back": {
                ensureArgument(args, 1, "No package name provided.");
                String pkg = args[1];
                String result = commands.installExistingPackage(pkg);
                System.out.println(result);
            } break;

            case "install": {
                ensureArgument(args, 1, "No path provided.");
                String pkg = args[1];
                String result = commands.install(pkg);
                System.out.println(result);
            } break;

            case "install-system": {
                ensureArgument(args, 1, "No path given. Provide the path to the apk");
                errorExit("Unsafe command");
                String apkPath = args[1];
                String appDirName = args.length > 2 ? args[2] : "";
                String result = commands.installsAsSystemApp(apkPath, appDirName);
                System.out.println(result);
            } break;

            case "uninstall-system": {
                ensureArgument(args, 1, "No path given.");
                // String name = args[1];
                errorExit("Unimplemented");
            } break;

            // Exports
            case "export": {
                ensureArgument(args, 1, "No package name provided");
                String name = args[1];
                String outputDir = args.length > 2 ? args[2] : EXPORT;
                exportByName(name, outputDir);
            } break;

            case "export-user": {
                String outputDir = args.length >= 2 ? args[1] : EXPORT;
                export(PackageType.USER, outputDir);
            } break;

            case "export-system": {
                String outputDir = args.length >= 2 ? args[1] : "export-sys";
                export(PackageType.SYSTEM, outputDir);
            } break;

            case "export-all": {
                String outputDir = args.length >= 2 ? args[1] : "export-all";
                export(PackageType.ALL, outputDir);
            } break;

            case "export-data": {
                ensureArgument(args, 1, "No package name provided");
                String name = args[1];
                String outputDir = args.length > 2 ? args[2] : DATA_EXPORT;
                exportDataByName(name, outputDir);
            } break;

            case "export-data-user": {
                String outputDir = args.length >= 2 ? args[1] : DATA_EXPORT;
                exportAppsData(PackageType.USER, outputDir);
            } break;

            // Imports
            case "import": {
                ensureArgument(args, 1, "No package name provided");
                String name = args[1];
                String outputDir = args.length > 2 ? args[2] : EXPORT;
                importApps(name, outputDir);
            } break;

            case "import-all": {
                String outputDir = args.length >= 2 ? args[1] : EXPORT;
                importApps(null, outputDir);
            } break;

            case "import-data": {
                ensureArgument(args, 1, "No package name provided");
                String name = args[1];
                String importDir = args.length > 2 ? args[2] : DATA_EXPORT;
                importDataByName(name, importDir);
            } break;

            case "import-data-all": {
                String outputDir = args.length >= 2 ? args[1] : DATA_EXPORT;
                importAppsData(outputDir);
            } break;

            case "list-packages": {
                String res = commands.listPackagesWithUID(PackageType.ALL);
                List<App> apps = Packages.parseWithUID(res);
                for (App app : apps) {
                    System.out.println(app);
                }
            } break;

            case "android-version": {
                String version = commands.getAndroidVersion();
                System.out.println("Android " + version);
            } break;

            default:
                errorExit("Unrecognized action command: " + action);
                break;
        }
    }

    private void importDataByName(String pkgName, String importDir) {
        String rootResult = commands.root();
        if (!ADBCommands.hasRoot(rootResult)) {
            errorExit("No adb root!");
            return;
        }
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
        String phoneTar = DATA_USER_0 + IMPORT_TAR;
        String pushResult = commands.push(localTar.toString(), phoneTar);
        System.out.println(pushResult);
        commands.extractTar(phoneTar, DATA_USER_0);
        commands.rm(phoneTar);
        String packagesWithUID = commands.listPackagesWithUID(PackageType.ALL);
        List<App> apps = Packages.parseWithUID(packagesWithUID);
        App targetApp = apps.stream().filter(app -> app.name.equals(pkgName)).findFirst().get();
        System.out.println("Target app: " + targetApp);
        commands.changeOwnership(targetApp.uid, targetApp.uid, DATA_USER_0 + pkgName);
    }

    private void importAppsData(String outputDir) {
        String packagesWithUID = commands.listPackagesWithUID(PackageType.ALL);
        List<App> apps = Packages.parseWithUID(packagesWithUID);
    }

    private void exportDataByName(String pkgName, String outputDir) {
        ensureRootAndDirectory(outputDir);

        String phoneDataDir = DATA_USER_0 + pkgName;
        if (!commands.exists(phoneDataDir)) {
            errorExit(phoneDataDir + " does not exist");
        }

        String appDataTar = DATA_USER_0 + OUTPUT_TAR;
        commands.tar(appDataTar, DATA_USER_0, pkgName);

        String pullOutput = commands.pull(appDataTar, outputDir + "/" + pkgName + ".tar");
        System.out.println(pullOutput);
        String rmResult = commands.rm(appDataTar);
        System.out.println(rmResult);
    }

    private void exportAppsData(PackageType type, String outputDir) {
        ensureRootAndDirectory(outputDir);
        String packagesRes = commands.listPackagesBy(type);
        List<String> packages = Packages.parseToList(packagesRes);
        boolean tarredAny = false;
        for (String pkgName : packages) {
            String phoneDataDir = DATA_USER_0 + pkgName;
            if (!commands.exists(phoneDataDir)) {
                System.err.println(phoneDataDir + " does not exist, skipping");
                continue;
            }

            String appDataTar = DATA_USER_0 + OUTPUT_TAR;
            commands.tar(appDataTar, DATA_USER_0, pkgName);
            tarredAny = true;
            String pullOutput = commands.pull(appDataTar, outputDir + "/" + pkgName + ".tar");
            System.out.println(pullOutput);
        }

        if (tarredAny) {
            String rmResult = commands.rm(DATA_USER_0 + OUTPUT_TAR);
            System.out.println(rmResult);
        }
    }

    private void ensureRootAndDirectory(String outputDir) {
        String rootResult = commands.root();
        if (!ADBCommands.hasRoot(rootResult)) {
            errorExit("No adb root!");
        }
        File dir = new File(outputDir);
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
        String output = commands.listDevices();
        System.out.print(output);
        int devices = devicesConnected(output);
        System.out.println(devices + (devices == 1 ? " connected device" : " connected devices"));
        if (SIMULATE_DEVICE) {
            return;
        }
        if (devices == 0) {
            System.out.println("No devices detected (is 'USB debugging' enabled?), press enter to refresh");
            if (scanner.hasNextLine()) {
                scanner.nextLine();
            }
            runDevicesStage();
        } else if (devices > 1) {
            System.err.println("Error: more than one device/emulator");
        }
    }

    private int devicesConnected(String output) {
        int newLine = output.indexOf('\n');
        if (newLine == -1) {
            return 0;
        }
        int devices = 0;
        for (int i = newLine + 1, len = output.length() - 6; i < len; i++) {
            if (output.startsWith("device", i)) {
                devices++;
            }
            // else "unauthorized" or "authorizing"
        }
        return devices;
    }

    private void importApps(String name, String outputDir) {
        File export = new File(outputDir);
        if (!export.exists()) {
            System.err.println(export.getAbsolutePath() + " not found");
            errorExit("Create an export or put an .apk in " + EXPORT + "/com.app.name/");
            return;
        }
        File[] apkDirs = export.listFiles(File::isDirectory);
        if (apkDirs == null || apkDirs.length == 0) {
            errorExit("There's nothing to install back.");
            return;
        }

        if (name == null) {
            System.out.println("Install possibly " + apkDirs.length + " APKs? (y/n)");
            if (!scanner.nextLine().toLowerCase().startsWith("y")) {
                return;
            }
        }

        for (File apkDir : apkDirs) {
            if (name != null && !apkDir.getName().equals(name)) {
                continue;
            }
            System.out.println("Installing " + apkDir.getName());
            File[] apks = apkDir.listFiles(file -> file.isFile() && file.getName().endsWith(".apk"));
            assert apks != null;
            if (apks.length == 1) {
                String output = commands.install(apks[0].getPath());
                System.out.println(output);
                continue;
            }

            String[] apkPaths = new String[apks.length];
            int index = 0;
            for (File apk : apks) {
                apkPaths[index] = apk.getPath();
                index++;
            }
            String installOutput = commands.installMultiple(apkPaths);
            System.out.println(installOutput);
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
        String[] apks = output.split("\\r?\\n");
        if (apks.length == 0) {
            errorExit("Nothing to export.");
            return;
        }
        for (int i = 0; i < apks.length; i++) {
            // remove package: prefix from apk path
            if (apks[i].length() < 8) {
                continue;
            }
            apks[i] = apks[i].substring(8);
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
        System.out.println("Backing up " + packages.size() + " packages, proceed? (y/n)");
        if (!scanner.nextLine().toLowerCase().startsWith("y")) {
            return;
        }
        int counter = 1;
        long st = System.currentTimeMillis();
        for (String pkg : packages) {
            output = commands.getPackagePath(pkg);
            if (output.isEmpty()) {
                System.err.println(pkg + " is incorrectly displayed by the package manager as an existing package");
                continue;
            }
            List<String> apks = Packages.parseToList(output);
            // System.out.println("PACKAGE PATH PARSED: " + apks);

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
        if (output.startsWith("package")) {
            packages = Packages.parseToSet(output);
            System.out.println("Found " + packages.size() + " packages installed on device.");
            // retain these that are installed
            bloatedPackages.retainAll(packages);
        } else if (output.startsWith("java.lang.UnsatisfiedLinkError")) {
            error_fallback = true;
            System.out.println("'pm list packages' command failed");
        } else {
            error_fallback = true;
            System.err.println(output);
        }
        if (error_fallback) {
            System.out.println("Do you want to try to blind-uninstall " + bloatedPackages.size() + " packages? (y/n)");
        } else {
            if (bloatedPackages.isEmpty()) {
                System.out.println("No bloated packages found on the device. Exiting ..");
                printRestoreCommandInfo();
                return;
            }
            System.out.println(bloatedPackages);
            System.out.println("Uninstall " + (full ? "fully " : "") + bloatedPackages.size() + " packages? (y/n)");
        }

        List<String> uninstalled = new ArrayList<>();
        boolean usePrefix = false;

        String prefix = scanner.nextLine();
        if (!prefix.startsWith("y")) {
            if (!prefix.startsWith("n")) {
                System.out.println("Exiting");
                System.exit(0);
            }
            System.out.println("Uninstall only those starting with:");
            prefix = scanner.nextLine();
            if (prefix.isEmpty()) {
                System.out.println("Exiting");
                System.exit(0);
            }
            usePrefix = true;
        }
        long start = System.currentTimeMillis();
        int fail = 0;

        for (String currentPackage : bloatedPackages) {
            if (usePrefix && !currentPackage.startsWith(prefix)) {
                continue;
            }

            output = full ? commands.uninstallPackageFully(currentPackage) : commands.uninstallPackage(currentPackage);
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

        if (uninstalled.size() > 0) {
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

    private void printRestoreCommandInfo() {
        System.out.println("If you wish to install back any deleted system packages try running command below:");
        System.out.println(ADBCommands.INSTALL_COMMAND_1 + " or " + ADBCommands.INSTALL_COMMAND_2);
    }

    public static void displayHelp() {
        System.out.println("run.sh/run.bat <action>");
        System.out.println();
        System.out.println("Debloat (packages.txt) (no root) (will prompt)");
        System.out.println("  debloat              Uninstalls packages listed in packages.txt");
        System.out.println("  debloat-full         \"debloat\" but also deletes package data");
        System.out.println("  debloat-undo [file]  \"debloat\" but reversed");
        System.out.println();
        System.out.println("Manage apps:");
        System.out.println("  disable          [name]            Disables package by name");
        System.out.println("  uninstall        [name]            Uninstalls package by name");
        System.out.println("  uninstall-full   [name]            Uninstalls package by name (fully)");
        System.out.println("  install-back     [name]            Installs an existing sys package by name");
        System.out.println("  install          [path]            Installs app from local path");
        System.out.println("[ROOT]:");
        System.out.println("  uninstall-system [name]            [TODO] Uninstalls package by name (from system)");
        System.out.println("  install-system   [path] [app_dir]  [BOOTLOOP] Installs app as system app from local path");
        System.out.println();
        System.out.println("Export apps (dir is optional):");
        System.out.println("  export [name] [dir]      Exports package by name");
        System.out.println("  export-user   [dir]      Exports user packages");
        System.out.println("  export-system [dir]      Exports system packages");
        System.out.println("  export-all    [dir]      Exports both user and system packages");
        System.out.println("[ROOT] Export apps data:");
        System.out.println("  export-data [name] [dir]      Export app's data directory");
        System.out.println("  export-data-user   [dir]      Export all user apps' data directories");
        System.out.println();
        System.out.println("Import apps:");
        System.out.println("  import [name] [dir]        Imports package by name from given directory");
        System.out.println("  import-all    [dir]        Imports all packages from given directory");
        System.out.println("[ROOT] Import apps data:");
        System.out.println("  import-data [name] [dir]   Import app's data directory");
        System.out.println("  import-data-all    [dir]   [TODO] Import all apps' data");
        System.out.println();
    }

    public static void errorExit(String message) {
        System.err.println(message);
        System.exit(1);
    }
}
