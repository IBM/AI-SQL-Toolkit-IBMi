package com.ibm.dbsdk.harness;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TagFilter;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.platform.reporting.legacy.xml.LegacyXmlReportGeneratingListener;

import com.github.theprez.jcmdutils.AppLogger;
import com.github.theprez.jcmdutils.ConsoleQuestionAsker;

/**
 * Entry point: parses options, resolves credentials, checks connectivity, then runs the toolkit tests through the JUnit Platform Launcher.
 */
public final class HarnessMain {

    public static final int EXIT_FAILURES = 1;

    public static final int EXIT_OK = 0;

    public static final int EXIT_USAGE = 2;

    static final String ENV_PASSWORD = "MAPEPIRE_PASSWORD";

    static final String ENV_USER = "MAPEPIRE_USER";

    static final String TESTS_PACKAGE = "com.ibm.dbsdk.harness.tests";

    private HarnessMain() {
    }

    /**
     * Build the discovery request for the toolkit tests, applying the tag filters from the configuration.
     *
     * @param _config
     *            the run configuration
     * @return the discovery request
     */
    static LauncherDiscoveryRequest buildRequest(final HarnessConfig _config) {
        final LauncherDiscoveryRequestBuilder builder = LauncherDiscoveryRequestBuilder.request().selectors(DiscoverySelectors.selectPackage(TESTS_PACKAGE));
        final Set<String> included = _config.getIncludedTags();
        if (!included.isEmpty()) {
            builder.filters(TagFilter.includeTags(new ArrayList<String>(included)));
        }
        final Set<String> excluded = _config.getExcludedTags();
        if (!excluded.isEmpty()) {
            builder.filters(TagFilter.excludeTags(new ArrayList<String>(excluded)));
        }
        return builder.build();
    }

    /**
     * Ask the IBM i itself to fetch the mock's health endpoint, since it is the IBM i, not this machine, that the toolkit's HTTP calls come from. Problems are reported as warnings.
     *
     * @param _db
     *            an open connection
     * @param _config
     *            the run configuration
     * @param _logger
     *            the console logger
     */
    private static void checkMockFromIbmi(final Db _db, final HarnessConfig _config, final AppLogger _logger) {
        final String url = _config.getMockBaseUrlForIbmi() + "/mock/health";
        try {
            final String health = _db.queryString("values qsys2.http_get(cast(? as varchar(1000) ccsid 1208))", url);
            if (null != health && health.contains("\"UP\"")) {
                _logger.printfln("Mock server reachable from the IBM i at %s", _config.getMockBaseUrlForIbmi());
            } else {
                _logger.printfln_warn("Unexpected mock health response from the IBM i: %s", health);
            }
        } catch (final SQLException _e) {
            _logger.printfln_warn("The IBM i could not reach the mock server at %s: %s", url, _e.getMessage());
            _logger.println_warn("Most toolkit tests will fail. Start the mock on a host the IBM i can reach and set --mock-host/--mock-port.");
        }
    }

