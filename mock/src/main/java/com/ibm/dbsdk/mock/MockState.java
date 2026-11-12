package com.ibm.dbsdk.mock;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** All mutable server state: issued watsonx tokens, recorded requests and Kafka topic contents. */
public final class MockState {

    private final MockConfig m_config;
    private final Map<String, List<JsonNode>> m_kafkaTopics = new TreeMap<String, List<JsonNode>>();
    private final AtomicLong m_requestSeq = new AtomicLong();
    private final Deque<ObjectNode> m_requests = new ArrayDeque<ObjectNode>();
    private final Instant m_startedAt = Instant.now();
    private final Map<String, Long> m_tokenExpiries = new ConcurrentHashMap<String, Long>();

    public MockState(final MockConfig _config) {
        m_config = _config;
    }

    /**
     * Check whether a bearer token was issued by this mock and is still valid.
     *
     * @param _token
     *            the token presented by the client; may be null
     * @return the token's status
     */
    public TokenStatus checkToken(final String _token) {
        if (null == _token) {
            return TokenStatus.UNKNOWN;
        }
        final Long expiresAt = m_tokenExpiries.get(_token);
        if (null == expiresAt) {
            return TokenStatus.UNKNOWN;
        }
        return System.currentTimeMillis() < expiresAt.longValue() ? TokenStatus.VALID : TokenStatus.EXPIRED;
    }

    /**
     * Clear the request history.
     *
     * @return the number of requests that were cleared
     */
    public int clearRequests() {
        synchronized (m_requests) {
            final int count = m_requests.size();
            m_requests.clear();
            return count;
        }
    }

    public MockConfig getConfig() {
        return m_config;
    }

    public Instant getStartedAt() {
        return m_startedAt;
    }

    /**
     * Issue a new bearer token that expires after the configured TTL.
     *
     * @return the token
     */
    public String issueToken() {
        final String token = "mock-watsonx-" + UUID.randomUUID().toString().replace("-", "");
        m_tokenExpiries.put(token, Long.valueOf(System.currentTimeMillis() + m_config.getTokenTtlSeconds() * 1000L));
        return token;
    }

    /**
     * Append records to a Kafka topic, creating the topic if needed.
     *
     * @param _topic
     *            the topic name
     * @param _records
     *            the records to append
     * @return the offset of the first appended record
     */
    public long kafkaAppend(final String _topic, final List<JsonNode> _records) {
        synchronized (m_kafkaTopics) {
            List<JsonNode> list = m_kafkaTopics.get(_topic);
            if (null == list) {
                list = new ArrayList<JsonNode>();
                m_kafkaTopics.put(_topic, list);
            }
            final long first = list.size();
            list.addAll(_records);
            return first;
        }
    }

    /**
     * Snapshot every topic's records with their partition and offset.
     *
     * @return an object keyed by topic name
     */
    public ObjectNode kafkaSnapshot() {
        final ObjectNode out = Json.object();
        synchronized (m_kafkaTopics) {
            for (final Map.Entry<String, List<JsonNode>> topic : m_kafkaTopics.entrySet()) {
                final ArrayNode records = out.putArray(topic.getKey());
                long offset = 0;
                for (final JsonNode rec : topic.getValue()) {
                    final ObjectNode r = records.addObject();
                    r.put("partition", 0);
                    r.put("offset", offset++);
                    r.set("key", rec.get("key"));
                    r.set("value", rec.get("value"));
                }
            }
        }
        return out;
    }

    /**
     * @return the names of all topics that have received records
     */
    public List<String> kafkaTopicNames() {
        synchronized (m_kafkaTopics) {
            return new ArrayList<String>(m_kafkaTopics.keySet());
        }
    }

    /**
     * Record a completed request and its response. Credentials (authorization headers, API keys, tokens, Slack webhook secrets) are masked before anything is stored. Only the newest
     * {@code --max-recorded} requests are kept.
     *
     * @param _call
     *            the completed call
     * @param _durationMillis
     *            how long the request took to handle
     */
    public void record(final HttpCall _call, final long _durationMillis) {
        final ObjectNode entry = Json.object();
        entry.put("id", m_requestSeq.incrementAndGet());
        entry.put("timestamp", Instant.now().toString());
        entry.put("service", _call.getServiceName());
        entry.put("method", _call.getMethod());
        entry.put("path", Redactor.redactPath(_call.getPath()));
        entry.put("uri", Redactor.redactPath(_call.getRawUri()));
        entry.put("remote", _call.getRemoteAddress());
        final ObjectNode headers = entry.putObject("headers");
        for (final Map.Entry<String, List<String>> h : new TreeMap<String, List<String>>(_call.getHeaders()).entrySet()) {
            headers.put(h.getKey(), Redactor.redactHeader(h.getKey(), String.join(", ", h.getValue())));
        }
        final JsonNode json = Redactor.redactJson(_call.getJson());
        if (null != json) {
            entry.put("body", json.toString());
            entry.set("json", json);
        } else {
            entry.put("body", Redactor.redactFormBody(_call.getBodyText()));
        }
        final ObjectNode response = entry.putObject("response");
        response.put("status", _call.getResponseStatus());
        response.put("contentType", _call.getResponseContentType());
        final String contentType = null == _call.getResponseContentType() ? "" : _call.getResponseContentType().toLowerCase(Locale.ROOT);
        response.put("body", contentType.contains("json") ? Redactor.redactJsonText(_call.getResponseBody()) : _call.getResponseBody());
        entry.put("durationMillis", _durationMillis);
        synchronized (m_requests) {
            m_requests.addLast(entry);
            while (m_requests.size() > m_config.getMaxRecordedRequests()) {
                m_requests.removeFirst();
            }
        }
    }

    /**
     * Get recorded requests, oldest first.
     *
     * @param _service
     *            only return requests for this service; null for all
     * @param _limit
     *            only return the newest N matching requests; null for all
     * @return copies of the matching recorded requests
     */
    public ArrayNode requests(final String _service, final Integer _limit) {
        final List<ObjectNode> matching = new ArrayList<ObjectNode>();
        synchronized (m_requests) {
            for (final ObjectNode e : m_requests) {
                if (null == _service || _service.equalsIgnoreCase(e.path("service").asText())) {
                    matching.add(e);
                }
            }
        }
        final int from = null == _limit ? 0 : Math.max(0, matching.size() - _limit.intValue());
        final ArrayNode out = Json.array();
        for (int i = from; i < matching.size(); i++) {
            out.add(matching.get(i).deepCopy());
        }
        return out;
    }

    /**
     * Clear recorded requests, issued tokens and Kafka topics.
     */
    public void reset() {
        clearRequests();
        m_tokenExpiries.clear();
        synchronized (m_kafkaTopics) {
            m_kafkaTopics.clear();
        }
    }

    /** Result of validating a bearer token. */
    public enum TokenStatus {
        EXPIRED, UNKNOWN, VALID
    }
}
