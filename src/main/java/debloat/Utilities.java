package debloat;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

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

    public static String stringArrayToString(String[] arr) {
        StringBuilder pathBuilder = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            pathBuilder.append(arr[i]);
            if (i != arr.length - 1) {
                pathBuilder.append(' ');
            }
        }
        return pathBuilder.toString();
    }
}
