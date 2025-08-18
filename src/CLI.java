import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

public class CLI {
    private static final String PACKAGES_SRC = "/packages.txt";
    private static final boolean SIMULATE_DEVICE = false;

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
                System.err.println("Couldn't find/load: " + PACKAGES_SRC);
                System.exit(1);
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
            System.err.println("Error reading packages with file.. exiting");
            System.exit(1);
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

            case "disable": {
                ensureArgument(args, 1, "No package name provided.");
                String pkg = args[1];
                String result = commands.disablePackageByName(pkg);
                System.out.println(result);
            } break;

            case "install-back": {
                ensureArgument(args, 1, "No package name provided.");
                String pkg = args[1];
                String result = commands.installExistingPackage(pkg);
                System.out.println(result);
            } break;

            // Exports
            case "export-user": {
                String outputDir = args.length >= 2 ? args[1] : "export";
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

            case "export": {
                ensureArgument(args, 1, "No package name provided");
                String name = args[1];
                String outputDir = args.length > 2 ? args[2] : "export";
                exportByName(name, outputDir);
            } break;

            // Imports
            case "import": {
                ensureArgument(args, 1, "No package name provided");
                String name = args[1];
                String outputDir = args.length > 2 ? args[2] : "export";
                importApps(name, outputDir);
            } break;

            case "import-all": {
                String outputDir = args.length >= 2 ? args[1] : "export";
                importApps(null, outputDir);
            } break;

            default:
                System.out.println("Unrecognized action command: " + action);
                break;
        }
    }

    private void ensureArgument(String[] args, int index, String errorMessage) {
        if (index < args.length) {
            return;
        }
        System.err.println(errorMessage);
        System.exit(1);
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
            System.out.println(export.getAbsolutePath() + " not found");
            System.err.println("Create an export or put an .apk in export/com.app.name/");
            return;
        }
        File[] apkDirs = export.listFiles(File::isDirectory);
        if (apkDirs == null || apkDirs.length == 0) {
            System.err.println("There's nothing to install back.");
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
            System.err.println("Unable to create export directory");
            return;
        }
        System.out.println("Provide package name to export: ");
        String output = commands.getPackagePath(pkgName);
        if (output.isEmpty()) {
            System.err.println(pkgName + " doesn't exist?");
            return;
        }
        String[] apks = output.split("\\r?\\n");
        if (apks.length == 0) {
            System.err.println("Nothing to export.");
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
            System.err.println("Unable to create " + pkgName + " directory");
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
            packages = Packages.parse(output);
        } else if (output.startsWith("java.lang.UnsatisfiedLinkError")) {
            System.err.println("'pm list packages' command failed - can't export");
            return;
        } else {
            System.err.println(output);
            return;
        }
        int pulled = 0, errors = 0;
        File export = new File(outputDir);
        if (!export.exists() && !export.mkdirs()) {
            System.err.println("Unable to create export directory");
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
        String output = commands.listPackages();
        if (output.startsWith("package")) {
            packages = Packages.parse(output);
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
                System.err.println(e.getMessage());
            }
        }
        printRestoreCommandInfo();
    }

    private void printRestoreCommandInfo() {
        System.out.println("If you wish to install back any deleted system packages try running command below:");
        System.out.println(ADBCommands.INSTALL_COMMAND_1 + " or " + ADBCommands.INSTALL_COMMAND_2);
    }
}
