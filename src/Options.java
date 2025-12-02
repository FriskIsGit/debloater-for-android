
public class Options {
    public PackageType packageType = PackageType.ALL;
    public String dir;
    public boolean skipCache = false;
    public boolean force = false;

    public static Options parseOptions(String[] options, int fromIndex) {
        Options opts = new Options();
        for (int i = fromIndex; i < options.length; i++) {
            switch (options[i]) {
                case "--package-type":
                case "--type":
                case "-pt": {
                    if (++i >= options.length) {
                        Utilities.errExit("No package type passed out of: user, system, all");
                    }
                    opts.packageType = PackageType.from(options[i]);
                    if (opts.packageType == null) {
                        Utilities.errExit("Invalid package type '" + options[i] + "'. None of: user, system, all");
                    }
                } break;

                case "--no-cache":
                case "--skip-cache": {
                    opts.skipCache = true;
                    System.err.println("Unimplemented");
                } break;

                case "--force":
                case "-f": {
                    opts.force = true;
                } break;

                case "--dir":
                case "-d": {
                    if (++i >= options.length) {
                        Utilities.errExit("No path passed for --dir <path>");
                    }
                    opts.dir = options[i];
                } break;
            }
        }
        return opts;
    }
}
