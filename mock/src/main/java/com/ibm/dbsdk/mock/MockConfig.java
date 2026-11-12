package com.ibm.dbsdk.mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Runtime configuration for the mock server. Populated from command-line arguments by {@link #parse(String[])} or built programmatically (e.g. from unit tests).
 */
public final class MockConfig {

    public static final int DEFAULT_PORT = 8080;

    private static final Set<String> s_valueOptions = new HashSet<String>(Arrays.asList("-p", "--port", "--https-port", "-b", "--bind", "--keystore", "--keystore-password", "--watsonx-apikey",
        "--openai-api-key", "--openai-flavor", "--twilio-auth-token", "--token-ttl", "--max-recorded"));

    private String m_bindAddress = "0.0.0.0";
    private boolean m_help = false;
    private final List<Integer> m_httpPorts = new ArrayList<Integer>();
    private final List<Integer> m_httpsPorts = new ArrayList<Integer>();
    private String m_keystorePassword = "changeit";
    private String m_keystorePath = null;
    private int m_maxRecordedRequests = 1000;
    private String m_openAiApiKey = null;
    private OpenAiFlavor m_openAiFlavor = OpenAiFlavor.OPENAI;
    private boolean m_quiet = false;
    private int m_tokenTtlSeconds = 3600;
    private String m_twilioAuthToken = null;
    private boolean m_version = false;
    private String m_watsonxApiKey = null;

    /**
     * Parse command-line arguments. Options that take a value accept it either as the next argument or inline ({@code --port=8080}).
     *
     * @param _args
     *            the command-line arguments
     * @return the parsed configuration
     * @throws IllegalArgumentException
     *             if an option is unknown, is missing its value, or has an invalid value
     */
    public static MockConfig parse(final String[] _args) {
        final MockConfig config = new MockConfig();
        int i = 0;
        while (i < _args.length) {
            final String token = _args[i];
            final int eq = token.indexOf('=');
            final boolean inline = token.startsWith("--") && eq > 0;
            final String name = inline ? token.substring(0, eq) : token;
            if ("-h".equals(name) || "--help".equals(name)) {
                config.m_help = true;
            } else if ("--version".equals(name)) {
                config.m_version = true;
            } else if ("-q".equals(name) || "--quiet".equals(name)) {
                config.m_quiet = true;
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
            "Usage: java -jar dbsdk-mock-server-<version>-jar-with-dependencies.jar [options]",
            "",
            "Mock server for every external service used by the AI-SDK-Db2-IBMi (dbsdk_v1) toolkit.",
            "All services are served from the same listener(s), routed by URL path.",
            "",
            "Listener options:",
            "  -p, --port <p[,p...]>        HTTP port(s) to listen on (default " + DEFAULT_PORT + ").",
            "                               e.g. --port 8080,11434,8000,8082 to match toolkit defaults.",
            "      --https-port <p[,p...]>  HTTPS port(s) to listen on (default: none).",
            "      --keystore <file>        PKCS12/JKS keystore for HTTPS. If omitted while HTTPS is",
            "                               enabled, mock-server.p12 is generated with keytool.",
            "      --keystore-password <pw> Keystore password (default: changeit).",
            "  -b, --bind <address>         Address to bind to (default: 0.0.0.0).",
            "",
            "Credential options (when omitted, any non-empty credential is accepted):",
            "      --watsonx-apikey <key>   The only IBM Cloud API key IAM will accept.",
            "      --openai-api-key <key>   Bearer key required by the OpenAI-compatible endpoints.",
            "      --twilio-auth-token <t>  Twilio auth token required for HTTP Basic auth.",
            "",
            "Behaviour options:",
            "      --openai-flavor <f>      openai (default) or gemini. gemini rejects the legacy",
            "                               /completions endpoint and unknown chat request fields.",
            "      --token-ttl <seconds>    Lifetime of issued watsonx tokens (default 3600).",
            "      --max-recorded <n>       Max requests kept for /mock/requests (default 1000).",
            "  -q, --quiet                  Do not log each request to stdout.",
            "      --version                Print the version and exit.",
            "  -h, --help                   Print this help and exit.",
            "",
            "Prompt directives (embed in any prompt/message text to steer the mock):",
            "  [[mock:status=503]]          Respond with the given HTTP status and a service-style error.",
            "  [[mock:delay=1500]]          Sleep for the given milliseconds before responding.",
            "  [[mock:reply=some text]]     Use this exact text as the generated reply.",
            "  [[mock:malformed]]           Return 200 with a body missing the expected fields.",
            "  [[mock:record-error]]        Kafka only: 200 with an error in offsets[0].",
            "",
            "Inspection endpoints: GET /mock/health, GET|DELETE /mock/requests, GET /mock/kafka,",
            "                      POST /mock/reset");
        //@formatter:on
    }

    /**
     * Parse a comma-separated list of ports.
     *
     * @param _option
     *            the option name, for error messages
     * @param _value
     *            the option value
     * @return the ports
     * @throws IllegalArgumentException
     *             if a port is not a number or is out of range
     */
    private static List<Integer> parsePorts(final String _option, final String _value) {
        final List<Integer> ports = new ArrayList<Integer>();
        for (final String part : _value.split(",")) {
            final String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            final int port;
            try {
                port = Integer.parseInt(p);
            } catch (final NumberFormatException _e) {
                throw new IllegalArgumentException("Invalid port for " + _option + ": " + p, _e);
            }
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException("Port out of range for " + _option + ": " + p);
            }
            ports.add(Integer.valueOf(port));
        }
        return ports;
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

    public MockConfig addHttpPort(final int _port) {
        m_httpPorts.add(Integer.valueOf(_port));
        return this;
    }

    public MockConfig addHttpsPort(final int _port) {
        m_httpsPorts.add(Integer.valueOf(_port));
        return this;
    }

    /**
     * Apply one option that takes a value. Error messages name the option but never echo credential values.
     *
     * @param _name
     *            the option name
     * @param _value
     *            the option value
     * @throws IllegalArgumentException
     *             if the value is invalid
     */
    private void applyOption(final String _name, final String _value) {
        if ("-p".equals(_name) || "--port".equals(_name)) {
            m_httpPorts.addAll(parsePorts(_name, _value));
        } else if ("--https-port".equals(_name)) {
            m_httpsPorts.addAll(parsePorts(_name, _value));
        } else if ("-b".equals(_name) || "--bind".equals(_name)) {
            m_bindAddress = _value;
        } else if ("--keystore".equals(_name)) {
            m_keystorePath = _value;
        } else if ("--keystore-password".equals(_name)) {
            m_keystorePassword = _value;
        } else if ("--watsonx-apikey".equals(_name)) {
            m_watsonxApiKey = _value;
        } else if ("--openai-api-key".equals(_name)) {
            m_openAiApiKey = _value;
        } else if ("--openai-flavor".equals(_name)) {
            m_openAiFlavor = parseFlavor(_value);
        } else if ("--twilio-auth-token".equals(_name)) {
            m_twilioAuthToken = _value;
        } else if ("--token-ttl".equals(_name)) {
            m_tokenTtlSeconds = parsePositiveInt(_name, _value);
        } else if ("--max-recorded".equals(_name)) {
            m_maxRecordedRequests = parsePositiveInt(_name, _value);
        } else {
            throw new IllegalArgumentException("Unknown option: " + _name);
        }
    }

    public String getBindAddress() {
        return m_bindAddress;
    }

    /**
     * @return the HTTP ports; {@link #DEFAULT_PORT} when neither HTTP nor HTTPS ports were configured
     */
    public List<Integer> getHttpPorts() {
        if (m_httpPorts.isEmpty() && m_httpsPorts.isEmpty()) {
            final List<Integer> defaults = new ArrayList<Integer>();
            defaults.add(Integer.valueOf(DEFAULT_PORT));
            return defaults;
        }
        return m_httpPorts;
    }

    public List<Integer> getHttpsPorts() {
        return m_httpsPorts;
    }

    public String getKeystorePassword() {
        return m_keystorePassword;
    }

    public String getKeystorePath() {
        return m_keystorePath;
    }

    public int getMaxRecordedRequests() {
        return m_maxRecordedRequests;
    }

    public String getOpenAiApiKey() {
        return m_openAiApiKey;
    }

    public OpenAiFlavor getOpenAiFlavor() {
        return m_openAiFlavor;
    }

    public int getTokenTtlSeconds() {
        return m_tokenTtlSeconds;
    }

    public String getTwilioAuthToken() {
        return m_twilioAuthToken;
    }

    public String getWatsonxApiKey() {
        return m_watsonxApiKey;
    }

    public boolean isHelp() {
        return m_help;
    }

    public boolean isQuiet() {
        return m_quiet;
    }

    public boolean isVersion() {
        return m_version;
    }

    /**
     * @param _value
     *            the {@code --openai-flavor} value
     * @return the matching flavor
     * @throws IllegalArgumentException
     *             if the value is not a known flavor
     */
    private OpenAiFlavor parseFlavor(final String _value) {
        try {
            return OpenAiFlavor.valueOf(_value.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException _e) {
            throw new IllegalArgumentException("--openai-flavor must be one of: openai, gemini", _e);
        }
    }

    public MockConfig setBindAddress(final String _bindAddress) {
        m_bindAddress = _bindAddress;
        return this;
    }

    public MockConfig setKeystorePassword(final String _keystorePassword) {
        m_keystorePassword = _keystorePassword;
        return this;
    }

    public MockConfig setKeystorePath(final String _keystorePath) {
        m_keystorePath = _keystorePath;
        return this;
    }

    public MockConfig setMaxRecordedRequests(final int _maxRecordedRequests) {
        m_maxRecordedRequests = _maxRecordedRequests;
        return this;
    }

    public MockConfig setOpenAiApiKey(final String _openAiApiKey) {
        m_openAiApiKey = _openAiApiKey;
        return this;
    }

    public MockConfig setOpenAiFlavor(final OpenAiFlavor _openAiFlavor) {
        m_openAiFlavor = _openAiFlavor;
        return this;
    }

    public MockConfig setQuiet(final boolean _quiet) {
        m_quiet = _quiet;
        return this;
    }

    public MockConfig setTokenTtlSeconds(final int _tokenTtlSeconds) {
        m_tokenTtlSeconds = _tokenTtlSeconds;
        return this;
    }

    public MockConfig setTwilioAuthToken(final String _twilioAuthToken) {
        m_twilioAuthToken = _twilioAuthToken;
        return this;
    }

    public MockConfig setWatsonxApiKey(final String _watsonxApiKey) {
        m_watsonxApiKey = _watsonxApiKey;
        return this;
    }

    /** Flavours of OpenAI-compatible endpoint the mock can imitate. */
    public enum OpenAiFlavor {
        /** Google Gemini OpenAI-compatibility layer: no legacy /completions, rejects unknown chat fields. */
        GEMINI,
        /** Standard OpenAI / vLLM / llama.cpp style server: lenient, supports legacy /completions. */
        OPENAI
    }
}
