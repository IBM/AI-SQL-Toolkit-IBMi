package com.ibm.dbsdk.mock;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Shared Jackson helpers. */
public final class Json {

    private static final ObjectMapper s_mapper = new ObjectMapper();

    private Json() {
    }

    /**
     * @return a new, empty JSON array
     */
    public static ArrayNode array() {
        return s_mapper.createArrayNode();
    }

    /**
     * Read a boolean field, accepting JSON booleans as well as the strings "true" and "false".
     *
     * @param _node
     *            the object to read from; may be null
     * @param _field
     *            the field name
     * @return the field's value, or false when it is missing, null or not boolean-like
     */
    public static boolean bool(final JsonNode _node, final String _field) {
        if (null == _node) {
            return false;
        }
        final JsonNode value = _node.get(_field);
        if (null == value || value.isNull()) {
            return false;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        return "true".equalsIgnoreCase(value.asText().trim());
    }

    /**
     * Read an integer field. Accepts JSON numbers and numeric strings, because Db2's {@code JSON_UPDATE} may store numbers either way.
     *
     * @param _node
     *            the object to read from; may be null
     * @param _field
     *            the field name
     * @return the value truncated to an int, or null if missing or not numeric
     */
    public static Integer integer(final JsonNode _node, final String _field) {
        final Double value = number(_node, _field);
        return null == value ? null : Integer.valueOf(value.intValue());
    }

    /**
     * @return the shared, thread-safe object mapper
     */
    public static ObjectMapper mapper() {
        return s_mapper;
    }

    /**
     * Read a numeric field. Accepts JSON numbers and numeric strings. A non-numeric value is logged and treated as absent.
     *
     * @param _node
     *            the object to read from; may be null
     * @param _field
     *            the field name
     * @return the value, or null if missing or not numeric
     */
    public static Double number(final JsonNode _node, final String _field) {
        if (null == _node) {
            return null;
        }
        final JsonNode value = _node.get(_field);
        if (null == value || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return Double.valueOf(value.asDouble());
        }
        if (value.isTextual()) {
            try {
                return Double.valueOf(value.asText().trim());
            } catch (final NumberFormatException _e) {
                MockLog.warn("Ignoring non-numeric value for JSON field '" + _field + "'", _e);
            }
        }
        return null;
    }

    /**
     * @return a new, empty JSON object
     */
    public static ObjectNode object() {
        return s_mapper.createObjectNode();
    }

    /**
     * Parse JSON text. Text that does not look like a JSON object or array is ignored silently, because it is simply a non-JSON body (e.g. a form post). Text that looks like JSON but fails to parse is
     * logged.
     *
     * @param _text
     *            the text to parse; may be null
     * @param _context
     *            a description of where the text came from, for the log; must not contain secrets
     * @return the parsed document, or null
     */
    public static JsonNode parse(final String _text, final String _context) {
        if (null == _text) {
            return null;
        }
        final String trimmed = _text.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return null;
        }
        try {
            return s_mapper.readTree(trimmed);
        } catch (final IOException _e) {
            // Log only the location: the parser message can quote the body, which may contain secrets.
            MockLog.warn("Invalid JSON in " + _context + " (" + _e.getClass().getSimpleName() + ")", null);
            return null;
        }
    }

    /**
     * Read a field as text. Numbers and booleans are returned in their textual form; objects and arrays as serialized JSON.
     *
     * @param _node
     *            the object to read from; may be null
     * @param _field
     *            the field name
     * @return the text, or null if the field is missing or JSON null
     */
    public static String text(final JsonNode _node, final String _field) {
        if (null == _node) {
            return null;
        }
        final JsonNode value = _node.get(_field);
        if (null == value || value.isNull()) {
            return null;
        }
        return value.isValueNode() ? value.asText() : value.toString();
    }
}
