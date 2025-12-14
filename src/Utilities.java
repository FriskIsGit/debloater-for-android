import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Utilities {
    public static final int MAX_LEN = 1073741823;

    public static String readFully(InputStream is) throws IOException {
        return read(is, MAX_LEN, false);
    }

    public static String read(InputStream is, int maxLen, boolean throwException) throws IOException {
        if (maxLen < 0) {
            return "";
        }
        byte[] buff = new byte[0];

        int actualRead;
        for (int i = 0; i < maxLen; i += actualRead) {
            int overflow;
            if (i >= buff.length) {
                overflow = Math.min(maxLen - i, buff.length + 1024);
                if (buff.length < i + overflow) {
                    buff = Arrays.copyOf(buff, i + overflow);
                }
            } else {
                overflow = buff.length - i;
            }

            actualRead = is.read(buff, i, overflow);
            if (actualRead < 0) {
                if (throwException && maxLen != 2147483647) {
                    throw new EOFException("Detect premature EOF");
                }

                if (buff.length != i) {
                    buff = Arrays.copyOf(buff, i);
                }
                break;
            }
        }
        return new String(buff);
    }

    public static String convertURLToString(URL url) {
        String str = url.getPath();
        if (str.charAt(0) == '/') {
            str = str.substring(1);
        }
        return str.replace("%20", " ");
    }

    public static String normalizeStringPath(String strPath) {
        strPath = strPath.replace("\\", "/");
        strPath = strPath.replace("\"", "");
        return strPath;
    }

    public static List<String> readAllLines(String str) {
        List<String> list = new ArrayList<>(64);
        Scanner scanner = new Scanner(str);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            list.add(line);
        }
        scanner.close();
        return list;
    }

    public static String readToString(String path) {
        byte[] encoded = new byte[0];
        try {
            encoded = Files.readAllBytes(Paths.get(path));
        } catch (IOException e) {
            errExit(e.getMessage());
        }
        return new String(encoded, StandardCharsets.UTF_8);
    }

    public static void errExit(String message) {
        System.err.println(message);
        System.exit(1);
    }

    public static void okExit(String message) {
        System.out.println(message);
        System.exit(0);
    }

    public static String getExtension(String filename) {
        if (filename == null || filename.length() < 2) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 1) {
            return "";
        }
        int endIndex = filename.length();
        if (filename.endsWith("/")) {
            endIndex = filename.length() - 1;
        }
        return filename.substring(lastDot + 1, endIndex);
    }

    public static List<String> unpackApkm(String apkmPath, String dest) throws IOException {
        Path srcPath = Paths.get(apkmPath);
        Path destPath = Paths.get(dest);
        Files.createDirectories(destPath);
        List<String> unpacked = new ArrayList<>();
        try (ZipInputStream zipStream = new ZipInputStream(new BufferedInputStream(Files.newInputStream(srcPath)))) {
            for (ZipEntry entry; (entry = zipStream.getNextEntry()) != null; ) {
                if (entry.isDirectory()) {
                    continue;
                }
                String fileName = entry.getName();
                String ext = getExtension(fileName);
                if (fileName.contains("/") || !ext.equals("apk")) {
                    continue;
                }
                Path destFile = destPath.resolve(fileName);
                Files.copy(zipStream, destFile, StandardCopyOption.REPLACE_EXISTING);
                unpacked.add(destFile.toString());
            }
        }
        return unpacked;
    }

    public static String[] joinCommand(String[] terms, String... command) {
        String[] joined = new String[terms.length + command.length];
        System.arraycopy(terms, 0, joined, 0, terms.length);
        System.arraycopy(command, 0, joined, terms.length, command.length);
        return joined;
    }

    public static String[] filesToPaths(File[] files) {
        String[] paths = new String[files.length];
        int index = 0;
        for (File apk : files) {
            paths[index] = apk.getPath();
            index++;
        }
        return paths;
    }

    public static void askToProceedOrExit(Scanner scanner) {
        System.out.println("Proceed? (y/n)");
        String input = scanner.nextLine();
        if (input.startsWith("y")) {
            return;
        }
        okExit("Aborting.");
    }

    public static boolean askToDelete(Scanner scanner) {
        System.out.println("Delete? (y/n)");
        String input = scanner.nextLine();
        return input.startsWith("y");
    }

    public static String formatFloat(double value) {
        return String.format("%.1f", value);
    }

    public static String formatBtoMB(long bytes) {
        return formatFloat((double)bytes/(double)(1024*1024)) + " MB";
    }

    public static String getParentDirectory(String path) {
        Path parent = Paths.get(path).getParent();
        if (parent == null) {
            return "";
        }
        return parent.toString().replace("\\", "/");
    }

    public static List<String> splitBy(String line, char target) {
        List<String> components = new ArrayList<>();
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char chr = line.charAt(i);
            if (chr == target) {
                if (content.length() > 0) {
                    components.add(content.toString());
                    content.setLength(0);
                }
            } else {
                content.append(chr);
            }
        }
        if (content.length() > 0) {
            components.add(content.toString());
        }
        return components;
    }
}
