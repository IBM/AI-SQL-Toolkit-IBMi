package com.ibm.dbsdk.harness.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.SQLException;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.ibm.dbsdk.harness.HarnessConfig;
import com.ibm.dbsdk.harness.MockClient;

/** {@code twilio_sendsms}, with the Twilio base URL pointed at the mock server. */
@DisplayName("Twilio send")
@Tag("twilio")
final class TwilioSendTest extends ToolkitTest {

    private static final String ACCOUNT_SID = "ACmockAccountSid";
    private static final String FROM_NUMBER = "5557654321";

    /**
     * Point this job's Twilio base URL at the mock, and set the account and sender.
     *
     * @throws SQLException
     *             if a setter fails
     */
    @BeforeAll
    void configureJob() throws SQLException {
        call("twilio_setbaseurlforjob(" + STR + ")", mockBaseUrl());
        call("twilio_setsidforjob(" + STR + ")", ACCOUNT_SID);
        call("twilio_setauthtokenforjob(" + STR + ")", "harness-auth-token");
        call("twilio_setnumberforjob(" + STR + ")", FROM_NUMBER);
    }

    @Test
    @DisplayName("sendsms signals SQLSTATE 38002 with Twilio's error message")
    void errorSignals38002() {
        assertSqlState("38002", () -> send("5551234567", "nope [[mock:status=400]]"));
    }

    /**
     * A + in an E.164 number must survive form encoding. The body is not URL-encoded, so + arrives as a space until #50 is fixed.
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @Tag(HarnessConfig.TAG_KNOWN_ISSUE)
    @DisplayName("sendsms preserves '+' in E.164 phone numbers [known issue #50]")
    void plusSignIsPreserved() throws Exception {
        send("+15551234567", "Plus sign check");
        assertEquals("+15551234567", MockClient.formFields(lastRequest("twilio")).get("To"));
    }

    private String send(final String _to, final String _message) throws SQLException {
        return value("dbsdk_v1.twilio_sendsms(" + STR + ", " + STR + ")", _to, _message);
    }

    /**
     * A message returns Twilio's status, and the request carries the number, sender and body.
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @DisplayName("sendsms returns 'queued' and sends To, From and Body")
    void sendReturnsQueued() throws Exception {
        assertEquals("queued", send("5551234567", "Hello from the harness"));
        final JsonNode request = lastRequest("twilio");
        assertEquals("/2010-04-01/Accounts/" + ACCOUNT_SID + "/Messages.json", request.path("path").asText());
        final Map<String, String> form = MockClient.formFields(request);
        assertEquals("5551234567", form.get("To"));
        assertEquals(FROM_NUMBER, form.get("From"));
        assertEquals("Hello from the harness", form.get("Body"));
    }
}
