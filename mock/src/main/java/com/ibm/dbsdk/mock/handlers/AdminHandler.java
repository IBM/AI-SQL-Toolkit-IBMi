package com.ibm.dbsdk.mock.handlers;

import java.io.IOException;
import java.util.Locale;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ibm.dbsdk.mock.HttpCall;
import com.ibm.dbsdk.mock.Json;
import com.ibm.dbsdk.mock.MockServer;
import com.ibm.dbsdk.mock.MockState;

/**
 * Inspection and control endpoints for test harnesses. Requests to these endpoints are not recorded.
 * <ul>
 * <li>{@code GET    /} or {@code /mock/health} server info and endpoint catalogue</li>
 * <li>{@code GET    /mock/requests[?service=slack][&limit=10]} recorded requests</li>
 * <li>{@code DELETE /mock/requests} clear recorded requests</li>
 * <li>{@code GET    /mock/kafka} messages published per topic</li>
 * <li>{@code POST   /mock/reset} clear requests, tokens and Kafka topics</li>
 * </ul>
 */
public final class AdminHandler extends ServiceHandler {

    public AdminHandler(final MockState _state) {
        super(_state);
    }

    /**
     * Send a 400 response with a JSON error message.
     *
     * @param _call
     *            the call to answer
     * @param _message
     *            the error message
     * @throws IOException
     *             if the response cannot be written
     */
    private static void badRequest(final HttpCall _call, final String _message) throws IOException {
        final ObjectNode err = Json.object();
        err.put("error", _message);
        _call.sendJson(400, err);
    }

    /**
     * Append one entry to the endpoint catalogue.
     *
     * @param _endpoints
     *            the catalogue
     * @param _service
     *            the service name
     * @param _method
     *            the HTTP method(s)
     * @param _path
     *            the path pattern
     * @param _description
     *            what the endpoint does
     */
    private static void endpoint(final ArrayNode _endpoints, final String _service, final String _method, final String _path, final String _description) {
        final ObjectNode o = _endpoints.addObject();
        o.put("service", _service);
        o.put("method", _method);
        o.put("path", _path);
        o.put("description", _description);
    }

    @Override
    public String getServiceName() {
        return "admin";
    }

    /**
     * Dispatch an admin request.
     *
     * @param _call
     *            the call to handle
     * @throws IOException
     *             if the response cannot be written
     */
    @Override
    public void handle(final HttpCall _call) throws IOException {
        final String path = _call.getPath();
        if ("/".equals(path) || "/mock".equals(path) || "/mock/health".equals(path)) {
            _call.sendJson(200, health());
        } else if ("/mock/requests".equals(path)) {
            requests(_call);
        } else if ("/mock/kafka".equals(path)) {
            _call.sendJson(200, m_state.kafkaSnapshot());
        } else if ("/mock/reset".equals(path)) {
            if (!"POST".equals(_call.getMethod())) {
                badRequest(_call, "use POST /mock/reset");
                return;
            }
            m_state.reset();
            final ObjectNode r = Json.object();
            r.put("reset", true);
            _call.sendJson(200, r);
        } else {
            final ObjectNode err = Json.object();
            err.put("error", "Unknown admin endpoint " + path);
            _call.sendJson(404, err);
        }
    }

    /**
     * Build the health document, including the endpoint catalogue.
     *
     * @return the health document
     */
    private ObjectNode health() {
        final ObjectNode h = Json.object();
        h.put("status", "UP");
        h.put("server", "dbsdk-mock-server");
        h.put("version", MockServer.version());
        h.put("startedAt", m_state.getStartedAt().toString());
        h.put("openaiFlavor", m_config.getOpenAiFlavor().name().toLowerCase(Locale.ROOT));
        h.put("tokenTtlSeconds", m_config.getTokenTtlSeconds());
        final ArrayNode e = h.putArray("endpoints");
        endpoint(e, "watsonx", "POST", "/identity/token", "IBM Cloud IAM API-key token (wx_authenticate)");
        endpoint(e, "watsonx", "POST", "/ml/v1/text/generation?version=...", "Text generation (wx_generate)");
        endpoint(e, "watsonx", "GET", "/ml/v1/foundation_model_specs", "Model list (wx_getmodels)");
        endpoint(e, "ollama", "POST", "/api/generate", "Generate (ollama_generate)");
        endpoint(e, "ollama", "GET", "/api/tags", "Installed models");
        endpoint(e, "openai", "POST", "{basepath}/completions", "Legacy completions (openai_compatible_generate[_json])");
        endpoint(e, "openai", "POST", "{basepath}/chat/completions", "Chat completions (openai_compatible_chat_generate)");
        endpoint(e, "openai", "GET", "{basepath}/models", "Model list");
        endpoint(e, "kafka", "POST", "/topics/{topic}", "REST Proxy produce (kafka_publish)");
        endpoint(e, "slack", "POST", "/services/{T}/{B}/{X}", "Incoming webhook (slack_sendmessage)");
        endpoint(e, "twilio", "POST", "/2010-04-01/Accounts/{sid}/Messages.json", "Send SMS (twilio_sendsms)");
        endpoint(e, "admin", "GET|DELETE", "/mock/requests", "Recorded requests (?service=&limit=)");
        endpoint(e, "admin", "GET", "/mock/kafka", "Published Kafka records");
        endpoint(e, "admin", "POST", "/mock/reset", "Clear all state");
        return h;
    }

    /**
     * List ({@code GET}) or clear ({@code DELETE}) the recorded requests.
     *
     * @param _call
     *            the call to handle
     * @throws IOException
     *             if the response cannot be written
     */
    private void requests(final HttpCall _call) throws IOException {
        if ("DELETE".equals(_call.getMethod())) {
            final ObjectNode r = Json.object();
            r.put("cleared", m_state.clearRequests());
            _call.sendJson(200, r);
            return;
        }
        final String limitText = _call.getQuery().get("limit");
        Integer limit = null;
        if (null != limitText) {
            try {
                limit = Integer.valueOf(Math.max(0, Integer.parseInt(limitText.trim())));
            } catch (final NumberFormatException _e) {
                badRequest(_call, "limit must be an integer");
                return;
            }
        }
        _call.sendJson(200, m_state.requests(_call.getQuery().get("service"), limit));
    }
}
