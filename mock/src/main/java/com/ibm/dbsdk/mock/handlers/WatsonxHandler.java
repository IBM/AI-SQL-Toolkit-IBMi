package com.ibm.dbsdk.mock.handlers;

import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ibm.dbsdk.mock.Directives;
import com.ibm.dbsdk.mock.HttpCall;
import com.ibm.dbsdk.mock.Json;
import com.ibm.dbsdk.mock.MockState;
import com.ibm.dbsdk.mock.TextGen;

/**
 * Emulates the IBM Cloud / watsonx.ai endpoints used by {@code wx_authenticate}, {@code wx_generate} and {@code wx_getmodels}:
 * <ul>
 * <li>{@code POST /identity/token} (iam.cloud.ibm.com)</li>
 * <li>{@code POST /ml/v1/text/generation?version=...} ({region}.ml.cloud.ibm.com)</li>
 * <li>{@code GET  /ml/v1/foundation_model_specs?version=...}</li>
 * </ul>
 */
public final class WatsonxHandler extends ServiceHandler {

    static final String APIKEY_GRANT = "urn:ibm:params:oauth:grant-type:apikey";

    /** watsonx's own default when {@code parameters.max_new_tokens} is not given. */
    private static final int DEFAULT_MAX_NEW_TOKENS = 20;

    /** Rows of model_id, label, provider, short_description. */
    private static final String[][] s_models = {
        { "meta-llama/llama-2-13b-chat", "llama-2-13b-chat", "Meta", "Llama-2-13b-chat is an auto-regressive language model that uses an optimized transformer architecture." },
        { "meta-llama/llama-3-3-70b-instruct", "llama-3-3-70b-instruct", "Meta", "A state-of-the-art refresh of the Llama 3.1 70B Instruct model." },
        { "ibm/granite-13b-instruct-v2", "granite-13b-instruct-v2", "IBM", "The Granite model series is a family of IBM-trained, dense decoder-only models." },
        { "ibm/granite-3-8b-instruct", "granite-3-8b-instruct", "IBM", "Granite 3.0 8B Instruct is a lightweight, open-source instruct model from IBM." },
        { "mistralai/mistral-large", "mistral-large", "Mistral AI", "Mistral Large is Mistral AI's flagship model with top-tier reasoning capabilities." } };

    public WatsonxHandler(final MockState _state) {
        super(_state);
    }

    /**
     * Send an IAM-style error ({@code errorCode} / {@code errorMessage}).
     *
     * @param _call
     *            the call to answer
     * @param _status
     *            the HTTP status
     * @param _code
     *            the IAM error code, e.g. BXNIM0415E
     * @param _message
     *            the error message
     * @throws IOException
     *             if the response cannot be written
     */
    private static void iamError(final HttpCall _call, final int _status, final String _code, final String _message) throws IOException {
        final ObjectNode err = Json.object();
        err.put("errorCode", _code);
        err.put("errorMessage", _message);
        final ObjectNode ctx = err.putObject("context");
        ctx.put("requestId", randomHex(32));
        ctx.put("requestType", "incoming.Identity_Token");
        _call.sendJson(_status, err);
    }

