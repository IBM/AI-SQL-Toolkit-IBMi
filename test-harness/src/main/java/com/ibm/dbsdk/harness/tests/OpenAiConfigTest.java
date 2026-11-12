package com.ibm.dbsdk.harness.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The OpenAI-compatible configuration functions. No network calls. */
@DisplayName("OpenAI-compatible configuration")
@Tag("openai")
final class OpenAiConfigTest extends ToolkitTest {

    /**
     * Set every OpenAI-compatible job setting to a recognizable value.
     *
     * @throws SQLException
     *             if a setter fails
     */
    @BeforeAll
    void configureJob() throws SQLException {
        call("openai_compatible_setprotocolforjob(" + STR + ")", "https");
        call("openai_compatible_setserverforjob(" + STR + ")", "job-server");
        call("openai_compatible_setportforjob(" + INT + ")", Integer.valueOf(9443));
        call("openai_compatible_setbasepathforjob(" + STR + ")", "/job/v1");
        call("openai_compatible_setmodelforjob(" + STR + ")", "job-model");
        call("openai_compatible_setapikeyforjob(" + STR + ")", "job-api-key");
    }

    /**
     * An explicit argument to a getter takes precedence over the job setting.
     *
     * @throws SQLException
     *             if a call fails
     */
    @Test
    @DisplayName("getters prefer an explicit argument over the job setting")
    void explicitArgumentOverridesJobSetting() throws SQLException {
        assertEquals("http", value("dbsdk_v1.openai_compatible_getprotocol(" + STR + ")", "http"));
        assertEquals("explicit-server", value("dbsdk_v1.openai_compatible_getserver(" + STR + ")", "explicit-server"));
        assertEquals("1111", value("dbsdk_v1.openai_compatible_getport(" + INT + ")", Integer.valueOf(1111)));
        assertEquals("/explicit", value("dbsdk_v1.openai_compatible_getbasepath(" + STR + ")", "/explicit"));
        assertEquals("explicit-model", value("dbsdk_v1.openai_compatible_getmodel(" + STR + ")", "explicit-model"));
        assertEquals("explicit-key", value("dbsdk_v1.openai_compatible_getapikey(" + STR + ")", "explicit-key"));
    }

    /**
     * Values set with the *forjob procedures are returned by the getters.
     *
     * @throws SQLException
     *             if a call fails
     */
    @Test
    @DisplayName("*forjob settings are returned by the getters")
    void jobSettingsAreReturnedByGetters() throws SQLException {
        assertEquals("https", value("dbsdk_v1.openai_compatible_getprotocol()"));
        assertEquals("job-server", value("dbsdk_v1.openai_compatible_getserver()"));
        assertEquals("9443", value("dbsdk_v1.openai_compatible_getport()"));
        assertEquals("/job/v1", value("dbsdk_v1.openai_compatible_getbasepath()"));
        assertEquals("job-model", value("dbsdk_v1.openai_compatible_getmodel()"));
        assertEquals("job-api-key", value("dbsdk_v1.openai_compatible_getapikey()"));
    }
}
