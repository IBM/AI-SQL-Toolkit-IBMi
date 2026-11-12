package com.ibm.dbsdk.mock.handlers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ibm.dbsdk.mock.Directives;
import com.ibm.dbsdk.mock.HttpCall;
import com.ibm.dbsdk.mock.Json;
import com.ibm.dbsdk.mock.MockState;

/**
 * Emulates the Confluent Kafka REST Proxy (v2) produce API used by {@code kafka_publish}:
 * <ul>
 * <li>{@code POST /topics/{topic}} with {@code {"records":[{"key":..,"value":..}]}}</li>
 * <li>{@code GET  /topics} and {@code GET /topics/{topic}}</li>
 * </ul>
 * Published records are kept in memory and can be inspected at {@code GET /mock/kafka}.
 */
public final class KafkaHandler extends ServiceHandler {

    private static final String TOPICS_PREFIX = "/topics/";
    private static final String V2_JSON = "application/vnd.kafka.v2+json";

    public KafkaHandler(final MockState _state) {
        super(_state);
    }

    /**
     * Send a REST Proxy-style error ({@code error_code} / {@code message}).
     *
     * @param _call
     *            the call to answer
     * @param _status
     *            the HTTP status
     * @param _code
     *            the REST Proxy error code
     * @param _message
     *            the error message
     * @throws IOException
     *             if the response cannot be written
     */
    private static void error(final HttpCall _call, final int _status, final int _code, final String _message) throws IOException {
        final ObjectNode err = Json.object();
        err.put("error_code", _code);
        err.put("message", _message);
        _call.sendJson(_status, err, V2_JSON);
    }

    /**
     * Handle {@code GET /topics/{topic}}: minimal topic metadata for topics that have received records.
     *
     * @param _call
     *            the call to handle
     * @param _topic
     *            the topic name
     * @throws IOException
     *             if the response cannot be written
     */
    private void describeTopic(final HttpCall _call, final String _topic) throws IOException {
        if (!m_state.kafkaTopicNames().contains(_topic)) {
            error(_call, 404, 40401, "Topic not found.");
            return;
        }
        final ObjectNode t = Json.object();
        t.put("name", _topic);
        t.putObject("configs");
        final ObjectNode p = t.putArray("partitions").addObject();
        p.put("partition", 0);
        p.put("leader", 1);
        p.putArray("replicas").addObject().put("broker", 1).put("leader", true).put("in_sync", true);
        _call.sendJson(200, t, V2_JSON);
    }

    @Override
    public String getServiceName() {
        return "kafka";
    }

    /**
     * Route a REST Proxy request by path and method.
     *
     * @param _call
     *            the call to handle
     * @throws IOException
     *             if the response cannot be written
     */
    @Override
    public void handle(final HttpCall _call) throws IOException {
        final String path = _call.getPath();
        if ("/topics".equals(path)) {
            final ArrayNode names = Json.array();
            for (final String t : m_state.kafkaTopicNames()) {
                names.add(t);
            }
            _call.sendJson(200, names, V2_JSON);
            return;
        }
        final String topic = path.substring(TOPICS_PREFIX.length());
        if (topic.isEmpty() || topic.contains("/")) {
            error(_call, 404, 40401, "Topic not found.");
        } else if ("GET".equals(_call.getMethod())) {
            describeTopic(_call, topic);
        } else if ("POST".equals(_call.getMethod())) {
            produce(_call, topic);
        } else {
            error(_call, 405, 40501, "HTTP " + _call.getMethod() + " not allowed.");
        }
    }

    /**
     * Handle {@code POST /topics/{topic}}. Content types are checked leniently: the toolkit's HTTP options may not deliver the REST Proxy content type, so any JSON-ish type (or none) is accepted.
     *
     * @param _call
     *            the call to handle
     * @param _topic
     *            the topic name
     * @throws IOException
     *             if the response cannot be written
     */
    private void produce(final HttpCall _call, final String _topic) throws IOException {
        final String contentType = _call.getHeader("Content-Type");
        if (null != contentType && !contentType.toLowerCase(Locale.ROOT).contains("json")) {
            error(_call, 415, 415, "HTTP 415 Unsupported Media Type");
            return;
        }
        final JsonNode body = _call.getJson();
        if (null == body || !body.isObject()) {
            error(_call, 400, 400, "Unexpected character: request body is not valid JSON.");
            return;
        }
        final JsonNode records = body.get("records");
        if (null == records || !records.isArray() || 0 == records.size()) {
            error(_call, 422, 42200, "records may not be empty");
            return;
        }
        final StringBuilder text = new StringBuilder();
        final List<JsonNode> list = new ArrayList<JsonNode>();
        for (final JsonNode r : records) {
            if (!r.isObject() || !r.has("value")) {
                error(_call, 422, 42201, "Each record must be an object with a 'value' field.");
                return;
            }
            list.add(r);
            text.append(Json.text(r, "value")).append('\n').append(Json.text(r, "key")).append('\n');
        }
        final Directives directives = Directives.parse(text.toString());
        directives.applyDelay();
        if (directives.isErrorRequested()) {
            final int status = directives.getStatus().intValue();
            error(_call, status, status * 100 + 1, "Mock error requested via directive.");
            return;
        }
        _call.sendJson(200, produceResponse(_topic, list, directives.isRecordError()), V2_JSON);
    }

    /**
     * Store the records (unless a record error was requested) and build the produce response.
     *
     * @param _topic
     *            the topic name
     * @param _records
     *            the records to store
     * @param _recordError
     *            if true, store nothing and report an error for every record
     * @return the produce response
     */
    private ObjectNode produceResponse(final String _topic, final List<JsonNode> _records, final boolean _recordError) {
        final ObjectNode resp = Json.object();
        resp.putNull("key_schema_id");
        resp.putNull("value_schema_id");
        final ArrayNode offsets = resp.putArray("offsets");
        final long first = _recordError ? 0 : m_state.kafkaAppend(_topic, _records);
        for (int i = 0; i < _records.size(); i++) {
            final ObjectNode o = offsets.addObject();
            if (_recordError) {
                o.putNull("partition");
                o.putNull("offset");
                o.put("error_code", 50002);
                o.put("error", "Kafka error: mock record failure requested via directive");
            } else {
                o.put("partition", 0);
                o.put("offset", first + i);
                o.putNull("error_code");
                o.putNull("error");
            }
        }
        return resp;
    }
}
