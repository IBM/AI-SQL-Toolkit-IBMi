package com.ibm.dbsdk.mock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;

/**
 * Wraps a single {@link HttpExchange}: gives handlers convenient access to the request and captures the response so it can be recorded for later inspection.
 */
public final class HttpCall {

    private final byte[] m_body;
    private final HttpExchange m_exchange;
    private final JsonNode m_json;
    private final String m_method;
    private final String m_path;
    private final Map<String, String> m_query;
    private boolean m_responded = false;
    private ResponseListener m_responseListener = null;
    private String m_responseBody = null;
    private String m_responseContentType = null;
    private int m_responseStatus = 0;
    private String m_serviceName = "unknown";

    /**
     * Read the request line, headers and body of an exchange.
     *
     * @param _exchange
     *            the exchange to wrap
     * @throws IOException
     *             if the request body cannot be read
     */
    HttpCall(final HttpExchange _exchange) throws IOException {
        m_exchange = _exchange;
        m_method = _exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        final String rawPath = _exchange.getRequestURI().getRawPath();
        m_path = normalize((null == rawPath || rawPath.isEmpty()) ? "/" : decodePath(rawPath));
        m_query = parseUrlEncoded(_exchange.getRequestURI().getRawQuery());
        m_body = readAll(_exchange.getRequestBody());
        m_json = Json.parse(new String(m_body, StandardCharsets.UTF_8), "request body of " + m_method + " " + Redactor.redactPath(m_path));
    }

    /**
     * Collapse duplicate slashes and strip a trailing slash, so routing does not depend on how a client concatenated its base URL and path.
     *
     * @param _path
     *            a decoded path
     * @return the normalized path, never empty
     */
    static String normalize(final String _path) {
        final String collapsed = _path.replaceAll("/{2,}", "/");
        if (collapsed.length() > 1 && collapsed.endsWith("/")) {
            return collapsed.substring(0, collapsed.length() - 1);
        }
        return collapsed;
    }

    /**
     * Parse {@code application/x-www-form-urlencoded} text (a form body or a query string).
     *
     * @param _raw
     *            the encoded text; may be null
     * @return the decoded fields in their original order
     */
    static Map<String, String> parseUrlEncoded(final String _raw) {
        final Map<String, String> result = new LinkedHashMap<String, String>();
        if (null == _raw || _raw.isEmpty()) {
            return result;
        }
        for (final String pair : _raw.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            final int eq = pair.indexOf('=');
            final String key = eq < 0 ? pair : pair.substring(0, eq);
            final String value = eq < 0 ? "" : pair.substring(eq + 1);
            result.put(decodeForm(key), decodeForm(value));
        }
        return result;
    }

    /**
     * Decode one form-encoded token. A malformed percent escape is logged and the raw text kept, rather than failing the whole request.
     *
     * @param _text
     *            the encoded token
     * @return the decoded token
     */
    private static String decodeForm(final String _text) {
        try {
            return URLDecoder.decode(_text, "UTF-8");
        } catch (final UnsupportedEncodingException _e) {
            throw new IllegalStateException("UTF-8 is always supported", _e);
        } catch (final IllegalArgumentException _e) {
            MockLog.warn("Keeping malformed percent-encoding undecoded", _e);
            return _text;
        }
    }

    /**
     * @param _rawPath
     *            the raw request path
     * @return the decoded path; unlike form decoding, '+' stays a literal plus sign
     */
    private static String decodePath(final String _rawPath) {
        return decodeForm(_rawPath.replace("+", "%2B"));
    }