    /**
     * Decide whether the user can be prompted. A console object alone is not enough: some JVMs (e.g. IBM's J9 on IBM i) provide one even when standard input is not a terminal, and the
     * prompt library then loops forever on end-of-input. On Unix-like systems, standard input is therefore also checked with {@code test -t 0}.
     *
     * @param _logger
     *            the console logger, for diagnostics
     * @return true if prompting on the console can work
     */
    private static boolean isInteractive(final AppLogger _logger) {
        if (null == System.console()) {
            return false;
        }
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return true;
        }
        try {
            final Process p = new ProcessBuilder("sh", "-c", "test -t 0").redirectInput(ProcessBuilder.Redirect.INHERIT).start();
            return p.waitFor(10, TimeUnit.SECONDS) && 0 == p.exitValue();
        } catch (final IOException _e) {
            _logger.printfln_warn_verbose("Could not check whether standard input is a terminal: %s", _e.getMessage());
            return true;
        } catch (final InterruptedException _e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Print the tests the current selection would run, without connecting anywhere.
     *
     * @param _config
     *            the run configuration
     * @param _logger
     *            the console logger
     * @return the exit status
     */
    private static int listTests(final HarnessConfig _config, final AppLogger _logger) {
        final TestPlan plan = LauncherFactory.create().discover(buildRequest(_config));
        long count = 0;
        for (final TestIdentifier root : plan.getRoots()) {
            for (final TestIdentifier id : plan.getDescendants(root)) {
                if (id.isTest()) {
                    final List<String> tags = new ArrayList<String>();
                    for (final TestTag tag : id.getTags()) {
                        tags.add(tag.getName());
                    }
                    final String parent = id.getParentId().isPresent() ? plan.getTestIdentifier(id.getParentId().get()).getDisplayName() : "";
                    _logger.printfln("%-28s %s  %s", parent, id.getDisplayName(), tags);
                    count++;
                }
            }
        }
        _logger.printfln("%d tests selected", Long.valueOf(count));
        return EXIT_OK;
    }

    /**
     * Command-line entry point.
     *
     * @param _args
     *            the command-line arguments; see {@link HarnessConfig#usage()}
     */
    public static void main(final String[] _args) {
        System.exit(run(_args));
    }

    /**
     * Check the connection, the toolkit installation and the mock server before running any test, so that environment problems are reported once and clearly rather than as many test
     * failures.
     *
     * @param _config
     *            the run configuration, with credentials resolved
     * @param _logger
     *            the console logger
     * @return true if the run can proceed
     */
    private static boolean preflight(final HarnessConfig _config, final AppLogger _logger) {
        _logger.printfln("Connecting to %s as %s", _config.getJdbcUrl(), _config.getUser());
        try (final Db db = Db.open(_config)) {
            _logger.printfln("Connected; job user is %s", db.queryString("values current_user"));
            final String routines = db.queryString("select count(*) from qsys2.sysroutines where routine_schema = 'DBSDK_V1'");
            if (null == routines || 0 == Integer.parseInt(routines.trim())) {
                _logger.println_err("No routines found in schema DBSDK_V1. Build and install the toolkit (see the root makefile) before running the tests.");
                return false;
            }
            _logger.printfln("Found %s routines in DBSDK_V1", routines.trim());
            checkMockFromIbmi(db, _config, _logger);
        } catch (final SQLException _e) {
            _logger.println_err("Could not connect: " + _e.getMessage());
            if (!_config.isInsecure()) {
                _logger.println_warn("If the Mapepire server uses a self-signed certificate, rerun with --insecure.");
            }
            _logger.printExceptionStack_verbose(_e);
            return false;
        }

        final MockClient mock = new MockClient(_config.getMockUrl());
        final String problem = mock.checkHealth();
        if (null == problem) {
            try {
                mock.reset();
                _logger.printfln("Mock server reachable from this machine at %s (state reset)", mock.getBaseUrl());
            } catch (final IOException _e) {
                _logger.printfln_warn("Could not reset the mock server: %s", _e.getMessage());
            }
        } else {
            _logger.printfln_warn("Mock server not reachable from this machine at %s (%s).", mock.getBaseUrl(), problem);
            _logger.println_warn("Tests still run, but checks of what the toolkit sent will be skipped. Use --mock-url to fix.");
        }
        return true;
    }

    /**
     * Fill in the user and password from the environment or, failing that, by prompting on the console. Prompting needs an interactive console; without one the user is told how to pass the
     * values instead.
     *
     * @param _config
     *            the run configuration to complete
     * @param _logger
     *            the console logger
     * @throws IOException
     *             if a value is missing and cannot be prompted for
     */
    static void resolveCredentials(final HarnessConfig _config, final AppLogger _logger) throws IOException {
        final boolean interactive = isInteractive(_logger);
        if (null == _config.getUser()) {
            final String fromEnv = System.getenv(ENV_USER);
            if (null != fromEnv && !fromEnv.trim().isEmpty()) {
                _config.setUser(fromEnv.trim());
            } else if (interactive) {
                _config.setUser(ConsoleQuestionAsker.get().askNonEmptyStringQuestion(_logger, null, "IBM i user for %s:", _config.getHost()).trim());
            } else {
                throw new IOException("No user given and no console to prompt on. Pass --user or set " + ENV_USER + ".");
            }
        }
        if (null == _config.getPassword()) {
            final String fromEnv = System.getenv(ENV_PASSWORD);
            if (null != fromEnv && !fromEnv.isEmpty()) {
                _config.setPassword(fromEnv);
            } else if (interactive) {
                _config.setPassword(ConsoleQuestionAsker.get().askUserForPwd("Password for " + _config.getUser() + "@" + _config.getHost() + ": "));
            } else {
                throw new IOException("No password given and no console to prompt on. Pass --password or set " + ENV_PASSWORD + ".");
            }
        }
    }

    /**
     * Run the harness and return its exit status instead of exiting, so it can be called from tests.
     *
     * @param _args
     *            the command-line arguments
     * @return {@link #EXIT_OK}, {@link #EXIT_FAILURES} or {@link #EXIT_USAGE}
     */
    static int run(final String[] _args) {
        final HarnessConfig config;
        try {
            config = HarnessConfig.parse(_args);
        } catch (final IllegalArgumentException _e) {
            System.err.println("Error: " + _e.getMessage());
            System.err.println();
            System.err.println(HarnessConfig.usage());
            return EXIT_USAGE;
        }
        if (config.isHelp()) {
            System.out.println(HarnessConfig.usage());
            return EXIT_OK;
        }
        if (config.isVersion()) {
            System.out.println("dbsdk-test-harness " + version());
            return EXIT_OK;
        }
        final AppLogger logger = AppLogger.getSingleton(config.isVerbose());
        HarnessContext.setConfig(config);
        if (config.isList()) {
            return listTests(config, logger);
        }
        try {
            resolveCredentials(config, logger);
        } catch (final IOException _e) {
            logger.println_err(_e.getMessage());
            return EXIT_USAGE;
        }
        if (!preflight(config, logger)) {
            return EXIT_USAGE;
        }
        return runTests(config, logger);
    }

    /**
     * Execute the selected tests and print a summary.
     *
     * @param _config
     *            the run configuration
     * @param _logger
     *            the console logger
     * @return {@link #EXIT_OK} if nothing failed, otherwise {@link #EXIT_FAILURES}
     */
    private static int runTests(final HarnessConfig _config, final AppLogger _logger) {
        final Launcher launcher = LauncherFactory.create();
        final SummaryGeneratingListener summaryListener = new SummaryGeneratingListener();
        final List<TestExecutionListener> listeners = new ArrayList<TestExecutionListener>();
        listeners.add(new ConsoleReporter(_logger));
        listeners.add(summaryListener);
        // Wraps System.err, which must stay open, so it is flushed rather than closed
        final PrintWriter reportErrors = new PrintWriter(System.err);
        if (null != _config.getReportDir()) {
            listeners.add(new LegacyXmlReportGeneratingListener(Paths.get(_config.getReportDir()), reportErrors));
        }
        launcher.execute(buildRequest(_config), listeners.toArray(new TestExecutionListener[0]));
        reportErrors.flush();

        final TestExecutionSummary summary = summaryListener.getSummary();
        _logger.println();
        _logger.printfln("Tests: %d found, %d passed, %d failed, %d skipped", Long.valueOf(summary.getTestsFoundCount()), Long.valueOf(summary.getTestsSucceededCount()),
            Long.valueOf(summary.getTotalFailureCount()), Long.valueOf(summary.getTestsAbortedCount() + summary.getTestsSkippedCount()));
        if (null != _config.getReportDir()) {
            _logger.printfln("JUnit XML reports written to %s", Paths.get(_config.getReportDir()).toAbsolutePath());
        }
        if (0 == summary.getTotalFailureCount()) {
            _logger.println_success("All selected tests passed.");
            return EXIT_OK;
        }
        _logger.println_err("Failures:");
        for (final TestExecutionSummary.Failure failure : summary.getFailures()) {
            _logger.println_err("  " + failure.getTestIdentifier().getDisplayName() + ": " + failure.getException().getMessage());
        }
        return EXIT_FAILURES;
    }

    /**
     * @return the implementation version from the jar manifest, or "dev" when run from classes
     */
    static String version() {
        final Package pkg = HarnessMain.class.getPackage();
        final String version = null == pkg ? null : pkg.getImplementationVersion();
        return null == version ? "dev" : version;
    }
}
