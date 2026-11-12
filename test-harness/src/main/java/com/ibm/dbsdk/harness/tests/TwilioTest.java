package com.ibm.dbsdk.harness.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The Twilio configuration functions. No network calls; see {@link TwilioSendTest} for sending. */
@DisplayName("Twilio configuration")
@Tag("twilio")
final class TwilioTest extends ToolkitTest {

    /**
     * Set every Twilio job setting to a recognizable value.
     *
     * @throws SQLException
     *             if a setter fails
     */
    @BeforeAll
    void configureJob() throws SQLException {
        call("twilio_setnumberforjob(" + STR + ")", "5550001111");
        call("twilio_setsidforjob(" + STR + ")", "ACjobAccountSid");
        call("twilio_setauthtokenforjob(" + STR + ")", "job-auth-token");
    }

    /**
     * With nothing configured, the base URL is the Twilio API.
     *
     * @throws SQLException
     *             if a call fails
     */
    @Test
    @DisplayName("base URL defaults to https://api.twilio.com")
    void baseUrlDefault() throws SQLException {
        assumeTrue(null == value("(select twilio_baseurl from dbsdk_v1.conf where usrprf = current_user)"), "this user's CONF row sets a Twilio base URL, so the default cannot be observed");
        assertEquals("https://api.twilio.com", value("dbsdk_v1.twilio_getbaseurl()"));
    }

    /**
     * The job setting is used with its trailing slash removed, an explicit argument wins over it, and an empty value falls back to the default.
     *
     * @throws SQLException
     *             if a call fails
     */
    @Test
    @DisplayName("base URL job setting and explicit argument are used; '/' is trimmed")
    void baseUrlJobSettingAndArgument() throws SQLException {
        call("twilio_setbaseurlforjob(" + STR + ")", "http://job-twilio.example:8080/");
        try {
            assertEquals("http://job-twilio.example:8080", value("dbsdk_v1.twilio_getbaseurl()"));
            assertEquals("http://arg-twilio.example", value("dbsdk_v1.twilio_getbaseurl(" + STR + ")", "http://arg-twilio.example"));
            call("twilio_setbaseurlforjob(" + STR + ")", "");
            assertEquals("https://api.twilio.com", value("dbsdk_v1.twilio_getbaseurl()"));
        } finally {
            call("twilio_setbaseurlforjob(" + STR + ")", (Object) null);
        }
    }

    @Test
    @DisplayName("getters prefer an explicit argument over the job setting")
    void explicitArgumentOverridesJobSetting() throws SQLException {
        assertEquals("5559998888", value("dbsdk_v1.twilio_getnumber(" + STR + ")", "5559998888"));
        assertEquals("ACexplicitSid", value("dbsdk_v1.twilio_getsid(" + STR + ")", "ACexplicitSid"));
        assertEquals("explicit-token", value("dbsdk_v1.twilio_getauthtoken(" + STR + ")", "explicit-token"));
    }

    @Test
    @DisplayName("*forjob settings are returned by the getters")
    void jobSettingsAreReturnedByGetters() throws SQLException {
        assertEquals("5550001111", value("dbsdk_v1.twilio_getnumber()"));
        assertEquals("ACjobAccountSid", value("dbsdk_v1.twilio_getsid()"));
        assertEquals("job-auth-token", value("dbsdk_v1.twilio_getauthtoken()"));
    }
}
