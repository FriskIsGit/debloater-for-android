import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class Main {
    private static boolean ENSURE_ADB_EXISTS = true;
    private static boolean CACHE_ADB_PATH = true;

    public static void main(String[] args) {
        // no arguments, ask for path
        if (args.length == 0) {
            displayHelp();
            return;
        }

        System.out.println("Detecting ADB..");
        if (detectADBEnv()) {
            CLI.start(ADBCommands.fromEnv(), args);
            return;
        }
        if (detectADBCmdEnv()) {
            CLI.start(ADBCommands.fromCmdEnv(), args);
            return;
        }
        String path = readPathFromCache();
        if (!isValidPath(path)) {
            System.out.println("ADB wasn't found in any environment. Provide path to directory where adb is located");
            path = getValidPath();
        }
        cachePath(path);
        CLI.start(ADBCommands.fromDir(path), args);
    }

    private static boolean detectADBEnv() {
        ProcessBuilder procBuilder = new ProcessBuilder();
        procBuilder.command("adb");
        try {
            Process proc = procBuilder.start();
            proc.waitFor(10, TimeUnit.MILLISECONDS);
            String output = Utilities.read(proc.getInputStream(), 36, false);
            return output.startsWith("Android Debug Bridge");
        } catch (IOException | InterruptedException exceptions) {
            // adb is not in path or adb as env var is only visible to cmd
            return false;
        }
    }

    private static boolean detectADBCmdEnv() {
        if (!System.getProperty("os.name").toLowerCase().startsWith("windows")) { // check on macOS?
            return false;
        }
        ProcessBuilder procBuilder = new ProcessBuilder();
        procBuilder.command("cmd", "/C", "adb");
        try {
            Process proc = procBuilder.start();
            proc.waitFor(10, TimeUnit.MILLISECONDS);
            String output = Utilities.read(proc.getInputStream(), 36, false);
            return output.startsWith("Android Debug Bridge");
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void cachePath(String path) {
        if (!CACHE_ADB_PATH) {
            return;
        }
        URL url = Main.class.getProtectionDomain().getCodeSource().getLocation();
        String executingPath = Utilities.convertURLToString(url);
        int lastSlash = executingPath.lastIndexOf('/');
        Path cachePath = Paths.get(executingPath.substring(0, lastSlash) + "/cache.txt");

        try {
            Files.write(cachePath, path.getBytes());
        } catch (IOException e) {
            System.err.println("Failed to cache path: " + e.getMessage());
        }
    }

    private static String readPathFromCache() {
        if (!CACHE_ADB_PATH) {
            return null;
        }
        URL url = Main.class.getProtectionDomain().getCodeSource().getLocation();
        String executingPath = Utilities.convertURLToString(url);
        int lastSlash = executingPath.lastIndexOf('/');
        executingPath = executingPath.substring(0, lastSlash);
        Path cachePath = Paths.get(executingPath + "/cache.txt");
        if (!Files.exists(cachePath)) {
            return null;
        }
        byte[] pathBytes;
        try {
            pathBytes = Files.readAllBytes(cachePath);
        } catch (IOException e) {
            return null;
        }
        return new String(pathBytes);
    }

    private static String getValidPath() {
        Scanner scan = new Scanner(System.in);
        String line;
        while (true) {
            line = scan.nextLine();
            if (isValidPath(line)) {
                break;
            }
        }
        return line;
    }

    private static boolean isValidPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        path = Utilities.normalizeStringPath(path);
        File dir = new File(path);
        if (!dir.exists()) {
            System.out.println("Provided path is invalid, provide a valid directory:");
            return false;
        }
        Path p = dir.toPath();
        System.out.println(p);
        if (!Files.isDirectory(p)) {
            System.out.println("Provided path is not a directory, provide a valid directory:");
            return false;
        }
        if (ENSURE_ADB_EXISTS) {
            if (hasAdb(p)) {
                System.out.println("Adb found, proceeding..");
                return true;
            } else {
                System.out.println("Adb couldn't be located in given directory");
                return false;
            }
        }
        return true;
    }

    private static boolean hasAdb(Path dirPath) {
        File[] entries = dirPath.toFile().listFiles();
        for (File f : entries) {
            if (f.isFile() && f.getName().startsWith("adb")) {
                return true;
            }
        }
        return false;
    }

    private static void displayHelp() {
        System.out.println("run.sh/run.bat <action>");
        System.out.println();
        System.out.println("Debloat (packages.txt) (no root) (will prompt)");
        System.out.println("  debloat              Uninstalls packages listed in packages.txt");
        System.out.println("  debloat-full         \"debloat\" but also deletes package data");
        System.out.println("  debloat-undo [file]  \"debloat\" but reversed");
        System.out.println();
        System.out.println("App management:");
        System.out.println("  uninstall    [name]      Uninstalls package by name (no root)");
        System.out.println("  disable      [name]      Disables package by name (no root)");
        System.out.println("  install-back [name]      Installs an existing package by name (no root)");
        System.out.println();
        System.out.println("App exports (no root) (dir is optional):");
        System.out.println("  export [name] [dir]      Exports package by name");
        System.out.println("  export-user   [dir]      Exports user packages");
        System.out.println("  export-system [dir]      Exports system packages");
        System.out.println();
        System.out.println("App imports (no root) (dir is optional):");
        System.out.println("  import [name] [dir]      Imports package by name from given directory");
        System.out.println("  import-all    [dir]      Imports all packages from given directory");
        System.out.println();
        System.out.println("App installs:");
        System.out.println("  install        [path]    Installs app from local path");
        System.out.println("  install-system [path]    Installs app as system app from local path (root)");
        System.out.println();
        System.out.println("Setup");
        System.out.println("  check-adb                Check if adb is available and device is detected");
        System.out.println("  set-adb [path]           Path to where adb is installed, in case it's not in PATH");
    }
}
