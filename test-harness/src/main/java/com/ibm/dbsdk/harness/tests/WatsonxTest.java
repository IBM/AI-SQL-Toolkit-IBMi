package com.ibm.dbsdk.harness.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.ibm.dbsdk.harness.HarnessConfig;

/**
 * The watsonx functions that work without the network: parameter building, URL building, service base URLs, API key and project settings, and the bearer-token lifecycle. See
 * {@link WatsonxServiceTest} for authentication and generation.
 */
@DisplayName("watsonx (local)")
@Tag("watsonx")
final class WatsonxTest extends ToolkitTest {

    /**
     * Skip the calling test if the user's CONF row sets a watsonx base URL, since the test checks the built-in defaults.
     *
     * @throws SQLException
     *             if the query fails
     */
    private void assumeNoConfBaseUrls() throws SQLException {
        final List<Map<String, String>> rows = db().queryRows("select watsonx_iam_baseurl, watsonx_ml_baseurl from dbsdk_v1.conf where usrprf = current_user");
        assumeTrue(rows.isEmpty() || (null == rows.get(0).get("WATSONX_IAM_BASEURL") && null == rows.get(0).get("WATSONX_ML_BASEURL")),
            "this user's CONF row sets a watsonx base URL, so the defaults cannot be observed");
    }

    /**
     * An explicit argument takes precedence over the job setting.
     *
     * @throws SQLException
     *             if a call fails
     */
    @Test
    @DisplayName("base URL getters prefer an explicit argument over the job setting")
    void baseUrlArgumentsOverrideJobSettings() throws SQLException {
        call("wx_SetIamBaseUrlForJob(" + STR + ")", "http://job-iam.example:8080");
        call("wx_SetMlBaseUrlForJob(" + STR + ")", "http://job-ml.example:8080");
        try {
            assertEquals("http://arg-iam.example", value("dbsdk_v1.wx_GetIamBaseUrl(" + STR + ")", "http://arg-iam.example"));
            assertEquals("http://arg-ml.example", value("dbsdk_v1.wx_GetMlBaseUrl(" + STR + ")", "http://arg-ml.example"));
        } finally {
            call("wx_SetIamBaseUrlForJob(" + STR + ")", (Object) null);
            call("wx_SetMlBaseUrlForJob(" + STR + ")", (Object) null);
        }
    }

    /**
     * With nothing configured, the base URLs are the IBM Cloud endpoints, the watsonx.ai one derived from the region.
     *
     * @throws SQLException
     *             if a call fails
     */
    @Test
    @DisplayName("base URLs default to IBM Cloud, using the region")
    void baseUrlDefaults() throws SQLException {
        assumeNoConfBaseUrls();
        db().execute("set dbsdk_v1.wx_region = 'eu-gb'");
        assertEquals("https://iam.cloud.ibm.com", value("dbsdk_v1.wx_GetIamBaseUrl()"));
        assertEquals("https://eu-gb.ml.cloud.ibm.com", value("dbsdk_v1.wx_GetMlBaseUrl()"));
    }

    /**
     * Job settings are used, trailing slashes are removed, and an empty value falls back to the default.
     *
     * @throws SQLException
     *             if a call fails
     */
    @Test
    @DisplayName("base URL job settings are used; '/' is trimmed and '' means the default")
    void baseUrlJobSettings() throws SQLException {
        call("wx_SetIamBaseUrlForJob(" + STR + ")", "http://job-iam.example:8080/");
        call("wx_SetMlBaseUrlForJob(" + STR + ")", "http://job-ml.example:8080/");
        try {
            assertEquals("http://job-iam.example:8080", value("dbsdk_v1.wx_GetIamBaseUrl()"));
            assertEquals("http://job-ml.example:8080", value("dbsdk_v1.wx_GetMlBaseUrl()"));
            assumeNoConfBaseUrls();
            call("wx_SetIamBaseUrlForJob(" + STR + ")", "");
            assertEquals("https://iam.cloud.ibm.com", value("dbsdk_v1.wx_GetIamBaseUrl()"));
        } finally {
            call("wx_SetIamBaseUrlForJob(" + STR + ")", (Object) null);
            call("wx_SetMlBaseUrlForJob(" + STR + ")", (Object) null);
        }
    }

    /**
     * With a region and an API version set and no base URL configured, GetUrl builds the regional endpoint with the version query parameter.
     *
     * @throws SQLException
     *             if a call fails
     */
    @Test
    @DisplayName("GetUrl builds the regional URL with the API version")
    void getUrlIncludesRegionAndVersion() throws SQLException {
        assumeNoConfBaseUrls();
        db().execute("set dbsdk_v1.wx_region = 'eu-de'");
        db().execute("set dbsdk_v1.wx_apiVersion = '2023-07-07'");
        assertEquals("https://eu-de.ml.cloud.ibm.com/ml/v1/text/generation?version=2023-07-07", value("dbsdk_v1.geturl(" + STR + ")", "/text/generation"));
    }

