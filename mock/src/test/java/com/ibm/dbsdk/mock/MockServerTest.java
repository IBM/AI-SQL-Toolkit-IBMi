package com.ibm.dbsdk.mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * End-to-end tests. Each request mirrors what the corresponding dbsdk_v1 SQL function sends.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MockServerTest {

    private static final String WATSONX_GENERATE = "/ml/v1/text/generation?version=2023-07-07";

    private TestClient m_client = null;
    private MockServer m_server = null;

    private static TestClient clientFor(final MockServer _server) {
        return new TestClient("http://127.0.0.1:" + _server.httpPorts().get(0));
    }

    /**
     * Start a throwaway server on an ephemeral port, for tests that need a non-default configuration.
     *
     * @param _config
     *            the configuration; the bind address and port are set here
     * @return the started server
     * @throws Exception
     *             if the server cannot start
     */
    private static MockServer startExtraServer(final MockConfig _config) throws Exception {
        final MockServer server = new MockServer(_config.setBindAddress("127.0.0.1").addHttpPort(0), null);
        server.start();
        return server;
    }

    @BeforeEach
    void reset() throws IOException {
        assertEquals(200, m_client.request("POST", "/mock/reset", "", null).getStatus());
    }

    /**
     * Start the shared server used by most tests.
     *
     * @throws Exception
     *             if the server cannot start
     */
    @BeforeAll
    void startServer() throws Exception {
        m_server = new MockServer(new MockConfig().setBindAddress("127.0.0.1").addHttpPort(0).setQuiet(true), null);
        m_server.start();
        m_client = clientFor(m_server);
    }

    @AfterAll
    void stopServer() {
        m_server.stop();
    }

    // ---------------------------------------------------------------------------------------------
    @Nested
    class Admin {

        @Test
        void delayDirective() throws IOException {
            final long start = System.currentTimeMillis();
            m_client.postJson("/services/T/B/X", "{\"text\":\"x [[mock:delay=300]]\"}");
            // Windows clock granularity is ~15 ms, so allow some slack below the requested delay
            assertTrue(System.currentTimeMillis() - start >= 250, "delay directive should slow the response");
        }

        @Test
        void healthAndUnknownRoutes() throws IOException {
            assertEquals("UP", m_client.get("/mock/health").json().get("status").asText());
            assertEquals(404, m_client.get("/definitely/not/here").getStatus());
        }

        /**
         * Credentials sent by the toolkit must be masked in the request history.
         *
         * @throws IOException
         *             if a request fails
         */
        @Test
        void recordingRedactsCredentials() throws IOException {
            m_client.postForm("/identity/token", "grant_type=urn:ibm:params:oauth:grant-type:apikey&apikey=super-secret-key", "Authorization", "Bearer also-secret");
            m_client.postJson("/services/T000/B000/webhook-secret", "{\"text\":\"hi\"}");
            final String recorded = m_client.get("/mock/requests").getBody();
            assertFalse(recorded.contains("super-secret-key"), recorded);
            assertFalse(recorded.contains("also-secret"), recorded);
            assertFalse(recorded.contains("webhook-secret"), recorded);
            assertFalse(recorded.contains("mock-watsonx-"), "issued tokens must be masked: " + recorded);
            final JsonNode iam = m_client.get("/mock/requests?service=watsonx").json().get(0);
            assertEquals("Bearer " + Redactor.MASK, iam.at("/headers/Authorization").asText());
            assertTrue(iam.get("body").asText().contains("apikey=" + Redactor.MASK));
        }

        /**
         * The request log supports service filtering, a limit, and clearing.
         *
         * @throws IOException
         *             if a request fails
         */
        @Test
        void requestLogFilterLimitAndClear() throws IOException {
            m_client.postJson("/services/T/B/X", "{\"text\":\"1\"}");
            m_client.postJson("/services/T/B/X", "{\"text\":\"2\"}");
            m_client.postJson("/api/generate", "{\"model\":\"m\",\"prompt\":\"p\",\"stream\":false}");
            final JsonNode all = m_client.get("/mock/requests").json();
            assertEquals(3, all.size(), all.toString());
            final JsonNode last = m_client.get("/mock/requests?service=slack&limit=1").json();
            assertEquals(1, last.size());
            assertEquals("2", last.at("/0/json/text").asText(), all.toString());
            assertEquals(200, last.at("/0/response/status").asInt());
            final String beforeClear = m_client.get("/mock/requests").getBody();
            assertEquals(3, m_client.delete("/mock/requests").json().get("cleared").asInt(), beforeClear);
            assertEquals(0, m_client.get("/mock/requests").json().size());
        }

        /**
         * POST /mock/shutdown stops the server.
         *
         * @throws Exception
         *             if the server or a request fails
         */
        @Test
        void shutdownEndpointStopsServer() throws Exception {
            final MockServer s = new MockServer(new MockConfig().setBindAddress("127.0.0.1").addHttpPort(0), null);
            s.start();
            final TestClient c = new TestClient("http://127.0.0.1:" + s.httpPorts().get(0));
            assertEquals(true, c.request("POST", "/mock/shutdown", "", null).json().get("shutdown").asBoolean());
            final long deadline = System.currentTimeMillis() + 10000;
            while (!s.httpPorts().isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            assertTrue(s.httpPorts().isEmpty(), "server should have stopped");
            s.awaitStop();
        }
    }

    // ---------------------------------------------------------------------------------------------
    @Nested
    class Kafka {

        @Test
        void emptyRecordsIs422() throws IOException {
            assertEquals(422, m_client.postJson("/topics/t", "{\"records\":[]}").getStatus());
        }

        /**
         * kafka_publish('{"order":1}', 'orders', 'k1') stores the record with increasing offsets.
         *
         * @throws IOException
         *             if a request fails
         */
        @Test
        void publishStoresRecord() throws IOException {
            final TestClient.Response r = m_client.postJson("/topics/orders", "{\"records\":[{\"key\":\"k1\",\"value\":\"{\\\"order\\\":1}\"}]}", "Content-Type", "application/vnd.kafka.json.v2+json",
                "Accept", "application/vnd.kafka.v2+json");
            assertEquals(200, r.getStatus(), r.getBody());
            assertEquals(0, r.json().at("/offsets/0/offset").asInt());
            assertEquals("k1", m_client.get("/mock/kafka").json().at("/orders/0/key").asText());
            assertEquals(1, m_client.postJson("/topics/orders", "{\"records\":[{\"key\":null,\"value\":\"x\"}]}").json().at("/offsets/0/offset").asInt());
        }

        @Test
        void recordErrorDirective() throws IOException {
            final JsonNode j = m_client.postJson("/topics/t", "{\"records\":[{\"key\":\"k\",\"value\":\"x [[mock:record-error]]\"}]}").json();
            assertTrue(j.at("/offsets/0/error").asText().contains("mock record failure"));
        }

        @Test
        void statusDirective() throws IOException {
            final TestClient.Response r = m_client.postJson("/topics/t", "{\"records\":[{\"key\":\"k\",\"value\":\"x [[mock:status=500]]\"}]}");
            assertEquals(500, r.getStatus());
            assertEquals(50001, r.json().get("error_code").asInt());
        }
    }

    // ---------------------------------------------------------------------------------------------
    @Nested
    class Ollama {

        /**
         * ollama_generate('Why is the sky blue?') sends {@code "stream": false} and reads {@code $.response}.
         *
         * @throws IOException
         *             if a request fails
         */
        @Test
        void generateNonStreaming() throws IOException {
            final TestClient.Response r = m_client.postJson("/api/generate", "{\"model\":\"granite3.2:8b\",\"prompt\":\"Why is the sky blue?\",\"stream\":false}");
            assertEquals(200, r.getStatus(), r.getBody());
            final JsonNode j = r.json();
            assertEquals("Mock ollama response from granite3.2:8b: Why is the sky blue?", j.get("response").asText());
            assertTrue(j.get("done").asBoolean());
        }

        /**
         * Without {@code "stream": false}, Ollama streams NDJSON ending with a {@code done: true} line.
         *
         * @throws IOException
         *             if a request fails
         */
        @Test
        void generateStreamingIsNdjson() throws IOException {
            final TestClient.Response r = m_client.postJson("/api/generate", "{\"model\":\"m\",\"prompt\":\"hi\"}");
            assertEquals(200, r.getStatus());
            final String[] lines = r.getBody().trim().split("\n");
            assertTrue(lines.length > 1);
            assertTrue(Json.mapper().readTree(lines[lines.length - 1]).get("done").asBoolean());
        }

        @Test
        void missingModelIs400() throws IOException {
            assertEquals(400, m_client.postJson("/api/generate", "{\"prompt\":\"x\",\"stream\":false}").getStatus());
        }

        @Test
        void statusDirective() throws IOException {
            final TestClient.Response r = m_client.postJson("/api/generate", "{\"model\":\"m\",\"prompt\":\"hi [[mock:status=404]]\",\"stream\":false}");
            assertEquals(404, r.getStatus());
            assertTrue(r.json().get("error").asText().contains("not found"));
        }

        @Test
        void tags() throws IOException {
            assertTrue(m_client.get("/api/tags").json().get("models").size() > 0);
        }
    }

    // ---------------------------------------------------------------------------------------------
    @Nested
    class OpenAiAuthAndGemini {

        /**
         * With --openai-api-key configured, requests need that exact bearer key.
         *
         * @throws Exception
         *             if the server or a request fails
         */
        @Test
        void apiKeyEnforcedWhenConfigured() throws Exception {
            final MockServer s = startExtraServer(new MockConfig().setOpenAiApiKey("sk-test"));
            try {
                final TestClient c = clientFor(s);
                final String body = "{\"model\":\"llama3\",\"prompt\":\"x\"}";
                assertEquals(401, c.postJson("/v1/completions", body).getStatus());
                assertEquals(401, c.postJson("/v1/completions", body, "Authorization", "Bearer wrong").getStatus());
                assertEquals(200, c.postJson("/v1/completions", body, "Authorization", "Bearer sk-test").getStatus());
            } finally {
                s.stop();
            }
        }

        /**
         * The gemini flavor rejects the legacy endpoint and unknown chat fields, but accepts a minimal chat request.
         *
         * @throws Exception
         *             if the server or a request fails
         */
        @Test
        void geminiFlavorRejectsLegacyAndUnknownFields() throws Exception {
            final MockServer s = startExtraServer(new MockConfig().setOpenAiFlavor(MockConfig.OpenAiFlavor.GEMINI));
            try {
                final TestClient c = clientFor(s);
                assertEquals(404, c.postJson("/v1beta/openai/completions", "{\"model\":\"g\",\"prompt\":\"x\"}").getStatus());
                final TestClient.Response bad = c.postJson("/v1beta/openai/chat/completions", "{\"model\":\"g\",\"logit_bias\":{},\"messages\":[{\"role\":\"user\",\"content\":\"x\"}]}");
                assertEquals(400, bad.getStatus());
                assertEquals("INVALID_ARGUMENT", bad.json().at("/0/error/status").asText());
                assertEquals(200, c.postJson("/v1beta/openai/chat/completions", "{\"model\":\"g\",\"temperature\":0.5,\"messages\":[{\"role\":\"user\",\"content\":\"x\"}]}").getStatus());
            } finally {
                s.stop();
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    @Nested
    class OpenAiCompatible {

        /**
         * openai_compatible_chat_generate('Hello') reads {@code $.choices[0].message.content}.
         *
         * @throws IOException
         *             if a request fails
         */
        @Test
        void chatCompletions() throws IOException {
            final String body = "{\"model\":\"gemini-2.0-flash\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}";
            final TestClient.Response r = m_client.postJson("/v1beta/openai/chat/completions", body);
            assertEquals(200, r.getStatus(), r.getBody());
            assertEquals("Mock chat response from gemini-2.0-flash: Hello", r.json().at("/choices/0/message/content").asText());
        }

        @Test
        void chatValidatesMessages() throws IOException {
            assertEquals(400, m_client.postJson("/v1/chat/completions", "{\"model\":\"m\",\"messages\":[]}").getStatus());
            assertEquals(400, m_client.postJson("/v1/chat/completions", "{\"model\":\"m\",\"messages\":[{\"role\":\"wizard\",\"content\":\"x\"}]}").getStatus());
        }

        @Test
        void invalidTemperatureIs400() throws IOException {
            assertEquals(400, m_client.postJson("/v1/completions", "{\"model\":\"llama3\",\"prompt\":\"x\",\"temperature\":5}").getStatus());
        }

        /**
         * openai_compatible_generate('why are armadillos so cute?', '{"max_tokens": 128, "temperature": 0.7}') reads {@code $.choices[0].text}.
         *
         * @throws IOException
         *             if a request fails
         */
        @Test
        void legacyCompletions() throws IOException {
            final String body = "{\"model\":\"llama3\",\"prompt\":\"why are armadillos so cute?\",\"max_tokens\":128,\"temperature\":0.7,\"top_p\":1,\"n\":1,\"presence_penalty\":0,\"frequency_penalty\":0}";
            final TestClient.Response r = m_client.postJson("/v1/completions", body);
            assertEquals(200, r.getStatus(), r.getBody());
            final JsonNode j = r.json();
            assertEquals("text_completion", j.get("object").asText());
            assertEquals("Mock openai response from llama3: why are armadillos so cute?", j.at("/choices/0/text").asText());
            assertTrue(j.at("/usage/total_tokens").asInt() > 0);
        }

        /**
         * Db2's JSON_UPDATE may serialize numbers as strings; they must still be accepted.
         *
         * @throws IOException
         *             if a request fails
         */
        @Test
        void legacyCompletionsAcceptsDb2StringNumbers() throws IOException {
            final String body = "{\"model\":\"llama3\",\"prompt\":\"x\",\"max_tokens\":\"256\",\"temperature\":\"1.0\",\"top_p\":\"1.0\",\"n\":\"2\",\"presence_penalty\":\"0.0\","
                + "\"frequency_penalty\":\"0.0\",\"seed\":\"7\"}";
            assertEquals(2, m_client.postJson("/v1/completions", body).json().get("choices").size());
        }

        /**
         * A malformed response lacks {@code choices}, which exercises the "return full response" fallback in the SQL functions.
         *
         * @throws IOException
         *             if a request fails
         */
        @Test
        void malformedDirectiveDropsChoices() throws IOException {
            final JsonNode j = m_client.postJson("/v1/completions", "{\"model\":\"llama3\",\"prompt\":\"x [[mock:malformed]]\"}").json();
            assertFalse(j.has("choices"));
        }

        @Test
        void maxTokensTruncatesWithLengthFinishReason() throws IOException {
            final JsonNode j = m_client.postJson("/v1/completions", "{\"model\":\"llama3\",\"prompt\":\"one two three\",\"max_tokens\":2}").json();
            assertEquals("Mock openai", j.at("/choices/0/text").asText());
            assertEquals("length", j.at("/choices/0/finish_reason").asText());
        }

        @Test
        void models() throws IOException {
            assertEquals("list", m_client.get("/v1/models").json().get("object").asText());
        }

        /**
         * Reply directives may contain JSON with brackets; status directives produce errors.
         *
         * @throws IOException
         *             if a request fails
         */
        @Test
        void replyAndStatusDirectives() throws IOException {
            final JsonNode j = m_client.postJson("/v1/completions", "{\"model\":\"llama3\",\"prompt\":\"[[mock:reply={\\\"names\\\":[\\\"a\\\"]}]]\"}").json();
            assertEquals("{\"names\":[\"a\"]}", j.at("/choices/0/text").asText());
            assertEquals(503, m_client.postJson("/v1/completions", "{\"model\":\"llama3\",\"prompt\":\"x [[mock:status=503]]\"}").getStatus());
        }

        @Test
        void sseStreaming() throws IOException {
            final TestClient.Response r = m_client.postJson("/v1/chat/completions", "{\"model\":\"m\",\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}");
            assertTrue(r.getBody().startsWith("data: "));
            assertTrue(r.getBody().trim().endsWith("data: [DONE]"));
        }
    }

    // ---------------------------------------------------------------------------------------------
    @Nested
    class Slack {

        private static final String HOOK = "/services/T000/B000/XXXX";

        /**
         * Slack's error responses are plain-text codes.
         *
         * @throws IOException
         *             if a request fails
         */
        @Test
        void errors() throws IOException {
            assertEquals("no_text", m_client.postJson(HOOK, "{\"text\":\"\"}").getBody());
            assertEquals("invalid_payload", m_client.postJson(HOOK, "not json").getBody());
            assertEquals(404, m_client.postJson("/services/T000", "{\"text\":\"x\"}").getStatus());
            final TestClient.Response r = m_client.postJson(HOOK, "{\"text\":\"x [[mock:status=403]]\"}");
            assertEquals(403, r.getStatus());
            assertEquals("invalid_token", r.getBody());
        }

        /**
         * slack_sendmessage('Hello from IBM i') posts {@code {"text": ...}} and gets {@code ok}.
         *
         * @throws IOException
         *             if a request fails
         */
        @Test
        void sendMessage() throws IOException {
            final TestClient.Response r = m_client.postJson(HOOK, "{\"text\":\"Hello from IBM i\"}");
            assertEquals(200, r.getStatus());
            assertEquals("ok", r.getBody());
            assertEquals("Hello from IBM i", m_client.get("/mock/requests?service=slack").json().at("/0/json/text").asText());
        }
    }

    // ---------------------------------------------------------------------------------------------
    @Nested
    class Twilio {

        private static final String SID = "ACmockAccountSid";
        private static final String URL = "/2010-04-01/Accounts/" + SID + "/Messages.json";

        /**
         * The Basic auth user must be the account SID from the path.
         *
         * @throws IOException
         *             if a request fails
         */
        @Test
        void authMustMatchAccountSid() throws IOException {
            final TestClient.Response r = m_client.postForm(URL, "To=1&From=2&Body=x", "Authorization", TestClient.basic("ACother", "token"));
            assertEquals(401, r.getStatus());
            assertEquals(20003, r.json().get("code").asInt());
            assertEquals(401, m_client.postForm(URL, "To=1&From=2&Body=x").getStatus());
        }

        /**
         * twilio_sendsms('5551234567', 'Hello there', '5557654321'). The SQL does not URL-encode the form body.
         *
         * @throws IOException
         *             if a request fails
         */
        @Test
        void sendSms() throws IOException {
            final TestClient.Response r = m_client.postForm(URL, "To=5551234567&From=5557654321&Body=Hello there", "Authorization", TestClient.basic(SID, "token"));
            assertEquals(201, r.getStatus(), r.getBody());
            final JsonNode j = r.json();
            assertEquals("queued", j.get("status").asText());
            assertEquals("Hello there", j.get("body").asText());
            assertTrue(j.get("sid").asText().startsWith("SM"));
        }

        /**
         * Missing To, From or Body produce Twilio's specific error codes.
         *
         * @throws IOException
         *             if a request fails
         */
        @Test
        void validation() throws IOException {
            final String auth = TestClient.basic(SID, "token");
            assertEquals(21604, m_client.postForm(URL, "From=2&Body=x", "Authorization", auth).json().get("code").asInt());
            assertEquals(21603, m_client.postForm(URL, "To=1&Body=x", "Authorization", auth).json().get("code").asInt());
            assertEquals(21602, m_client.postForm(URL, "To=1&From=2", "Authorization", auth).json().get("code").asInt());
            final TestClient.Response r = m_client.postForm(URL, "To=1&From=2&Body=x [[mock:status=400]]", "Authorization", auth);
            assertEquals(400, r.getStatus());
            assertNotNull(r.json().get("message"));
        }
    }

    // ---------------------------------------------------------------------------------------------
    @Nested
    class Watsonx {

        /**
         * Perform wx_authenticate()'s IAM request.
         *
         * @return the issued access token
         * @throws IOException
         *             if the request fails
         */
        private String authenticate() throws IOException {
            final TestClient.Response r = m_client.postForm("/identity/token", "grant_type=urn:ibm:params:oauth:grant-type:apikey&apikey=my-key", "Accept", "application/json");
            assertEquals(200, r.getStatus(), r.getBody());
            final JsonNode j = r.json();
            assertEquals(3600, j.get("expires_in").asInt());
            return j.get("access_token").asText();
        }

        @Test
        void authenticateRejectsBadKey() throws IOException {
            final TestClient.Response r = m_client.postForm("/identity/token", "grant_type=urn:ibm:params:oauth:grant-type:apikey&apikey=-BAD");
            assertEquals(400, r.getStatus());
            assertEquals("BXNIM0415E", r.json().get("errorCode").asText());
        }

        @Test
        void authenticateSucceeds() throws IOException {
            assertTrue(authenticate().startsWith("mock-watsonx-"));
        }

        /**
         * A token past its TTL is rejected with authentication_token_expired.
         *
         * @throws Exception
         *             if the server or a request fails
         */
        @Test
        void expiredTokenRejected() throws Exception {
            final MockServer s = startExtraServer(new MockConfig().setTokenTtlSeconds(1));
            try {
                final TestClient c = clientFor(s);
                final String token = c.postForm("/identity/token", "grant_type=urn:ibm:params:oauth:grant-type:apikey&apikey=k").json().get("access_token").asText();
                Thread.sleep(1100);
                final TestClient.Response r = c.postJson(WATSONX_GENERATE, "{\"model_id\":\"meta-llama/llama-2-13b-chat\",\"input\":\"x\",\"project_id\":\"p\"}", "Authorization", "Bearer " + token);
                assertEquals(401, r.getStatus());
                assertEquals("authentication_token_expired", r.json().at("/errors/0/code").asText());
            } finally {
                s.stop();
            }
        }

        /**
         * max_new_tokens truncates the reply and reports stop_reason max_tokens.
         *
         * @throws IOException
         *             if a request fails
         */
        @Test
        void generateHonoursMaxNewTokens() throws IOException {
            final String token = authenticate();
            final String body = "{\"model_id\":\"ibm/granite-13b-instruct-v2\",\"input\":\"a b c d e f\",\"parameters\":{\"max_new_tokens\":3},\"project_id\":\"proj\"}";
            final JsonNode j = m_client.postJson(WATSONX_GENERATE, body, "Authorization", "Bearer " + token).json();
            assertEquals("Mock watsonx response", j.at("/results/0/generated_text").asText());
            assertEquals("max_tokens", j.at("/results/0/stop_reason").asText());
        }

        /**
         * project_id and the version query parameter are both required.
         *
         * @throws IOException
         *             if a request fails
         */
        @Test
        void generateRequiresProjectIdAndVersion() throws IOException {
            final String token = authenticate();
            final String noProject = "{\"model_id\":\"meta-llama/llama-2-13b-chat\",\"input\":\"x\",\"project_id\":null}";
            assertEquals(400, m_client.postJson(WATSONX_GENERATE, noProject, "Authorization", "Bearer " + token).getStatus());
            final String ok = "{\"model_id\":\"meta-llama/llama-2-13b-chat\",\"input\":\"x\",\"project_id\":\"p\"}";
            assertEquals(400, m_client.postJson("/ml/v1/text/generation", ok, "Authorization", "Bearer " + token).getStatus());
        }

        @Test
        void generateRequiresToken() throws IOException {
            final TestClient.Response r = m_client.postJson(WATSONX_GENERATE, "{\"model_id\":\"meta-llama/llama-2-13b-chat\",\"input\":\"x\",\"project_id\":\"p\"}", "Authorization", "Bearer nope");
            assertEquals(401, r.getStatus());
            assertEquals("authentication_token_not_valid", r.json().at("/errors/0/code").asText());
        }

        /**
         * wx_generate('Hello world') reads {@code $.results[0].generated_text}.
         *
         * @throws IOException
         *             if a request fails
         */
        @Test
        void generateReturnsResultsGeneratedText() throws IOException {
            final String token = authenticate();
            final String body = "{\"model_id\":\"meta-llama/llama-2-13b-chat\",\"input\":\"Hello world\",\"parameters\":{\"max_new_tokens\":100,\"time_limit\":1000},\"project_id\":\"proj\"}";
            final TestClient.Response r = m_client.postJson(WATSONX_GENERATE, body, "Authorization", "Bearer " + token, "Accept", "application/json");
            assertEquals(200, r.getStatus(), r.getBody());
            assertEquals("Mock watsonx response from meta-llama/llama-2-13b-chat: Hello world", r.json().at("/results/0/generated_text").asText());
        }

        /**
         * wx_getmodels() needs no authentication and reads four columns from {@code $.resources[*]}.
         *
         * @throws IOException
         *             if a request fails
         */
        @Test
        void getModelsListsResources() throws IOException {
            final TestClient.Response r = m_client.get("/ml/v1/foundation_model_specs?version=2023-07-07");
            assertEquals(200, r.getStatus());
            final JsonNode first = r.json().at("/resources/0");
            assertNotNull(first.get("model_id"));
            assertNotNull(first.get("label"));
            assertNotNull(first.get("provider"));
            assertNotNull(first.get("short_description"));
        }

        @Test
        void unknownModelIs404() throws IOException {
            final String token = authenticate();
            final TestClient.Response r = m_client.postJson(WATSONX_GENERATE, "{\"model_id\":\"nope/nope\",\"input\":\"x\",\"project_id\":\"p\"}", "Authorization", "Bearer " + token);
            assertEquals(404, r.getStatus());
        }
    }
}
