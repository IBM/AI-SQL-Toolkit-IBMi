package com.ibm.dbsdk.harness;

/**
 * Hands the run's configuration to the test classes. JUnit instantiates the tests itself, so they cannot receive it through a constructor; {@link HarnessMain} installs it here before
 * launching them.
 */
public final class HarnessContext {

    private static volatile HarnessConfig s_config = null;

    private HarnessContext() {
    }

    /**
     * @return the configuration of the current run
     * @throws IllegalStateException
     *             if the tests were started without going through {@link HarnessMain}
     */
    public static HarnessConfig getConfig() {
        final HarnessConfig config = s_config;
        if (null == config) {
            throw new IllegalStateException("The toolkit tests must be run through HarnessMain (java -jar dbsdk-test-harness-...-jar-with-dependencies.jar)");
        }
        return config;
    }

    static void setConfig(final HarnessConfig _config) {
        s_config = _config;
    }
}