    /**
     * A configured watsonx.ai base URL replaces the regional endpoint in GetUrl.
     *
     * @throws SQLException
     *             if a call fails
     */
    @Test
    @DisplayName("GetUrl uses the configured watsonx.ai base URL")
    void getUrlUsesMlBaseUrl() throws SQLException {
        db().execute("set dbsdk_v1.wx_apiVersion = '2023-07-07'");
        call("wx_SetMlBaseUrlForJob(" + STR + ")", "http://mock.example:9000");
        try {
            assertEquals("http://mock.example:9000/ml/v1/text/generation?version=2023-07-07", value("dbsdk_v1.geturl(" + STR + ")", "/text/generation"));
        } finally {
            call("wx_SetMlBaseUrlForJob(" + STR + ")", (Object) null);
        }
    }

    /**
     * The watsonx API requires the version parameter, so GetUrl must supply one even when wx_apiVersion was never set.
     *
     * @throws SQLException
     *             if a call fails
     */
    @Test
    @Tag(HarnessConfig.TAG_KNOWN_ISSUE)
    @DisplayName("GetUrl includes a version when wx_apiVersion is not set [known issue #49]")
    void getUrlIncludesVersionByDefault() throws SQLException {
        db().execute("set dbsdk_v1.wx_region = 'us-south'");
        db().execute("set dbsdk_v1.wx_apiVersion = NULL");
        final String url = value("dbsdk_v1.geturl(" + STR + ")", "/text/generation");
        assertTrue(url.contains("version="), "watsonx requires ?version=, got " + url);
    }

    @Test
    @DisplayName("generate without a token signals SQLSTATE 38001")
    void generateWithoutTokenSignals38001() throws SQLException {
        call("wx_logoutJob()");
        assertSqlState("38001", () -> value("dbsdk_v1.wx_generate(" + STR + ")", "Hello"));
    }

    /**
     * Decimal parameters must come out as valid JSON numbers (e.g. 0.5, not .5).
     *
     * @throws SQLException
     *             if the call fails
     */
    @Test
    @DisplayName("parameters() writes decimal values as valid JSON numbers")
    void parametersDecimalsAreValidJson() throws SQLException {
        final JsonNode params = json(value("dbsdk_v1.parameters(temperature => 0.5, top_p => 0.9, repetition_penalty => 0.1)"));
        assertNumber(0.5, params, "temperature");
        assertNumber(0.9, params, "top_p");
        assertNumber(0.1, params, "repetition_penalty");
    }

    /**
     * parameters() includes exactly the arguments given.
     *
     * @throws SQLException
     *             if the call fails
     */
    @Test
    @DisplayName("parameters() builds JSON from the given arguments")
    void parametersIncludeGivenArguments() throws SQLException {
        final JsonNode params = json(value("dbsdk_v1.parameters(decoding_method => 'greedy', max_new_tokens => 100, time_limit => 1000, random_seed => 7)"));
        assertEquals("greedy", params.path("decoding_method").asText());
        assertNumber(100, params, "max_new_tokens");
        assertNumber(1000, params, "time_limit");
        assertNumber(7, params, "random_seed");
        assertEquals(4, params.size(), params.toString());
    }

    @Test
    @DisplayName("parameters() with no arguments is an empty object")
    void parametersOmitNulls() throws SQLException {
        assertEquals(0, json(value("dbsdk_v1.parameters()")).size());
    }

    /**
     * The API key and project ID setters write the job's global variables.
     *
     * @throws SQLException
     *             if a call fails
     */
    @Test
    @DisplayName("SetApiKeyForJob and SetProjectIdForJob set the job's values")
    void setApiKeyAndProjectId() throws SQLException {
        call("wx_SetApiKeyForJob(" + STR + ")", "harness-api-key");
        call("wx_SetProjectIdForJob(" + STR + ")", "harness-project");
        assertEquals("harness-api-key", value("dbsdk_v1.wx_apikey"));
        assertEquals("harness-project", value("dbsdk_v1.wx_projectid"));
    }

    /**
     * ShouldGetNewToken reflects logout, a fresh token, and a token inside the 60-second safety margin.
     *
     * @throws SQLException
     *             if a call fails
     */
    @Test
    @DisplayName("ShouldGetNewToken follows the bearer-token lifecycle")
    void tokenLifecycle() throws SQLException {
        call("wx_logoutJob()");
        assertEquals("Y", value("dbsdk_v1.wx_ShouldGetNewToken()"), "after logout");
        call("wx_SetBearerTokenForJob(" + STR + ", " + INT + ")", "harness-token", Integer.valueOf(3600));
        assertEquals("N", value("dbsdk_v1.wx_ShouldGetNewToken()"), "with a token valid for an hour");
        assertEquals("harness-token", value("dbsdk_v1.wx_JobBearerToken"));
        call("wx_SetBearerTokenForJob(" + STR + ", " + INT + ")", "short-token", Integer.valueOf(30));
        assertEquals("Y", value("dbsdk_v1.wx_ShouldGetNewToken()"), "a token expiring within the 60-second margin");
        call("wx_logoutJob()");
        assertEquals("Y", value("dbsdk_v1.wx_ShouldGetNewToken()"), "after logging out again");
    }
}
