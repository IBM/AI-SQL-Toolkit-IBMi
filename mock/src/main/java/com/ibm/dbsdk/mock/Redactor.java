package com.ibm.dbsdk.mock;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Masks credentials before request data is logged or recorded. Clients send real-looking secrets to the mock (API keys, auth tokens, webhook secrets), and those must never reach the console or the
 * {@code /mock/requests} history.
 */
final class Redactor {

    static final String MASK = "****";
    private static final String SLACK_PREFIX = "/services/";

    private static final Set<String> s_secretFields = new HashSet<String>(Arrays.asList("access_token", "refresh_token", "apikey", "api_key", "client_secret", "password", "authtoken", "token"));
    private static final Set<String> s_secretHeaders = new HashSet<String>(Arrays.asList("authorization", "proxy-authorization", "x-api-key", "api-key"));

    private Redactor() {
    }

    /**
     * Mask the values of secret-bearing fields in an application/x-www-form-urlencoded body. Other fields are left exactly as sent.
     *
     * @param _body
     *            the raw form body
     * @return the body with secret values replaced by {@link #MASK}
     */
    static String redactFormBody(final String _body) {
        if (null == _body || _body.isEmpty()) {
            return _body;
        }
        final StringBuilder out = new StringBuilder();
        for (final String pair : _body.split("&", -1)) {
            if (0 < out.length()) {
                out.append('&');
            }
            final int eq = pair.indexOf('=');
            final String key = eq < 0 ? pair : pair.substring(0, eq);
            if (0 <= eq && s_secretFields.contains(key.toLowerCase(Locale.ROOT))) {
                out.append(key).append('=').append(MASK);
            } else {
                out.append(pair);
            }
        }
        return out.toString();
    }

    /**
     * Mask a header value if the header carries credentials. For {@code Authorization}-style headers the scheme (e.g. {@code Bearer}) is kept so the recording still shows how the client authenticated.
     *
     * @param _name
     *            the header name
     * @param _value
     *            the header value
     * @return the value, masked if the header is secret
     */
    static String redactHeader(final String _name, final String _value) {
        if (null == _name || null == _value || !s_secretHeaders.contains(_name.toLowerCase(Locale.ROOT))) {
            return _value;
        }
        final String trimmed = _value.trim();
        final int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) + " " + MASK : MASK;
    }

    /**
     * Return a copy of a JSON document with every secret-bearing field masked, at any depth.
     *
     * @param _node
     *            the document; may be null
     * @return a redacted deep copy, or null if the input was null
     */
    static JsonNode redactJson(final JsonNode _node) {
        if (null == _node) {
            return null;
        }
        final JsonNode copy = _node.deepCopy();
        maskInPlace(copy);
        return copy;
    }

    /**
     * Redact a JSON document held as text. Fails closed: if the text cannot be parsed it is replaced entirely by {@link #MASK}.
     *
     * @param _text
     *            JSON text produced by the mock
     * @return the redacted JSON text
     */
    static String redactJsonText(final String _text) {
        if (null == _text || _text.isEmpty()) {
            return _text;
        }
        try {
            return Json.mapper().writeValueAsString(redactJson(Json.mapper().readTree(_text)));
        } catch (final IOException _e) {
            MockLog.warn("Could not parse JSON for redaction; recording it masked", _e);
            return MASK;
        }
    }

    /**
     * Mask the secret segment of a Slack webhook path ({@code /services/T.../B.../<secret>}). Any query string is preserved. Other paths are returned unchanged.
     *
     * @param _pathOrUri
     *            a request path or path-plus-query
     * @return the path with webhook secrets masked
     */
    static String redactPath(final String _pathOrUri) {
        if (null == _pathOrUri || !_pathOrUri.startsWith(SLACK_PREFIX)) {
            return _pathOrUri;
        }
        final int queryStart = _pathOrUri.indexOf('?');
        final String path = queryStart < 0 ? _pathOrUri : _pathOrUri.substring(0, queryStart);
        final String query = queryStart < 0 ? "" : _pathOrUri.substring(queryStart);
        final int lastSlash = path.lastIndexOf('/');
        if (lastSlash <= SLACK_PREFIX.length() - 1 || lastSlash == path.length() - 1) {
            return _pathOrUri;
        }
        return path.substring(0, lastSlash + 1) + MASK + query;
    }

    /**
     * Recursively mask secret fields of an object or array node in place.
     *
     * @param _node
     *            the node to modify
     */
    private static void maskInPlace(final JsonNode _node) {
        if (_node.isObject()) {
            final ObjectNode obj = (ObjectNode) _node;
            final Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
            while (fields.hasNext()) {
                final Map.Entry<String, JsonNode> field = fields.next();
                if (s_secretFields.contains(field.getKey().toLowerCase(Locale.ROOT)) && field.getValue().isValueNode() && !field.getValue().isNull()) {
                    field.setValue(obj.textNode(MASK));
                } else {
                    maskInPlace(field.getValue());
                }
            }
        } else if (_node.isArray()) {
            for (final JsonNode child : _node) {
                maskInPlace(child);
            }
        }
    }
}
