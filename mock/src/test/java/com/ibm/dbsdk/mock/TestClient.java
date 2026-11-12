package com.ibm.dbsdk.mock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

import com.fasterxml.jackson.databind.JsonNode;

/** Minimal Java 8 HTTP client for the tests. */
final class TestClient {

    private final String m_base;
    private SSLContext m_ssl = null;

    TestClient(final String _base) {
        m_base = _base;
    }

    /**
     * @param _user
     *            the user name
     * @param _password
     *            the password
     * @return an HTTP Basic {@code Authorization} header value
     */
    static String basic(final String _user, final String _password) {
        return "Basic " + Base64.getEncoder().encodeToString((_user + ":" + _password).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Turn alternating name/value arguments into a header map.
     *
     * @param _nameValuePairs
     *            header names and values, alternating
     * @return the headers in order
     */
    private static Map<String, String> headers(final String... _nameValuePairs) {
        final Map<String, String> headers = new LinkedHashMap<String, String>();
        for (int i = 0; i + 1 < _nameValuePairs.length; i += 2) {
            headers.put(_nameValuePairs[i], _nameValuePairs[i + 1]);
        }
        return headers;
    }

    /**
     * Read a response stream fully.
     *
     * @param _in
     *            the stream; may be null when the server sent no body
     * @return the body as UTF-8 text
     * @throws IOException
     *             if reading fails
     */
    private static String readBody(final InputStream _in) throws IOException {
        if (null == _in) {
            return "";
        }
        try (final InputStream in = _in) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buf = new byte[4096];
            int n = in.read(buf);
            while (-1 != n) {
                out.write(buf, 0, n);
                n = in.read(buf);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    Response delete(final String _path) throws IOException {
        return request("DELETE", _path, null, null);
    }

    Response get(final String _path) throws IOException {
        return request("GET", _path, null, null);
    }

    /**
     * POST a form-encoded body.
     *
     * @param _path
     *            the request path
     * @param _form
     *            the already-encoded form body
     * @param _headers
     *            extra headers as alternating names and values
     * @return the response
     * @throws IOException
     *             if the request fails
     */
    Response postForm(final String _path, final String _form, final String... _headers) throws IOException {
        final Map<String, String> h = headers(_headers);
        h.put("Content-Type", "application/x-www-form-urlencoded");
        return request("POST", _path, _form, h);
    }

    /**
     * POST a JSON body. Content-Type defaults to {@code application/json} unless given in the headers.
     *
     * @param _path
     *            the request path
     * @param _json
     *            the JSON body
     * @param _headers
     *            extra headers as alternating names and values
     * @return the response
     * @throws IOException
     *             if the request fails
     */
    Response postJson(final String _path, final String _json, final String... _headers) throws IOException {
        final Map<String, String> h = headers(_headers);
        if (!h.containsKey("Content-Type")) {
            h.put("Content-Type", "application/json");
        }
        return request("POST", _path, _json, h);
    }

    /**
     * Send a request and read the whole response, including error bodies.
     *
     * @param _method
     *            the HTTP method
     * @param _path
     *            the request path
     * @param _body
     *            the request body, or null for none
     * @param _headers
     *            request headers, or null
     * @return the response
     * @throws IOException
     *             if the request fails
     */
    Response request(final String _method, final String _path, final String _body, final Map<String, String> _headers) throws IOException {
        final HttpURLConnection c = (HttpURLConnection) new URL(m_base + _path).openConnection();
        if (null != m_ssl && c instanceof HttpsURLConnection) {
            ((HttpsURLConnection) c).setSSLSocketFactory(m_ssl.getSocketFactory());
            ((HttpsURLConnection) c).setHostnameVerifier((_hostname, _session) -> true);
        }
        c.setRequestMethod(_method);
        // No connection reuse: HttpURLConnection silently resends a POST when a pooled keep-alive
        // connection turns out to be closed, which would record the request twice
        c.setRequestProperty("Connection", "close");
        c.setConnectTimeout(5000);
        c.setReadTimeout(20000);
        if (null != _headers) {
            for (final Map.Entry<String, String> e : _headers.entrySet()) {
                c.setRequestProperty(e.getKey(), e.getValue());
            }
        }
        if (null != _body) {
            c.setDoOutput(true);
            try (final OutputStream os = c.getOutputStream()) {
                os.write(_body.getBytes(StandardCharsets.UTF_8));
            }
        }
        final int status = c.getResponseCode();
        final String text = readBody(status >= 400 ? c.getErrorStream() : c.getInputStream());
        return new Response(status, c.getContentType(), text);
    }

    TestClient withSsl(final SSLContext _ssl) {
        m_ssl = _ssl;
        return this;
    }

    /** A fully read HTTP response. */
    static final class Response {

        private final String m_body;
        private final String m_contentType;
        private final int m_status;

        Response(final int _status, final String _contentType, final String _body) {
            m_status = _status;
            m_contentType = _contentType;
            m_body = _body;
        }

        String getBody() {
            return m_body;
        }

        String getContentType() {
            return m_contentType;
        }

        int getStatus() {
            return m_status;
        }

        JsonNode json() throws IOException {
            return Json.mapper().readTree(m_body);
        }

        @Override
        public String toString() {
            return m_status + " " + m_body;
        }
    }
}
