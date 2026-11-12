package com.ibm.dbsdk.harness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Command-line configuration for the test harness. Credentials may be left unset here; {@link HarnessMain} then fills them from the environment or by prompting.
 *
 * This class deliberately has no {@code toString()}: it holds the Mapepire password.
 */
public final class HarnessConfig {

    public static final int DEFAULT_MAPEPIRE_PORT = 8076;
    public static final int DEFAULT_MOCK_PORT = 8080;
    public static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 120;
    /** Tag for tests that fail until an open toolkit bug is fixed. */
    public static final String TAG_KNOWN_ISSUE = "known-issue";

    private static final Set<String> s_valueOptions = new HashSet<String>(Arrays.asList("-h", "--host", "-P", "--port", "-u", "--user", "--password", "--mock-host", "--mock-port", "--mock-url",
        "--query-timeout", "--skip", "--only", "--report-dir"));

    private boolean m_help = false;
    private String m_host = "localhost";
    private boolean m_insecure = false;
    private boolean m_list = false;
    private String m_mockHost = "localhost";
    private int m_mockPort = DEFAULT_MOCK_PORT;
    private String m_mockUrl = null;
    private final Set<String> m_onlyTags = new LinkedHashSet<String>();
    private String m_password = null;
    private int m_port = DEFAULT_MAPEPIRE_PORT;
    private int m_queryTimeoutSeconds = DEFAULT_QUERY_TIMEOUT_SECONDS;
    private String m_reportDir = null;
    private final Set<String> m_skipTags = new LinkedHashSet<String>();
    private String m_user = null;
    private boolean m_verbose = false;
    private boolean m_version = false;

    /**
     * Parse command-line arguments. Options that take a value accept it either as the next argument or inline ({@code --port=8076}).
     *
     * @param _args
     *            the command-line arguments
     * @return the parsed configuration
     * @throws IllegalArgumentException
     *             if an option is unknown, is missing its value, or has an invalid value
     */
    public static HarnessConfig parse(final String[] _args) {
        final HarnessConfig config = new HarnessConfig();
        int i = 0;
        while (i < _args.length) {
            final String token = _args[i];
            final int eq = token.indexOf('=');
            final boolean inline = token.startsWith("--") && eq > 0;
            final String name = inline ? token.substring(0, eq) : token;
            if ("-?".equals(name) || "--help".equals(name)) {
                config.m_help = true;
            } else if ("--version".equals(name)) {
                config.m_version = true;
            } else if ("--list".equals(name)) {
                config.m_list = true;
            } else if ("--insecure".equals(name)) {
                config.m_insecure = true;
            } else if ("-v".equals(name) || "--verbose".equals(name)) {
                config.m_verbose = true;
            } else if (!s_valueOptions.contains(name)) {
                throw new IllegalArgumentException("Unknown option: " + name);
            } else if (inline) {
                config.applyOption(name, token.substring(eq + 1));
            } else if (i + 1 >= _args.length) {
                throw new IllegalArgumentException("Missing value for option " + name);
            } else {
                i++;
                config.applyOption(name, _args[i]);
            }
            i++;
        }
        return config;
    }

    /**
     * @return the command-line help text
     */
    public static String usage() {
        //@formatter:off
        return String.join(System.lineSeparator(),
            "Usage: java -jar dbsdk-test-harness-<version>-jar-with-dependencies.jar [options]",
            "",
            "Runs the dbsdk_v1 SQL toolkit test suite over the Mapepire JDBC driver. External services",
            "are provided by the dbsdk mock server (see ../mock), which the IBM i must be able to reach.",
            "",
            "Connection options:",
            "  -h, --host <host>          Mapepire server host (default: localhost).",
            "  -P, --port <port>          Mapepire server port (default: " + DEFAULT_MAPEPIRE_PORT + ").",
            "  -u, --user <user>          IBM i user profile. Prompted for if omitted",
            "                             (or set MAPEPIRE_USER).",
            "      --password <password>  Password. Prompted for if omitted (or set MAPEPIRE_PASSWORD).",
            "                             Prefer the prompt or the variable: command-line arguments",
            "                             are visible to other users of the machine.",
            "      --insecure             Do not verify the Mapepire server's TLS certificate",
            "                             (needed for the default self-signed certificate).",
            "      --query-timeout <s>    Per-statement timeout in seconds (default " + DEFAULT_QUERY_TIMEOUT_SECONDS + ").",
            "",
            "Mock server options:",
            "      --mock-host <host>     Mock server host as seen FROM THE IBM i (default: localhost).",
            "      --mock-port <port>     Mock server HTTP port (default: " + DEFAULT_MOCK_PORT + ").",
            "      --mock-url <url>       Mock server URL as seen from this machine, used to inspect",
            "                             what the toolkit sent (default: http://<mock-host>:<mock-port>).",
            "",
            "Test selection:",
            "      --skip <tags>          Skip tests with these tags, e.g. --skip conf,pase,known-issue",
            "      --only <tags>          Run only tests with these tags, e.g. --only openai",
            "      --list                 List the tests that would run, then exit (no connection).",
            "",
            "Output options:",
            "      --report-dir <dir>     Also write JUnit XML reports to this directory.",
            "  -v, --verbose              Show stack traces and extra detail.",
            "      --version              Print the version and exit.",
            "  -?, --help                 Print this help and exit.",
            "",
            "Tags: json, conf, ollama, openai, kafka, slack, twilio, watsonx, pase, known-issue",
            "",
            "Exit status: 0 all tests passed, 1 a test failed, 2 usage or connection error.");
        //@formatter:on
    }

