import java.util.*;

public class Packages {
    private static final int PACKAGE_NAME_OFFSET = 8;

    private Packages() {
    }

    //parsers package names, expected form:
    //"package:com.group.example\n"
    public static Set<String> parse(String output) {
        HashSet<String> packages = new HashSet<>();
        int outputLen = output.length();
        int index = 0, nextNewline;
        String packageName;
        while (index < outputLen - 1) {
            nextNewline = output.indexOf('\n', index + PACKAGE_NAME_OFFSET);
            int decrement = nextNewline;
            //fix made to correctly parse package name, regardless of the number of carriage returns
            while (output.charAt(decrement - 1) == '\r') {
                decrement--;
            }
            packageName = output.substring(index + PACKAGE_NAME_OFFSET, decrement);
            packages.add(packageName);
            index = nextNewline + 1;
        }
        return packages;
    }

    public static List<String> parseToList(String output) {
        List<String> packages = new ArrayList<>();
        int outputLen = output.length();
        for (int i = 0; i < outputLen; i++) {
            int colon = output.indexOf(':', i);
            if (colon == -1) {
                break;
            }
            int packageEnd = colon + 1;
            pkg_name_loop:
            for (;packageEnd < outputLen; packageEnd++) {
                switch (output.charAt(packageEnd)) {
                    case '\r':
                    case '\n':
                        break pkg_name_loop;
                }
            }
            String packageName = output.substring(colon + 1, packageEnd);
            packages.add(packageName);
            i = packageEnd;
        }
        return packages;
    }

    public static List<App> parseWithUID(String output) {
        List<App> apps = new ArrayList<>();
        int outputLen = output.length();
        for (int i = 0; i < outputLen; i++) {
            int colon = output.indexOf(':', i);
            if (colon == -1) {
                break;
            }
            int space = output.indexOf(' ', colon + 1);
            String packageName = output.substring(colon + 1, space);

            int uidColon = output.indexOf(':', space);

            int end = uidColon + 1;
            uid_end_loop:
            for (;end < outputLen; end++) {
                switch (output.charAt(end)) {
                    case '\r':
                    case '\n':
                    case ',':
                        break uid_end_loop;
                }
            }


            String uid = output.substring(uidColon + 1, end);
            App app = new App(packageName, uid);
            apps.add(app);
            i = end + 1;
        }
        return apps;
    }

    public static String sortByGroups(Set<String> packageNames) {
        HashMap<String, List<String>> groupsToPackages = new HashMap<>();
        String groupName = "";
        for (String name : packageNames) {
            int firstIndex = name.indexOf('.');
            if (firstIndex == -1) {
                resolveInMap(groupsToPackages, name, name);
                continue;
            }
            firstIndex++;
            int secondDot = name.indexOf('.', firstIndex);
            if (secondDot == -1) {
                groupName = name.substring(firstIndex);
            } else {
                groupName = name.substring(firstIndex, secondDot);
            }
            resolveInMap(groupsToPackages, groupName, name);
        }
        StringBuilder strBuilder = new StringBuilder(256);
        groupsToPackages.forEach((gName, list) -> {
            strBuilder.append('[').append(gName).append(']').append('\n');
            for (String pckg : list) {
                strBuilder.append(pckg).append('\n');
            }
        });
        strBuilder.append("------------------------------");
        return strBuilder.toString();
    }

    private static void resolveInMap(HashMap<String, List<String>> groupToPackages, String groupName, String fullName) {
        if (groupToPackages.containsKey(groupName)) {
            groupToPackages.get(groupName).add(fullName);
        } else {
            groupToPackages.put(groupName, new ArrayList<>(Arrays.asList(fullName)));
        }
    }
}

