package com.ibm.dbsdk.harness.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

/**
 * {@code openai_compatible_generate} and {@code openai_compatible_generate_json} against the legacy {@code /completions} endpoint, including every extended option the functions accept.
 */
@DisplayName("OpenAI-compatible /completions")
@Tag("openai")
final class OpenAiCompletionsTest extends ToolkitTest {

    private static final String JOB_API_KEY = "harness-key";

    private static final String MODEL = "llama3";

    private static final String PROMPT = "why are armadillos so cute?";

    /**
     * @param _prompt
     *            the prompt
     * @return the reply the mock generates for the prompt with the job's model
     */
    private static String expectedReply(final String _prompt) {
        return "Mock openai response from " + MODEL + ": " + _prompt;
    }

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
        call("openai_compatible_setapikeyforjob(" + STR + ")", JOB_API_KEY);
    }

    @Test
    @DisplayName("api_key argument is sent as a Bearer token")
    void apiKeyArgumentIsSentAsBearerToken() throws Exception {
        clobValue("dbsdk_v1.openai_compatible_generate(" + STR + ", " + STR + ", " + STR + ")", PROMPT, "{}", "explicit-key");
        // The mock masks credentials in its recordings, so only the scheme is visible
        assertEquals("Bearer ****", MockClient.header(lastRequest("openai"), "Authorization"));
    }

    /**
     * base_url replaces the protocol, server, port and base path from the job settings.
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @DisplayName("base_url argument overrides the job's endpoint settings")
    void baseUrlArgumentOverridesJobSettings() throws Exception {
        final String reply = clobValue("dbsdk_v1.openai_compatible_generate(" + STR + ", " + STR + ", " + STR + ", " + STR + ")", PROMPT, "{}", null, mockBaseUrl() + "/v1beta/openai");
        assertEquals(expectedReply(PROMPT), reply);
        assertEquals("/v1beta/openai/completions", lastRequest("openai").path("path").asText());
    }

    /**
     * With empty options the function still sends its documented defaults, and nothing else.
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @DisplayName("empty options send the documented defaults")
    void defaultOptionsAreSent() throws Exception {
        generate(PROMPT, "{}");
        final JsonNode body = lastRequestJson("openai");
        assertEquals(MODEL, body.path("model").asText());
        assertEquals(PROMPT, body.path("prompt").asText());
        assertNumber(256, body, "max_tokens");
        assertNumber(1, body, "temperature");
        assertNumber(1, body, "top_p");
        assertNumber(1, body, "n");
        assertNumber(0, body, "presence_penalty");
        assertNumber(0, body, "frequency_penalty");
        for (final String absent : new String[] { "stop", "seed", "user", "suffix", "logprobs", "logit_bias" }) {
            assertFalse(body.has(absent), "'" + absent + "' should not be sent by default: " + body);
        }
    }

    /**
     * An empty API key means no Authorization header at all.
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @DisplayName("an empty API key sends no Authorization header")
    void emptyApiKeySendsNoAuthorization() throws Exception {
        call("openai_compatible_setapikeyforjob(" + STR + ")", "");
        try {
            generate(PROMPT, "{}");
            assertNull(MockClient.header(lastRequest("openai"), "Authorization"));
        } finally {
            call("openai_compatible_setapikeyforjob(" + STR + ")", JOB_API_KEY);
        }
    }

    @Test
    @Tag(HarnessConfig.TAG_KNOWN_ISSUE)
    @DisplayName("presence_penalty and frequency_penalty are sent [known issue #52]")
    void frequencyAndPresencePenaltiesAreSent() throws Exception {
        generate(PROMPT, "{\"presence_penalty\": 1.5, \"frequency_penalty\": -1.5}");
        final JsonNode body = lastRequestJson("openai");
        assertNumber(1.5, body, "presence_penalty");
        assertNumber(-1.5, body, "frequency_penalty");
    }

    private String generate(final String _prompt, final String _options) throws SQLException {
        return clobValue("dbsdk_v1.openai_compatible_generate(" + STR + ", " + STR + ")", _prompt, _options);
    }

    private String generateJson(final String _prompt, final String _options) throws SQLException {
        return clobValue("dbsdk_v1.openai_compatible_generate_json(" + STR + ", " + STR + ")", _prompt, _options);
    }

    /**
     * generate_json forwards the same options as generate.
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @Tag(HarnessConfig.TAG_KNOWN_ISSUE)
    @DisplayName("generate_json sends temperature, top_p and max_tokens [known issue #52]")
    void generateJsonPassesExtendedOptions() throws Exception {
        final JsonNode reply = json(generateJson(PROMPT, "{\"temperature\": 0.3, \"top_p\": 0.8, \"max_tokens\": 2}"));
        assertEquals("Mock openai", reply.at("/choices/0/text").asText());
        final JsonNode body = lastRequestJson("openai");
        assertNumber(0.3, body, "temperature");
        assertNumber(0.8, body, "top_p");
        assertNumber(2, body, "max_tokens");
    }

    /**
     * generate_json returns the whole response document.
     *
     * @throws SQLException
     *             if the call fails
     */
    @Test
    @DisplayName("generate_json returns the full JSON response")
    void generateJsonReturnsFullResponse() throws SQLException {
        final JsonNode reply = json(generateJson(PROMPT, "{}"));
        assertEquals("text_completion", reply.path("object").asText());
        assertEquals(expectedReply(PROMPT), reply.at("/choices/0/text").asText());
        assertTrue(reply.at("/usage/total_tokens").asInt() > 0, "usage missing: " + reply);
    }

    @Test
    @DisplayName("generate_json returns NULL on an HTTP error")
    void generateJsonReturnsNullOnHttpError() throws SQLException {
        assertNull(generateJson("fail [[mock:status=500]]", "{}"));
    }

    @Test
    @DisplayName("generate signals SQLSTATE 38002 on an HTTP error")
    void httpErrorSignals38002() {
        final SQLException e = assertSqlState("38002", () -> generate("fail [[mock:status=503]]", "{}"));
        assertTrue(e.getMessage().contains("503"), "message should name the HTTP status: " + e.getMessage());
    }

    /**
     * The job settings build the URL, and the reply is choices[0].text.
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @DisplayName("generate calls {basepath}/completions and returns choices[0].text")
    void jobSettingsBuildTheUrl() throws Exception {
        assertEquals(expectedReply(PROMPT), generate(PROMPT, "{}"));
        final JsonNode request = lastRequest("openai");
        assertEquals("/v1/completions", request.path("path").asText());
        assertEquals("POST", request.path("method").asText());
    }

    /**
     * When the response has no choices[0].text, the function falls back to returning the whole body.
     *
     * @throws SQLException
     *             if the call fails
     */
    @Test
    @DisplayName("generate returns the full body when choices[0].text is missing")
    void malformedResponseReturnsFullBody() throws SQLException {
        final JsonNode reply = json(generate("x [[mock:malformed]]", "{}"));
        assertEquals("text_completion", reply.path("object").asText());
        assertFalse(reply.has("choices"));
    }

    @Test
    @DisplayName("max_tokens: a truncated reply reports finish_reason 'length'")
    void maxTokensReportsLengthFinishReason() throws SQLException {
        assertEquals("length", json(generateJson(PROMPT, "{\"max_tokens\": 3}")).at("/choices/0/finish_reason").asText());
    }

    /**
     * max_tokens limits the length of the reply.
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @DisplayName("max_tokens limits the reply")
    void maxTokensTruncatesReply() throws Exception {
        assertEquals("Mock openai response", generate(PROMPT, "{\"max_tokens\": 3}"));
        assertNumber(3, lastRequestJson("openai"), "max_tokens");
    }

    /**
     * model_id in the options replaces the job's model.
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @DisplayName("model_id option overrides the job's model")
    void modelIdOptionOverridesJobModel() throws Exception {
        assertEquals("Mock openai response from gpt-4o-mini: " + PROMPT, generate(PROMPT, "{\"model_id\": \"gpt-4o-mini\"}"));
        assertEquals("gpt-4o-mini", lastRequestJson("openai").path("model").asText());
    }

    @Test
    @DisplayName("n requests that many choices")
    void nRequestsMultipleChoices() throws Exception {
        assertEquals(3, json(generateJson(PROMPT, "{\"n\": 3}")).path("choices").size());
        assertNumber(3, lastRequestJson("openai"), "n");
    }

    /**
     * NULL options behave like empty options (from the former test/openai_compatible_test.sql).
     *
     * @throws Exception
     *             if a call or the mock inspection fails
     */
    @Test
    @DisplayName("NULL options send the documented defaults")
    void nullOptionsUseDefaults() throws Exception {
        assertEquals(expectedReply(PROMPT), generate(PROMPT, null));
        final JsonNode body = lastRequestJson("openai");
        assertEquals(MODEL, body.path("model").asText());
        assertNumber(256, body, "max_tokens");
        assertNumber(1, body, "temperature");
        assertEquals(expectedReply(PROMPT), json(generateJson(PROMPT, null)).at("/choices/0/text").asText());
    }

    /**
     * The prompt alone is enough; options default to '{}' (from the former test/openai_compatible_test.sql).
     *
     * @throws Exception
     *             if a call or the mock inspection fails
     */
    @Test
    @DisplayName("a prompt alone uses the default options")
    void promptOnlyUsesDefaults() throws Exception {
        final String prompt = "Tell me a story about a dragon.";
        assertEquals(expectedReply(prompt), clobValue("dbsdk_v1.openai_compatible_generate(" + STR + ")", prompt));
        assertNumber(256, lastRequestJson("openai"), "max_tokens");
        assertEquals(expectedReply(prompt), json(clobValue("dbsdk_v1.openai_compatible_generate_json(" + STR + ")", prompt)).at("/choices/0/text").asText());
    }

    /**
     * The less common options are forwarded when given.
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @DisplayName("seed, user, suffix and logprobs are sent")
    void seedUserSuffixAndLogprobsAreSent() throws Exception {
        generate(PROMPT, "{\"seed\": 42, \"user\": \"harness-user\", \"suffix\": \"!\", \"logprobs\": 2}");
        final JsonNode body = lastRequestJson("openai");
        assertNumber(42, body, "seed");
        assertEquals("harness-user", body.path("user").asText());
        assertEquals("!", body.path("suffix").asText());
        assertNumber(2, body, "logprobs");
    }

    /**
     * The stop sequence is sent and the reply ends before it.
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @DisplayName("stop ends the reply at the stop sequence")
    void stopSequenceIsSent() throws Exception {
        assertEquals("Mock openai", generate(PROMPT, "{\"stop\": \"response\"}").trim());
        assertEquals("response", lastRequestJson("openai").path("stop").asText());
    }

    /**
     * temperature and max_tokens together, as in the toolkit's documentation examples.
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @Tag(HarnessConfig.TAG_KNOWN_ISSUE)
    @DisplayName("temperature and max_tokens work together [known issue #52]")
    void temperatureAndMaxTokensTogether() throws Exception {
        assertEquals("Mock openai response from", generate(PROMPT, "{\"temperature\": 0.2, \"max_tokens\": 4}"));
        final JsonNode body = lastRequestJson("openai");
        assertNumber(0.2, body, "temperature");
        assertNumber(4, body, "max_tokens");
    }

    /**
     * temperature is forwarded, including zero (which must not fall back to the default of 1).
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @Tag(HarnessConfig.TAG_KNOWN_ISSUE)
    @DisplayName("temperature is sent, including 0 [known issue #52]")
    void temperatureIsSent() throws Exception {
        assertEquals(expectedReply(PROMPT), generate(PROMPT, "{\"temperature\": 0.7}"));
        assertNumber(0.7, lastRequestJson("openai"), "temperature");
        generate(PROMPT, "{\"temperature\": 0}");
        assertNumber(0, lastRequestJson("openai"), "temperature");
    }

    @Test
    @Tag(HarnessConfig.TAG_KNOWN_ISSUE)
    @DisplayName("an out-of-range temperature signals SQLSTATE 38002 (HTTP 400) [known issue #52]")
    void temperatureOutOfRangeSignals38002() {
        final SQLException e = assertSqlState("38002", () -> generate(PROMPT, "{\"temperature\": 2.5}"));
        assertTrue(e.getMessage().contains("400"), "message should name the HTTP status: " + e.getMessage());
    }

    @Test
    @Tag(HarnessConfig.TAG_KNOWN_ISSUE)
    @DisplayName("top_p is sent [known issue #52]")
    void topPIsSent() throws Exception {
        generate(PROMPT, "{\"top_p\": 0.5}");
        assertNumber(0.5, lastRequestJson("openai"), "top_p");
    }

    @Test
    @DisplayName("generate preserves non-ASCII text (CCSID 1208)")
    void unicodeRoundTrip() throws SQLException {
        final String prompt = "Caf\u00e9 \u00fcber \u65e5\u672c \u2713";
        assertEquals(expectedReply(prompt), generate(prompt, "{}"));
    }

    /**
     * stream, echo and best_of are documented but not supported: they must not be forwarded, and stream must not turn the response into an event stream.
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @DisplayName("stream, echo and best_of are not forwarded")
    void unsupportedOptionsAreNotForwarded() throws Exception {
        assertEquals(expectedReply(PROMPT), generate(PROMPT, "{\"stream\": true, \"echo\": true, \"best_of\": 3}"));
        final JsonNode body = lastRequestJson("openai");
        assertFalse(body.has("stream"), body.toString());
        assertFalse(body.has("echo"), body.toString());
        assertFalse(body.has("best_of"), body.toString());
    }
}