    /**
     * Parse a comma-separated list of tags or suite names, lower-cased.
     *
     * @param _value
     *            the option value
     * @return the non-empty entries
     */
    private static List<String> parseList(final String _value) {
        final List<String> result = new ArrayList<String>();
        for (final String part : _value.split(",")) {
            final String trimmed = part.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * Parse a port number.
     *
     * @param _option
     *            the option name, for error messages
     * @param _value
     *            the option value
     * @return the port
     * @throws IllegalArgumentException
     *             if the value is not a valid port
     */
    private static int parsePort(final String _option, final String _value) {
        final int port = parsePositiveInt(_option, _value);
        if (port > 65535) {
            throw new IllegalArgumentException("Port out of range for " + _option + ": " + _value);
        }
        return port;
    }

    /**
     * Parse a positive integer option value.
     *
     * @param _option
     *            the option name, for error messages
     * @param _value
     *            the option value
     * @return the parsed value
     * @throws IllegalArgumentException
     *             if the value is not a positive integer
     */
    private static int parsePositiveInt(final String _option, final String _value) {
        final int value;
        try {
            value = Integer.parseInt(_value.trim());
        } catch (final NumberFormatException _e) {
            throw new IllegalArgumentException(_option + " requires a positive integer, got: " + _value, _e);
        }
        if (value <= 0) {
            throw new IllegalArgumentException(_option + " requires a positive integer, got: " + _value);
        }
        return value;
    }

    /**
     * Apply one option that takes a value. Error messages never echo the password.
     *
     * @param _name
     *            the option name
     * @param _value
     *            the option value
     * @throws IllegalArgumentException
     *             if the value is invalid
     */
    private void applyOption(final String _name, final String _value) {
        if ("-h".equals(_name) || "--host".equals(_name)) {
            m_host = _value;
        } else if ("-P".equals(_name) || "--port".equals(_name)) {
            m_port = parsePort(_name, _value);
        } else if ("-u".equals(_name) || "--user".equals(_name)) {
            m_user = _value;
        } else if ("--password".equals(_name)) {
            m_password = _value;
        } else if ("--mock-host".equals(_name)) {
            m_mockHost = _value;
        } else if ("--mock-port".equals(_name)) {
            m_mockPort = parsePort(_name, _value);
        } else if ("--mock-url".equals(_name)) {
            m_mockUrl = _value.endsWith("/") ? _value.substring(0, _value.length() - 1) : _value;
        } else if ("--query-timeout".equals(_name)) {
            m_queryTimeoutSeconds = parsePositiveInt(_name, _value);
        } else if ("--skip".equals(_name)) {
            m_skipTags.addAll(parseList(_value));
        } else if ("--only".equals(_name)) {
            m_onlyTags.addAll(parseList(_value));
        } else if ("--report-dir".equals(_name)) {
            m_reportDir = _value;
        } else {
            throw new IllegalArgumentException("Unknown option: " + _name);
        }
    }

    /**
     * @return the tags to exclude, from {@code --skip}; empty means none
     */
    public Set<String> getExcludedTags() {
        return m_skipTags;
    }

    public String getHost() {
        return m_host;
    }

    /**
     * @return the tags to include, from {@code --only}; empty means all tags
     */
    public Set<String> getIncludedTags() {
        return m_onlyTags;
    }

    public String getJdbcUrl() {
        return "jdbc:mapepire://" + m_host + ":" + m_port;
    }

    /**
     * @return the base URL of the mock server as the IBM i sees it, e.g. {@code http://localhost:8080}
     */
    public String getMockBaseUrlForIbmi() {
        return "http://" + m_mockHost + ":" + m_mockPort;
    }

    public String getMockHost() {
        return m_mockHost;
    }

    public int getMockPort() {
        return m_mockPort;
    }

    /**
     * @return the mock server URL as this machine sees it, for inspecting recorded requests
     */
    public String getMockUrl() {
        return null == m_mockUrl ? getMockBaseUrlForIbmi() : m_mockUrl;
    }

    public String getPassword() {
        return m_password;
    }

    public int getPort() {
        return m_port;
    }

    public int getQueryTimeoutSeconds() {
        return m_queryTimeoutSeconds;
    }

    public String getReportDir() {
        return m_reportDir;
    }

    public String getUser() {
        return m_user;
    }

    public boolean isHelp() {
        return m_help;
    }

    public boolean isInsecure() {
        return m_insecure;
    }

    public boolean isList() {
        return m_list;
    }

    public boolean isVerbose() {
        return m_verbose;
    }

    public boolean isVersion() {
        return m_version;
    }

    public HarnessConfig setPassword(final String _password) {
        m_password = _password;
        return this;
    }

    public HarnessConfig setUser(final String _user) {
        m_user = _user;
        return this;
    }
}
