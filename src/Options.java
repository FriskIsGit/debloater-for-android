public class Options {
    public PackageType packageType = PackageType.ALL;
    public String dir;
    public boolean skipCache = false;

    public static Options parseOptions(String[] options, int fromIndex) {
        Options opts = new Options();
        for (int i = fromIndex; i < options.length; i++) {
            switch (options[i]) {
                case "--user":
                case "-u": {
                    opts.packageType = PackageType.USER;
                } break;

                case "--system":
                case "-s": {
                    opts.packageType = PackageType.SYSTEM;
                } break;

                case "--all":
                case "-a": {
                    opts.packageType = PackageType.ALL;
                } break;

                case "--no-cache":
                case "--skip-cache": {
                    opts.skipCache = true;
                } break;

                case "--dir":
                case "-d": {
                    i++;
                    if (i >= options.length) {
                        Utilities.errExit("No path passed for --dir <path>");
                    }
                    opts.dir = options[i];
                } break;
            }
        }
        return opts;
    }
}