    /**
     * Read a stream fully. The stream is owned by the {@link HttpExchange} and is not closed here; it is released when the exchange is closed after the response is sent.
     *
     * @param _in
     *            the stream to read
     * @return all bytes read
     * @throws IOException
     *             if reading fails
     */
    private static byte[] readAll(final InputStream _in) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buf = new byte[8192];
        int n = _in.read(buf);
        while (-1 != n) {
            out.write(buf, 0, n);
            n = _in.read(buf);
        }
        return out.toByteArray();
    }

    /**
     * Get the user and password from an {@code Authorization: Basic ...} header.
     *
     * @return a two-element array of user and password, or null if the header is absent or cannot be decoded
     */
    public String[] getBasicCredentials() {
        final String auth = getHeader("Authorization");
        if (null == auth) {
            return null;
        }
        final String trimmed = auth.trim();
        if (!trimmed.regionMatches(true, 0, "Basic ", 0, 6)) {
            return null;
        }
        final String decoded;
        try {
            decoded = new String(Base64.getMimeDecoder().decode(trimmed.substring(6).trim()), StandardCharsets.UTF_8);
        } catch (final IllegalArgumentException _e) {
            MockLog.warn("Ignoring undecodable Basic authorization header", null);
            return null;
        }
        final int colon = decoded.indexOf(':');
        if (colon < 0) {
            return new String[] { decoded, "" };
        }
        return new String[] { decoded.substring(0, colon), decoded.substring(colon + 1) };
    }

    /**
     * Get the token from an {@code Authorization: Bearer ...} header.
     *
     * @return the token, or null if there is no Bearer authorization header
     */
    public String getBearerToken() {
        final String auth = getHeader("Authorization");
        if (null == auth) {
            return null;
        }
        final String trimmed = auth.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return null;
    }

    public String getBodyText() {
        return new String(m_body, StandardCharsets.UTF_8);
    }

    /**
     * @return the body parsed as {@code application/x-www-form-urlencoded}
     */
    public Map<String, String> getForm() {
        return parseUrlEncoded(getBodyText());
    }

    public String getHeader(final String _name) {
        return m_exchange.getRequestHeaders().getFirst(_name);
    }

    public Map<String, List<String>> getHeaders() {
        return m_exchange.getRequestHeaders();
    }

    /**
     * @return the body parsed as JSON, or null if it is empty or not valid JSON
     */
    public JsonNode getJson() {
        return m_json;
    }

    public String getMethod() {
        return m_method;
    }

    /**
     * @return the decoded request path with duplicate and trailing slashes removed; never empty
     */
    public String getPath() {
        return m_path;
    }

    public Map<String, String> getQuery() {
        return m_query;
    }

    public String getRawUri() {
        return m_exchange.getRequestURI().toString();
    }

    /**
     * @return the client's address, or "?" if unknown
     */
    public String getRemoteAddress() {
        return null == m_exchange.getRemoteAddress() ? "?" : m_exchange.getRemoteAddress().toString();
    }

    public String getResponseBody() {
        return m_responseBody;
    }

    public String getResponseContentType() {
        return m_responseContentType;
    }

    public int getResponseStatus() {
        return m_responseStatus;
    }

    public String getServiceName() {
        return m_serviceName;
    }

    public boolean hasResponded() {
        return m_responded;
    }

    /**
     * Send a complete response and close the exchange.
     *
     * @param _status
     *            the HTTP status code
     * @param _contentType
     *            the Content-Type header value
     * @param _text
     *            the response body
     * @throws IOException
     *             if the response cannot be written
     */
    public void send(final int _status, final String _contentType, final String _text) throws IOException {
        final byte[] bytes = _text.getBytes(StandardCharsets.UTF_8);
        markResponded(_status, _contentType, _text);
        if ("HEAD".equals(m_method) || 0 == bytes.length) {
            m_exchange.sendResponseHeaders(_status, -1);
            m_exchange.close();
            return;
        }
        m_exchange.sendResponseHeaders(_status, bytes.length);
        try (final OutputStream os = m_exchange.getResponseBody()) {
            os.write(bytes);
        }
        m_exchange.close();
    }

    /**
     * Send a JSON response with Content-Type {@code application/json}.
     *
     * @param _status
     *            the HTTP status code
     * @param _json
     *            the response document
     * @throws IOException
     *             if the response cannot be written
     */
    public void sendJson(final int _status, final JsonNode _json) throws IOException {
        sendJson(_status, _json, "application/json");
    }

    /**
     * Send a JSON response with a specific Content-Type.
     *
     * @param _status
     *            the HTTP status code
     * @param _json
     *            the response document
     * @param _contentType
     *            the Content-Type header value
     * @throws IOException
     *             if the response cannot be written
     */
    public void sendJson(final int _status, final JsonNode _json, final String _contentType) throws IOException {
        send(_status, _contentType, Json.mapper().writeValueAsString(_json));
    }

    /**
     * Send a plain-text response.
     *
     * @param _status
     *            the HTTP status code
     * @param _text
     *            the response body
     * @throws IOException
     *             if the response cannot be written
     */
    public void sendText(final int _status, final String _text) throws IOException {
        send(_status, "text/plain; charset=utf-8", _text);
    }

    public void setServiceName(final String _serviceName) {
        m_serviceName = _serviceName;
    }

    /**
     * Start a chunked streaming response (used for Ollama NDJSON and OpenAI server-sent events). The caller owns the returned stream and must close it, which also completes the exchange.
     *
     * @param _status
     *            the HTTP status code
     * @param _contentType
     *            the Content-Type header value
     * @return the response body stream
     * @throws IOException
     *             if the response headers cannot be sent
     */
    public OutputStream startStream(final int _status, final String _contentType) throws IOException {
        markResponded(_status, _contentType, "<streamed>");
        m_exchange.sendResponseHeaders(_status, 0);
        return m_exchange.getResponseBody();
    }

    /**
     * Remember the response for recording, set the common response headers, and notify the response listener.
     *
     * @param _status
     *            the HTTP status code
     * @param _contentType
     *            the Content-Type header value
     * @param _body
     *            the body as recorded
     */
    private void markResponded(final int _status, final String _contentType, final String _body) {
        m_responded = true;
        m_responseStatus = _status;
        m_responseContentType = _contentType;
        m_responseBody = _body;
        m_exchange.getResponseHeaders().set("Content-Type", _contentType);
        m_exchange.getResponseHeaders().set("X-Mock-Server", "dbsdk-mock-server");
        if (null != m_responseListener) {
            m_responseListener.beforeResponse(this);
        }
    }

    /**
     * Register a listener that runs when the response is complete but before any of it is sent. The router uses this to record the request first, so a client that has received a response can
     * always find the request in {@code /mock/requests}.
     *
     * @param _listener
     *            the listener, or null for none
     */
    void setResponseListener(final ResponseListener _listener) {
        m_responseListener = _listener;
    }

    /** Called when a response is ready to be sent. */
    interface ResponseListener {

        /**
         * @param _call
         *            the call, with its response status, content type and body already set
         */
        void beforeResponse(HttpCall _call);
    }
}
