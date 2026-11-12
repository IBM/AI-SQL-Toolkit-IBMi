package com.ibm.dbsdk.harness.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.ibm.dbsdk.harness.HarnessConfig;
import com.ibm.dbsdk.harness.MockClient;

/**
 * watsonx authentication, generation and model listing, with the IAM and watsonx.ai base URLs pointed at the mock server.
 */
@DisplayName("watsonx (service calls)")
@Tag("watsonx")
final class WatsonxServiceTest extends ToolkitTest {

    private static final String API_KEY = "harness-api-key";
    private static final String DEFAULT_MODEL = "meta-llama/llama-2-13b-chat";

    /**
     * Point this job's IAM and watsonx.ai base URLs at the mock, and configure region, API version, key and project.
     *
     * @throws SQLException
     *             if a call fails
     */
    @BeforeAll
    void configureJob() throws SQLException {
        call("wx_SetIamBaseUrlForJob(" + STR + ")", mockBaseUrl());
        call("wx_SetMlBaseUrlForJob(" + STR + ")", mockBaseUrl());
        db().execute("set dbsdk_v1.wx_region = 'us-south'");
        db().execute("set dbsdk_v1.wx_apiVersion = '2023-07-07'");
        call("wx_SetApiKeyForJob(" + STR + ")", API_KEY);
        call("wx_SetProjectIdForJob(" + STR + ")", "harness-project");
        call("wx_logoutJob()");
    }

    private String authenticate() throws SQLException {
        return value("dbsdk_v1.wx_authenticate()");
    }

    /**
     * authenticate gets a token from IAM, after which no new token is needed.
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @DisplayName("authenticate returns Y and stores a token")
    void authenticateSucceeds() throws Exception {
        call("wx_logoutJob()");
        assertEquals("Y", authenticate());
        assertEquals("N", value("dbsdk_v1.wx_ShouldGetNewToken()"));
        final Map<String, String> form = MockClient.formFields(lastRequest("watsonx"));
        assertEquals("urn:ibm:params:oauth:grant-type:apikey", form.get("grant_type"));
    }

    /**
     * An invalid key must not authenticate. Whether the function returns N or raises an error is not documented, so either is accepted.
     *
     * @throws SQLException
     *             if resetting the key fails
     */
    @Test
    @DisplayName("authenticate does not succeed with an invalid API key")
    void badApiKeyIsNotAuthenticated() throws SQLException {
        call("wx_logoutJob()");
        call("wx_SetApiKeyForJob(" + STR + ")", "-BAD");
        try {
            String result = null;
            try {
                result = authenticate();
            } catch (final SQLException _e) {
                result = "error: " + _e.getMessage();
            }
            assertFalse("Y".equals(result), "authentication with an invalid key returned Y");
        } finally {
            call("wx_SetApiKeyForJob(" + STR + ")", API_KEY);
            call("wx_logoutJob()");
        }
    }

    /**
     * generate sends model, input, parameters and project to /text/generation and returns results[0].generated_text.
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @DisplayName("generate returns results[0].generated_text")
    void generateReturnsGeneratedText() throws Exception {
        assertEquals("Y", authenticate());
        assertEquals("Mock watsonx response from " + DEFAULT_MODEL + ": Hello world", value("dbsdk_v1.wx_generate(" + STR + ")", "Hello world"));
        final JsonNode request = lastRequest("watsonx");
        assertEquals("/ml/v1/text/generation", request.path("path").asText());
        assertTrue(request.path("uri").asText().contains("version=2023-07-07"), request.path("uri").asText());
        final JsonNode body = request.get("json");
        assertEquals(DEFAULT_MODEL, body.path("model_id").asText());
        assertEquals("harness-project", body.path("project_id").asText());
        assertNumber(100, body.path("parameters"), "max_new_tokens");
    }

    /**
     * The model_id and parameters arguments are forwarded; max_new_tokens limits the reply.
     *
     * @throws Exception
     *             if the call or the mock inspection fails
     */
    @Test
    @DisplayName("generate uses the model_id and parameters arguments")
    void generateUsesModelAndParameters() throws Exception {
        assertEquals("Y", authenticate());
        final String reply = value("dbsdk_v1.wx_generate(" + STR + ", model_id => " + STR + ", parameters => dbsdk_v1.parameters(max_new_tokens => 3))", "Hi there", "ibm/granite-3-8b-instruct");
        assertEquals("Mock watsonx response", reply);
        assertEquals("ibm/granite-3-8b-instruct", lastRequestJson("watsonx").path("model_id").asText());
    }

    /**
     * With wx_apiVersion unset, generate should still work. GetUrl leaves out the required version parameter, so this fails until #49 is fixed.
     *
     * @throws SQLException
     *             if a call fails
     */
    @Test
    @Tag(HarnessConfig.TAG_KNOWN_ISSUE)
    @DisplayName("generate works without setting wx_apiVersion [known issue #49]")
    void generateWorksWithDefaultApiVersion() throws SQLException {
        assertEquals("Y", authenticate());
        db().execute("set dbsdk_v1.wx_apiVersion = NULL");
        try {
            assertTrue(value("dbsdk_v1.wx_generate(" + STR + ")", "Hello").startsWith("Mock watsonx response"));
        } finally {
            db().execute("set dbsdk_v1.wx_apiVersion = '2023-07-07'");
        }
    }

    /**
     * wx_getmodels returns a result set of model_id, label, provider and short_description.
     *
     * @throws SQLException
     *             if the call fails
     */
    @Test
    @DisplayName("getmodels returns the model list as a result set")
    void getModelsReturnsResultSet() throws SQLException {
        final List<Map<String, String>> rows = db().callForRows("call dbsdk_v1.wx_getmodels()");
        assertFalse(rows.isEmpty(), "no models returned");
        final Map<String, String> first = rows.get(0);
        for (final String column : new String[] { "MODEL_ID", "LABEL", "PROVIDER", "SHORT_DESCRIPTION" }) {
            assertTrue(first.containsKey(column), "missing column " + column + " in " + first.keySet());
        }
        boolean found = false;
        for (final Map<String, String> row : rows) {
            found = found || DEFAULT_MODEL.equals(row.get("MODEL_ID"));
        }
        assertTrue(found, "the default model should be listed");
    }

    @Test
    @DisplayName("generate with an unknown model signals SQLSTATE 38002")
    void unknownModelSignals38002() throws SQLException {
        assertEquals("Y", authenticate());
        assertSqlState("38002", () -> value("dbsdk_v1.wx_generate(" + STR + ", model_id => " + STR + ")", "Hi", "nobody/no-such-model"));
    }
}
