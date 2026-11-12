package com.ibm.dbsdk.harness.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.ibm.dbsdk.harness.HarnessConfig;

/** {@code slack_sendmessage} and the Slack webhook configuration functions. */
@DisplayName("Slack")
@Tag("slack")
final class SlackTest extends ToolkitTest {

    private String m_webhook = null;

    @BeforeAll
    void configureJob() throws SQLException {
        m_webhook = mockBaseUrl() + "/services/T000HARNESS/B000HARNESS/harness-webhook";
        call("slack_setwebhookforjob(" + STR + ")", m_webhook);
    }

    /**
     * Slack's error text should be raised as the message of SQLSTATE 38002. The error branch refers to a misspelled variable, so this path is expected to fail until #48 is fixed.
     */
    @Test
    @Tag(HarnessConfig.TAG_KNOWN_ISSUE)
    @DisplayName("sendmessage signals SQLSTATE 38002 with Slack's error text [known issue #48]")
    void errorSignalsSlackError() {
        final SQLException e = assertSqlState("38002", () -> send("nope [[mock:status=403]]"));
        assertTrue(e.getMessage().contains("invalid_token"), "message should carry Slack's error: " + e.getMessage());
    }

    @Test
    @DisplayName("getwebhook prefers an explicit argument over the job setting")
    void explicitArgumentOverridesJobSetting() throws SQLException {
        assertEquals("https://example.invalid/hook", value("dbsdk_v1.slack_getwebhook(" + STR + ")", "https://example.invalid/hook"));
    }

    @Test
    @DisplayName("*forjob webhook is returned by the getter")
    void jobSettingIsReturnedByGetter() throws SQLException {
        assertEquals(m_webhook, value("dbsdk_v1.slack_getwebhook()"));
    }

    private String send(final String _message) throws SQLException {
        return value("dbsdk_v1.slack_sendmessage(" + STR + ")", _message);
    }

    /**
     * A message posts {"text": ...} to the webhook and returns Slack's "ok".
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @DisplayName("sendmessage posts the text and returns 'ok'")
    void sendReturnsOk() throws Exception {
        assertEquals("ok", send("Hello from the test harness"));
        final JsonNode request = lastRequest("slack");
        assertEquals("Hello from the test harness", request.path("json").path("text").asText());
        assertTrue(request.path("path").asText().startsWith("/services/T000HARNESS/B000HARNESS/"), request.path("path").asText());
    }

    /**
     * The webhook argument overrides the job's webhook.
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @DisplayName("sendmessage uses the webhook argument when given")
    void sendToExplicitWebhook() throws Exception {
        final String other = mockBaseUrl() + "/services/T111OTHER/B111OTHER/other-webhook";
        assertEquals("ok", value("dbsdk_v1.slack_sendmessage(" + STR + ", " + STR + ")", "to the other hook", other));
        assertTrue(lastRequest("slack").path("path").asText().startsWith("/services/T111OTHER/B111OTHER/"));
    }
}
