package com.ibm.dbsdk.harness;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Client for the dbsdk mock server's inspection API ({@code /mock/...}). The harness uses it to check what the toolkit actually sent, not only what the SQL function returned.
 */
public final class MockClient {

    private static final int CONNECT_TIMEOUT_MS = 3000;

    private static final int READ_TIMEOUT_MS = 15000;

    private static final ObjectMapper s_mapper = new ObjectMapper();

    private final String m_baseUrl;

    /**
     * @param _baseUrl
     *            the mock server URL as this machine sees it, without a trailing slash
     */
    public MockClient(final String _baseUrl) {
        m_baseUrl = _baseUrl;
    }

    /**
     * @param _text
     *            a form-encoded token
     * @return the decoded token
     */
    private static String decode(final String _text) {
        try {
            return URLDecoder.decode(_text, "UTF-8");
        } catch (final UnsupportedEncodingException _e) {
            throw new IllegalStateException("UTF-8 is always supported", _e);
        }
    }

    /**
     * Decode a recorded {@code application/x-www-form-urlencoded} body the way the receiving service would.
     *
     * @param _request
     *            a recorded request
     * @return the form fields in order
     */
    public static Map<String, String> formFields(final JsonNode _request) {
        final Map<String, String> fields = new LinkedHashMap<String, String>();
        final String body = _request.path("body").asText("");
        for (final String pair : body.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            final int eq = pair.indexOf('=');
            fields.put(decode(eq < 0 ? pair : pair.substring(0, eq)), decode(eq < 0 ? "" : pair.substring(eq + 1)));
        }
        return fields;
    }

    /**
     * Find a header in a recorded request, ignoring case: the mock records header names as the JDK HTTP server normalizes them (e.g. {@code Content-type}).
     *
     * @param _request
     *            a recorded request
     * @param _name
     *            the header name
     * @return the header value, or null if absent
     */
    public static String header(final JsonNode _request, final String _name) {
        final JsonNode headers = _request.path("headers");
        final Iterator<Map.Entry<String, JsonNode>> fields = headers.fields();
        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> field = fields.next();
            if (field.getKey().equalsIgnoreCase(_name)) {
                return field.getValue().asText();
            }
        }
        return null;
    }

    /**
     * Read a response stream fully.
     *
     * @param _in
     *            the stream, or null
     * @return the body as UTF-8 text
     * @throws IOException
     *             if reading fails
     */
    private static String readBody(final InputStream _in) throws IOException {
        if (null == _in) {
            return "";
        }
        try (final InputStream in = _in; final ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            final byte[] buf = new byte[4096];
            int n = in.read(buf);
            while (-1 != n) {
                out.write(buf, 0, n);
                n = in.read(buf);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Check that {@code GET /mock/health} answers with status UP.
     *
     * @return null if the mock is reachable and healthy, otherwise a description of the problem
     */
    public String checkHealth() {
        try {
            final String status = request("GET", "/mock/health").path("status").asText();
            return "UP".equals(status) ? null : "unexpected health status '" + status + "'";
        } catch (final IOException _e) {
            return _e.getClass().getSimpleName() + ": " + _e.getMessage();
        }
    }

    /**
     * @return the base URL this client talks to
     */
    public String getBaseUrl() {
        return m_baseUrl;
    }

    /**
     * @return the Kafka records the mock has stored, keyed by topic
     * @throws IOException
     *             if the mock cannot be reached
     */
    public JsonNode kafkaRecords() throws IOException {
        return request("GET", "/mock/kafka");
    }

    /**
     * Get the most recent request the mock recorded for a service.
     *
     * @param _service
     *            the service name: watsonx, ollama, openai, kafka, slack or twilio
     * @return the recorded request, or null if there is none
     * @throws IOException
     *             if the mock cannot be reached
     */
    public JsonNode lastRequest(final String _service) throws IOException {
        final JsonNode list = request("GET", "/mock/requests?service=" + _service + "&limit=1");
        return 0 == list.size() ? null : list.get(0);
    }

    /**
     * Send a request to the mock and parse the JSON response.
     *
     * @param _method
     *            the HTTP method
     * @param _path
     *            the path and query
     * @return the parsed response body
     * @throws IOException
     *             if the request fails or the mock answers with an error status
     */
    private JsonNode request(final String _method, final String _path) throws IOException {
        final HttpURLConnection c = (HttpURLConnection) new URL(m_baseUrl + _path).openConnection();
        c.setRequestMethod(_method);
        // No connection reuse: HttpURLConnection silently resends a POST when a pooled keep-alive
        // connection turns out to be closed
        c.setRequestProperty("Connection", "close");
        c.setConnectTimeout(CONNECT_TIMEOUT_MS);
        c.setReadTimeout(READ_TIMEOUT_MS);
        if ("POST".equals(_method)) {
            c.setDoOutput(true);
            try (final OutputStream os = c.getOutputStream()) {
                os.write(new byte[0]);
            }
        }
        final int status = c.getResponseCode();
        if (status >= 400) {
            throw new IOException("Mock server answered " + status + " for " + _method + " " + _path + ": " + readBody(c.getErrorStream()));
        }
        return s_mapper.readTree(readBody(c.getInputStream()));
    }

    /**
     * Clear the mock's recorded requests, issued tokens and Kafka topics.
     *
     * @throws IOException
     *             if the mock cannot be reached
     */
    public void reset() throws IOException {
        request("POST", "/mock/reset");
    }
}
