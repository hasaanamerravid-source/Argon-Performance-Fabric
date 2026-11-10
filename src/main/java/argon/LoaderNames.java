package argon;

public final class LoaderNames {
    private static final String NAME = detect();

    private LoaderNames() {
    }

    public static String current() {
        return NAME;
    }

    private static String detect() {
        if (present("org.quiltmc.loader.api.QuiltLoader")
                || present("org.quiltmc.loader.impl.QuiltLoaderImpl")) {
            return "Quilt";
        }
        return "Fabric";
    }

    private static boolean present(String className) {
        try {
            Class.forName(className, false, LoaderNames.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