    /**
     * @param _modelId
     *            a model id
     * @return true if the model is in the mock's model list
     */
    private static boolean isKnownModel(final String _modelId) {
        for (final String[] model : s_models) {
            if (model[0].equals(_modelId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Send a watsonx.ai-style error ({@code errors[]}, {@code trace}, {@code status_code}).
     *
     * @param _call
     *            the call to answer
     * @param _status
     *            the HTTP status
     * @param _code
     *            the watsonx error code
     * @param _message
     *            the error message
     * @throws IOException
     *             if the response cannot be written
     */
    private static void wxError(final HttpCall _call, final int _status, final String _code, final String _message) throws IOException {
        final ObjectNode err = Json.object();
        final ObjectNode e = err.putArray("errors").addObject();
        e.put("code", _code);
        e.put("message", _message);
        e.put("more_info", "https://cloud.ibm.com/apidocs/watsonx-ai");
        err.put("trace", randomHex(32));
        err.put("status_code", _status);
        _call.sendJson(_status, err);
    }

    /**
     * Handle {@code POST /ml/v1/text/generation}. Validates the version, bearer token and required body fields in that order, like the real service.
     *
     * @param _call
     *            the call to handle
     * @throws IOException
     *             if the response cannot be written
     */
    private void generate(final HttpCall _call) throws IOException {
        final JsonNode body = _call.getJson();
        final String input = Json.text(body, "input");
        final Directives directives = Directives.parse(input);
        directives.applyDelay();

        if (isBlank(_call.getQuery().get("version"))) {
            wxError(_call, 400, "invalid_request_entity", "Missing query parameter 'version'. Set dbsdk_v1.wx_apiVersion, e.g. '2023-07-07'.");
            return;
        }
        if (!isAuthorized(_call)) {
            return;
        }
        if (null == body || !body.isObject()) {
            wxError(_call, 400, "json_validation_error", "Request body must be a JSON object.");
            return;
        }
        final String modelId = Json.text(body, "model_id");
        if (isBlank(modelId)) {
            wxError(_call, 400, "json_validation_error", "Json field validation failed: model_id is required.");
            return;
        }
        if (null == input) {
            wxError(_call, 400, "json_validation_error", "Json field validation failed: input is required.");
            return;
        }
        if (isBlank(Json.text(body, "project_id")) && isBlank(Json.text(body, "space_id"))) {
            wxError(_call, 400, "json_validation_error", "Either project_id or space_id must be specified. Call dbsdk_v1.wx_setprojectidforjob first.");
            return;
        }
        if (!isKnownModel(modelId)) {
            wxError(_call, 404, "model_not_supported", "Model '" + modelId + "' is not supported.");
            return;
        }
        if (directives.isErrorRequested()) {
            wxError(_call, directives.getStatus().intValue(), "mock_error", "Mock error requested via directive.");
            return;
        }
        sendGeneration(_call, modelId, input, body.get("parameters"), directives);
    }

    @Override
    public String getServiceName() {
        return "watsonx";
    }

    /**
     * Route a watsonx request by path and method.
     *
     * @param _call
     *            the call to handle
     * @throws IOException
     *             if the response cannot be written
     */
    @Override
    public void handle(final HttpCall _call) throws IOException {
        final String path = _call.getPath();
        final String method = _call.getMethod();
        if ("/identity/token".equals(path)) {
            if ("POST".equals(method)) {
                token(_call);
            } else {
                wxError(_call, 405, "method_not_allowed", "Use POST for " + path);
            }
        } else if ("/ml/v1/text/generation".equals(path)) {
            if ("POST".equals(method)) {
                generate(_call);
            } else {
                wxError(_call, 405, "method_not_allowed", "Use POST for " + path);
            }
        } else if ("/ml/v1/foundation_model_specs".equals(path)) {
            if ("GET".equals(method) || "HEAD".equals(method)) {
                models(_call);
            } else {
                wxError(_call, 405, "method_not_allowed", "Use GET for " + path);
            }
        } else {
            wxError(_call, 404, "not_found", "The mock does not implement " + path);
        }
    }

    /**
     * Without a configured key every non-empty key is accepted, except ones that are obviously meant to be invalid (the toolkit's own tests use "-BAD").
     *
     * @param _apiKey
     *            the key presented by the client
     * @return true if IAM should issue a token for it
     */
    private boolean isApiKeyAccepted(final String _apiKey) {
        if (null != m_config.getWatsonxApiKey()) {
            return m_config.getWatsonxApiKey().equals(_apiKey);
        }
        final String key = _apiKey.trim().toLowerCase(Locale.ROOT);
        return !(key.startsWith("bad") || key.startsWith("-bad") || key.startsWith("invalid"));
    }

    /**
     * Check the bearer token, sending the matching 401 error if it is missing, unknown or expired.
     *
     * @param _call
     *            the call to check
     * @return true if the call may proceed
     * @throws IOException
     *             if an error response cannot be written
     */
    private boolean isAuthorized(final HttpCall _call) throws IOException {
        final String token = _call.getBearerToken();
        if (null == token) {
            wxError(_call, 401, "authorization_rejected", "Missing Authorization header with Bearer token.");
            return false;
        }
        final MockState.TokenStatus status = m_state.checkToken(token);
        if (MockState.TokenStatus.EXPIRED == status) {
            wxError(_call, 401, "authentication_token_expired", "Failed to authenticate the request due to an expired token.");
            return false;
        }
        if (MockState.TokenStatus.VALID != status) {
            wxError(_call, 401, "authentication_token_not_valid", "Failed to authenticate the request due to invalid token.");
            return false;
        }
        return true;
    }

    /**
     * Handle {@code GET /ml/v1/foundation_model_specs}. No authentication is required, matching the toolkit's documentation.
     *
     * @param _call
     *            the call to handle
     * @throws IOException
     *             if the response cannot be written
     */
    private void models(final HttpCall _call) throws IOException {
        final ObjectNode resp = Json.object();
        resp.put("total_count", s_models.length);
        resp.put("limit", 100);
        resp.putObject("first").put("href", "https://us-south.ml.cloud.ibm.com/ml/v1/foundation_model_specs?limit=100");
        final ArrayNode resources = resp.putArray("resources");
        for (final String[] model : s_models) {
            final ObjectNode r = resources.addObject();
            r.put("model_id", model[0]);
            r.put("label", model[1]);
            r.put("provider", model[2]);
            r.put("source", model[2]);
            r.put("short_description", model[3]);
            r.putArray("functions").addObject().put("id", "text_generation");
            r.put("tier", "class_2");
            r.put("number_params", "unknown");
        }
        _call.sendJson(200, resp);
    }

    /**
     * Send a successful text-generation response.
     *
     * @param _call
     *            the call to answer
     * @param _modelId
     *            the requested model
     * @param _input
     *            the prompt
     * @param _parameters
     *            the request's {@code parameters} object; may be null
     * @param _directives
     *            the directives found in the prompt
     * @throws IOException
     *             if the response cannot be written
     */
    private void sendGeneration(final HttpCall _call, final String _modelId, final String _input, final JsonNode _parameters, final Directives _directives) throws IOException {
        final Integer requestedMax = Json.integer(_parameters, "max_new_tokens");
        final Integer maxNewTokens = null == requestedMax ? Integer.valueOf(DEFAULT_MAX_NEW_TOKENS) : requestedMax;
        final TextGen.Result result = TextGen.generate("watsonx", _modelId, _input, _directives, maxNewTokens);

        final ObjectNode resp = Json.object();
        resp.put("model_id", _modelId);
        resp.put("created_at", Instant.now().toString());
        if (_directives.isMalformed()) {
            resp.put("note", "malformed response requested via directive");
            _call.sendJson(200, resp);
            return;
        }
        final ObjectNode r = resp.putArray("results").addObject();
        r.put("generated_text", result.getText());
        r.put("generated_token_count", result.getTokens());
        r.put("input_token_count", TextGen.countTokens(_input));
        r.put("stop_reason", result.isTruncated() ? "max_tokens" : "eos_token");
        final ObjectNode warning = resp.putObject("system").putArray("warnings").addObject();
        warning.put("message", "This response was produced by the dbsdk mock server.");
        warning.put("id", "mock_server");
        _call.sendJson(200, resp);
    }

    /**
     * Handle {@code POST /identity/token}: the IBM Cloud IAM API-key grant.
     *
     * @param _call
     *            the call to handle
     * @throws IOException
     *             if the response cannot be written
     */
    private void token(final HttpCall _call) throws IOException {
        final Map<String, String> form = _call.getForm();
        final String apiKey = form.get("apikey");
        final Directives directives = Directives.parse(apiKey);
        directives.applyDelay();

        if (directives.isErrorRequested()) {
            iamError(_call, directives.getStatus().intValue(), "BXNIM0000E", "Mock error requested via directive.");
            return;
        }
        final String grant = form.get("grant_type");
        if (isBlank(grant)) {
            iamError(_call, 400, "BXNIM0003E", "Property missing or empty. Property name: grant_type");
            return;
        }
        if (!APIKEY_GRANT.equals(grant)) {
            iamError(_call, 400, "BXNIM0109E", "Property grant_type has unsupported value: " + grant);
            return;
        }
        if (isBlank(apiKey) || !isApiKeyAccepted(apiKey)) {
            iamError(_call, 400, "BXNIM0415E", "Provided API key could not be found.");
            return;
        }
        final ObjectNode resp = Json.object();
        resp.put("access_token", m_state.issueToken());
        resp.put("refresh_token", "not_supported");
        resp.put("token_type", "Bearer");
        resp.put("expires_in", m_config.getTokenTtlSeconds());
        resp.put("expiration", nowEpochSeconds() + m_config.getTokenTtlSeconds());
        resp.put("scope", "ibm openid");
        _call.sendJson(200, resp);
    }
}
