package com.ibm.dbsdk.harness.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

/** {@code ollama_generate} and the Ollama configuration functions. */
@DisplayName("Ollama")
@Tag("ollama")
final class OllamaTest extends ToolkitTest {

    private static final String MODEL = "granite3.2:8b";

    /**
     * Point this job's Ollama settings at the mock server.
     *
     * @throws SQLException
     *             if a setter fails
     */
    @BeforeAll
    void configureJob() throws SQLException {
        call("ollama_setprotocolforjob(" + STR + ")", "http");
        call("ollama_setserverforjob(" + STR + ")", mockHost());
        call("ollama_setportforjob(" + INT + ")", mockPort());
        call("ollama_setmodelforjob(" + STR + ")", MODEL);
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
        assertEquals("explicit-host", value("dbsdk_v1.ollama_getserver(" + STR + ")", "explicit-host"));
        assertEquals("1234", value("dbsdk_v1.ollama_getport(" + INT + ")", Integer.valueOf(1234)));
        assertEquals("explicit-model", value("dbsdk_v1.ollama_getmodel(" + STR + ")", "explicit-model"));
        assertEquals("https", value("dbsdk_v1.ollama_getprotocol(" + STR + ")", "https"));
    }

    private String generate(final String _prompt) throws SQLException {
        return clobValue("dbsdk_v1.ollama_generate(" + STR + ")", _prompt);
    }

    /**
     * The model's reply comes back exactly, including quotes and markup characters.
     *
     * @throws SQLException
     *             if the call fails
     */
    @Test
    @DisplayName("generate returns the model's text unchanged")
    void generateReturnsTextUnchanged() throws SQLException {
        assertEquals("Line one. \"Quoted\" & <tagged> text!", generate("[[mock:reply=Line one. \"Quoted\" & <tagged> text!]]"));
    }

    @Test
    @DisplayName("generate preserves non-ASCII text (CCSID 1208)")
    void generateRoundTripsUnicode() throws SQLException {
        final String prompt = "Gr\u00fc\u00dfe, \u4e16\u754c \u2713";
        assertEquals("Mock ollama response from " + MODEL + ": " + prompt, generate(prompt));
    }

    /**
     * The request matches Ollama's API: model, prompt, and streaming turned off.
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @DisplayName("generate sends a non-streaming /api/generate request")
    void generateSendsNonStreamingRequest() throws Exception {
        generate("Check the request");
        final JsonNode request = lastRequest("ollama");
        assertEquals("/api/generate", request.path("path").asText());
        final JsonNode body = request.get("json");
        assertEquals(MODEL, body.path("model").asText());
        assertEquals("Check the request", body.path("prompt").asText());
        assertTrue(body.has("stream") && !body.get("stream").asBoolean(true), "stream must be false: " + body);
    }

    @Test
    @DisplayName("generate signals SQLSTATE 38002 when the server returns an HTTP error")
    void generateSignalsOnHttpError() {
        assertSqlState("38002", () -> generate("fail please [[mock:status=500]]"));
    }

    @Test
    @DisplayName("generate uses the job's model by default")
    void generateUsesJobModel() throws SQLException {
        assertEquals("Mock ollama response from " + MODEL + ": Why is the sky blue?", generate("Why is the sky blue?"));
    }

    /**
     * The model_id argument overrides the configured model.
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @DisplayName("generate uses the model_id argument when given")
    void generateWithExplicitModel() throws Exception {
        final String reply = clobValue("dbsdk_v1.ollama_generate(" + STR + ", model_id => " + STR + ")", "Hello", "llama3:latest");
        assertEquals("Mock ollama response from llama3:latest: Hello", reply);
        assertEquals("llama3:latest", lastRequestJson("ollama").path("model").asText());
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
        assertEquals(mockHost(), value("dbsdk_v1.ollama_getserver()"));
        assertEquals(String.valueOf(mockPort()), value("dbsdk_v1.ollama_getport()"));
        assertEquals(MODEL, value("dbsdk_v1.ollama_getmodel()"));
        assertEquals("http", value("dbsdk_v1.ollama_getprotocol()"));
    }
}
