package com.ibm.dbsdk.harness.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.ibm.dbsdk.harness.HarnessConfig;
import com.ibm.dbsdk.harness.MockClient;

/** {@code kafka_publish} (Kafka REST Proxy) and the Kafka configuration functions. */
@DisplayName("Kafka")
@Tag("kafka")
final class KafkaTest extends ToolkitTest {

    private String m_jobTopic = null;

    /**
     * Point this job's Kafka settings at the mock server, with a topic unique to this run.
     *
     * @throws SQLException
     *             if a setter fails
     */
    @BeforeAll
    void configureJob() throws SQLException {
        m_jobTopic = unique("harness-orders");
        call("kafka_setprotocolforjob(" + STR + ")", "http");
        call("kafka_setbrokerforjob(" + STR + ")", mockHost());
        call("kafka_setportforjob(" + INT + ")", mockPort());
        call("kafka_settopicforjob(" + STR + ")", m_jobTopic);
    }

    @Test
    @DisplayName("getters prefer an explicit argument over the job setting")
    void explicitArgumentOverridesJobSetting() throws SQLException {
        assertEquals("explicit-broker", value("dbsdk_v1.kafka_getbroker(" + STR + ")", "explicit-broker"));
        assertEquals("9092", value("dbsdk_v1.kafka_getport(" + INT + ")", Integer.valueOf(9092)));
        assertEquals("explicit-topic", value("dbsdk_v1.kafka_gettopic(" + STR + ")", "explicit-topic"));
        assertEquals("https", value("dbsdk_v1.kafka_getprotocol(" + STR + ")", "https"));
    }

    @Test
    @DisplayName("publish signals SQLSTATE 38002 on an HTTP error")
    void httpErrorSignals38002() {
        assertSqlState("38002", () -> publish("broker down [[mock:status=503]]", "k"));
    }

    @Test
    @DisplayName("*forjob settings are returned by the getters")
    void jobSettingsAreReturnedByGetters() throws SQLException {
        assertEquals(mockHost(), value("dbsdk_v1.kafka_getbroker()"));
        assertEquals(String.valueOf(mockPort()), value("dbsdk_v1.kafka_getport()"));
        assertEquals(m_jobTopic, value("dbsdk_v1.kafka_gettopic()"));
        assertEquals("http", value("dbsdk_v1.kafka_getprotocol()"));
    }

    /**
     * Get the last record the mock stored for a topic.
     *
     * @param _topic
     *            the topic
     * @return the record
     * @throws Exception
     *             if the mock inspection fails
     */
    private JsonNode lastStoredRecord(final String _topic) throws Exception {
        final JsonNode records = mock().kafkaRecords().path(_topic);
        assertTrue(records.isArray() && records.size() > 0, "no records stored for topic " + _topic);
        return records.get(records.size() - 1);
    }

    private String publish(final String _message, final String _key) throws SQLException {
        return value("dbsdk_v1.kafka_publish(msgdata => " + STR + ", key => " + STR + ")", _message, _key);
    }

    /**
     * The REST Proxy request body is a records array with one key/value record.
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @DisplayName("publish sends a REST Proxy records envelope")
    void publishSendsRecordsEnvelope() throws Exception {
        publish("envelope check", "k1");
        final JsonNode records = lastRequestJson("kafka").path("records");
        assertEquals(1, records.size());
        assertEquals("k1", records.at("/0/key").asText());
        assertEquals("envelope check", records.at("/0/value").asText());
    }

    /**
     * A successful publish returns NULL and the record lands on the job's topic.
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @DisplayName("publish returns NULL and stores the record on the job's topic")
    void publishStoresRecord() throws Exception {
        assertNull(publish("{\"order\": 1001, \"status\": \"shipped\"}", "order-1001"));
        final JsonNode record = lastStoredRecord(m_jobTopic);
        assertEquals("order-1001", record.path("key").asText());
        assertEquals("{\"order\": 1001, \"status\": \"shipped\"}", record.path("value").asText());
        assertEquals("/topics/" + m_jobTopic, lastRequest("kafka").path("path").asText());
    }

    /**
     * The topic argument overrides the job's topic.
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @DisplayName("publish uses the topic argument when given")
    void publishToExplicitTopic() throws Exception {
        final String topic = unique("harness-audit");
        assertNull(value("dbsdk_v1.kafka_publish(" + STR + ", " + STR + ", " + STR + ")", "user logged in", topic, "u1"));
        assertEquals("user logged in", lastStoredRecord(topic).path("value").asText());
    }

    /**
     * The key is optional.
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @DisplayName("publish without a key stores a record with a null key")
    void publishWithoutKey() throws Exception {
        final String topic = unique("harness-nokey");
        assertNull(value("dbsdk_v1.kafka_publish(msgdata => " + STR + ", topic => " + STR + ")", "no key here", topic));
        final JsonNode record = lastStoredRecord(topic);
        assertTrue(record.path("key").isNull() || record.path("key").isMissingNode(), "key should be null: " + record);
    }

    /**
     * The documented return value is the API error, if any; a per-record error inside a 200 response is currently only logged and NULL is returned.
     *
     * @throws SQLException
     *             if the call fails
     */
    @Test
    @Tag(HarnessConfig.TAG_KNOWN_ISSUE)
    @DisplayName("publish returns the per-record error from the REST Proxy [known issue]")
    void recordErrorIsReturned() throws SQLException {
        final String result = publish("bad record [[mock:record-error]]", "k");
        assertNotNull(result, "kafka_publish should return the record error, but returned NULL");
        assertTrue(result.contains("mock record failure"), result);
    }

    /**
     * Both REST Proxy headers must arrive. The HTTP options repeat the "header" key; QSYS2 accepts repeated keys and sends both headers (verified on IBM i 7.5, see #51).
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @DisplayName("publish sends the REST Proxy Content-Type and Accept headers")
    void sendsRestProxyHeaders() throws Exception {
        publish("header check", "k");
        final JsonNode request = lastRequest("kafka");
        assertEquals("application/vnd.kafka.json.v2+json", MockClient.header(request, "Content-Type"));
        assertEquals("application/vnd.kafka.v2+json", MockClient.header(request, "Accept"));
    }
}
