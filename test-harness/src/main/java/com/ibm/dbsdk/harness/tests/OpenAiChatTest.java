package com.ibm.dbsdk.harness.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.ibm.dbsdk.harness.MockClient;

/**
 * {@code openai_compatible_chat_generate} against {@code /chat/completions}. Unlike the legacy function it sends optional fields only when they are given, because endpoints such as Gemini
 * reject fields they do not recognize.
 */
@DisplayName("OpenAI-compatible /chat/completions")
@Tag("openai")
final class OpenAiChatTest extends ToolkitTest {

    private static final String MODEL = "llama3";

    /**
     * Point this job's OpenAI-compatible settings at the mock server.
     *
     * @throws SQLException
     *             if a setter fails
     */
    @BeforeAll
    void configureJob() throws SQLException {
        call("openai_compatible_setprotocolforjob(" + STR + ")", "http");
        call("openai_compatible_setserverforjob(" + STR + ")", mockHost());
        call("openai_compatible_setportforjob(" + INT + ")", mockPort());
        call("openai_compatible_setbasepathforjob(" + STR + ")", "/v1");
        call("openai_compatible_setmodelforjob(" + STR + ")", MODEL);
        call("openai_compatible_setapikeyforjob(" + STR + ")", "harness-key");
    }

    /**
     * @param _body
     *            a JSON object
     * @return its field names, sorted
     */
    private static List<String> fieldNames(final JsonNode _body) {
        final List<String> names = new ArrayList<String>();
        final Iterator<String> it = _body.fieldNames();
        while (it.hasNext()) {
            names.add(it.next());
        }
        Collections.sort(names);
        return names;
    }

    @Test
    @DisplayName("api_key argument is sent as a Bearer token")
    void apiKeyArgumentIsSentAsBearerToken() throws Exception {
        clobValue("dbsdk_v1.openai_compatible_chat_generate(" + STR + ", " + STR + ", " + STR + ")", "Hello", "{}", "explicit-key");
        assertEquals("Bearer ****", MockClient.header(lastRequest("openai"), "Authorization"));
    }

    /**
     * base_url replaces the job's endpoint settings; /chat/completions is appended to it.
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @DisplayName("base_url argument overrides the job's endpoint settings")
    void baseUrlArgumentOverridesJobSettings() throws Exception {
        final String reply = clobValue("dbsdk_v1.openai_compatible_chat_generate(" + STR + ", " + STR + ", " + STR + ", " + STR + ")", "Hi", "{}", null, mockBaseUrl() + "/v1beta/openai");
        assertEquals("Mock chat response from " + MODEL + ": Hi", reply);
        assertEquals("/v1beta/openai/chat/completions", lastRequest("openai").path("path").asText());
    }

    private String chat(final String _prompt, final String _options) throws SQLException {
        return clobValue("dbsdk_v1.openai_compatible_chat_generate(" + STR + ", " + STR + ")", _prompt, _options);
    }

    /**
     * The prompt becomes a single user message, and the reply is choices[0].message.content.
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @DisplayName("chat_generate sends the prompt as a user message")
    void chatSendsUserMessage() throws Exception {
        assertEquals("Mock chat response from " + MODEL + ": Hello", chat("Hello", "{}"));
        final JsonNode request = lastRequest("openai");
        assertEquals("/v1/chat/completions", request.path("path").asText());
        final JsonNode messages = request.path("json").path("messages");
        assertEquals(1, messages.size());
        assertEquals("user", messages.at("/0/role").asText());
        assertEquals("Hello", messages.at("/0/content").asText());
    }

    /**
     * With empty options only model and messages are sent.
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @DisplayName("empty options send only model and messages")
    void emptyOptionsSendOnlyModelAndMessages() throws Exception {
        chat("Hello", "{}");
        assertEquals(Arrays.asList("messages", "model"), fieldNames(lastRequestJson("openai")));
    }

    /**
     * Every supported option is forwarded with its value, and max_tokens limits the reply.
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @DisplayName("extended options are all sent")
    void extendedOptionsAreSent() throws Exception {
        final String options = "{\"temperature\": 0.2, \"max_tokens\": 5, \"top_p\": 0.9, \"n\": 2, \"presence_penalty\": 0.5, \"frequency_penalty\": -0.5, \"stop\": \"zzz\", \"seed\": 7, \"user\": \"harness-user\"}";
        assertEquals("Mock chat response from " + MODEL + ":", chat("Hello", options));
        final JsonNode body = lastRequestJson("openai");
        assertNumber(0.2, body, "temperature");
        assertNumber(5, body, "max_tokens");
        assertNumber(0.9, body, "top_p");
        assertNumber(2, body, "n");
        assertNumber(0.5, body, "presence_penalty");
        assertNumber(-0.5, body, "frequency_penalty");
        assertEquals("zzz", body.path("stop").asText());
        assertNumber(7, body, "seed");
        assertEquals("harness-user", body.path("user").asText());
    }

    @Test
    @DisplayName("chat_generate signals SQLSTATE 38002 on an HTTP error")
    void httpErrorSignals38002() {
        final SQLException e = assertSqlState("38002", () -> chat("slow down [[mock:status=429]]", "{}"));
        assertTrue(e.getMessage().contains("429"), "message should name the HTTP status: " + e.getMessage());
    }

    @Test
    @DisplayName("chat_generate returns the full body when choices[0].message.content is missing")
    void malformedResponseReturnsFullBody() throws SQLException {
        final JsonNode reply = json(chat("x [[mock:malformed]]", "{}"));
        assertEquals("chat.completion", reply.path("object").asText());
        assertFalse(reply.has("choices"));
    }

    /**
     * Giving only max_tokens adds only max_tokens.
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @DisplayName("max_tokens alone adds only max_tokens and limits the reply")
    void maxTokensAloneAddsOnlyMaxTokens() throws Exception {
        assertEquals("Mock chat response", chat("Hello", "{\"max_tokens\": 3}"));
        assertEquals(Arrays.asList("max_tokens", "messages", "model"), fieldNames(lastRequestJson("openai")));
    }

    @Test
    @DisplayName("model_id option overrides the job's model")
    void modelIdOptionOverridesJobModel() throws Exception {
        assertEquals("Mock chat response from gemini-2.0-flash: Hi", chat("Hi", "{\"model_id\": \"gemini-2.0-flash\"}"));
        assertEquals("gemini-2.0-flash", lastRequestJson("openai").path("model").asText());
    }

    @Test
    @DisplayName("temperature alone adds only temperature")
    void temperatureAloneAddsOnlyTemperature() throws Exception {
        chat("Hello", "{\"temperature\": 0.7}");
        final JsonNode body = lastRequestJson("openai");
        assertEquals(Arrays.asList("messages", "model", "temperature"), fieldNames(body));
        assertNumber(0.7, body, "temperature");
    }
}
