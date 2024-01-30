package debloat;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class Main {
    private static boolean ENSURE_ADB_EXISTS = true;
    private static boolean CACHE_ADB_PATH = true;

    public static void main(String[] args) {
        //no arguments, ask for path
        if (args.length == 0) {
            System.out.println("Detecting ADB..");
            if (detectADBEnv()) {
                CLI.start(ADBCommands.fromEnv());
                return;
            }
            if (detectADBCmdEnv()) {
                CLI.start(ADBCommands.fromCmdEnv());
                return;
            }
            String path = readPathFromCache();
            if (!isValidPath(path)) {
                System.out.println("Provide path to directory where adb is located:");
                path = getValidPath();
            }
            cachePath(path);
            CLI.start(ADBCommands.fromDir(path));
            return;
        }
        //path arg/args provided
        String path = args.length == 1 ? args[0] : Utilities.stringArrayToString(args);
        if (!isValidPath(path)) {
            System.out.println("Provided path is not a directory, provide a valid directory:");
            path = getValidPath();
        }
        cachePath(path);
        CLI.start(ADBCommands.fromDir(path));
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
            if (Files.notExists(cachePath)) {
                Files.createFile(cachePath);
            }
            Files.write(cachePath, path.getBytes(), StandardOpenOption.WRITE);
        } catch (IOException e) {
            return;
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
}
