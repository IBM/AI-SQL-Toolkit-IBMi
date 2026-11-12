package com.ibm.dbsdk.mock;

import java.io.IOException;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ibm.dbsdk.mock.handlers.AdminHandler;
import com.ibm.dbsdk.mock.handlers.KafkaHandler;
import com.ibm.dbsdk.mock.handlers.OllamaHandler;
import com.ibm.dbsdk.mock.handlers.OpenAiHandler;
import com.ibm.dbsdk.mock.handlers.ServiceHandler;
import com.ibm.dbsdk.mock.handlers.SlackHandler;
import com.ibm.dbsdk.mock.handlers.TwilioHandler;
import com.ibm.dbsdk.mock.handlers.WatsonxHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Single entry point for all listeners. Routes by URL path so that one port can serve every service the toolkit talks to (their URL spaces do not overlap).
 */
final class Router implements HttpHandler {

    private final AdminHandler m_admin;
    private final KafkaHandler m_kafka;
    private final OllamaHandler m_ollama;
    private final OpenAiHandler m_openAi;
    private final SlackHandler m_slack;
    private final MockState m_state;
    private final TwilioHandler m_twilio;
    private final WatsonxHandler m_watsonx;

    /**
     * Create the router and one handler per emulated service, all sharing the given state.
     *
     * @param _state
     *            the shared server state
     */
    Router(final MockState _state) {
        m_state = _state;
        m_admin = new AdminHandler(_state);
        m_kafka = new KafkaHandler(_state);
        m_ollama = new OllamaHandler(_state);
        m_openAi = new OpenAiHandler(_state);
        m_slack = new SlackHandler(_state);
        m_twilio = new TwilioHandler(_state);
        m_watsonx = new WatsonxHandler(_state);
    }

    /**
     * Dispatch one request to its service handler, then record and log it. This is the request boundary: any failure is converted into a 500 response.
     *
     * @param _exchange
     *            the exchange to handle
     */
    @Override
    public void handle(final HttpExchange _exchange) {
        final long start = System.currentTimeMillis();
        final HttpCall call;
        try {
            call = new HttpCall(_exchange);
        } catch (final IOException _e) {
            MockLog.warn("Could not read request " + _exchange.getRequestMethod() + " " + Redactor.redactPath(_exchange.getRequestURI().getRawPath()), _e);
            _exchange.close();
            return;
        }
        try {
            dispatch(call);
        } catch (final Exception _e) {
            MockLog.error("Mock handler failed for " + call.getMethod() + " " + Redactor.redactPath(call.getPath()), _e);
            sendInternalError(call, "Mock server internal error: " + _e.getClass().getName());
        } finally {
            final long elapsed = System.currentTimeMillis() - start;
            if (!"admin".equals(call.getServiceName())) {
                m_state.record(call, elapsed);
            }
            MockLog.info(String.format("%-8s %3d %-6s %s (%d ms)", call.getServiceName(), Integer.valueOf(call.getResponseStatus()), call.getMethod(), Redactor.redactPath(call.getRawUri()),
                Long.valueOf(elapsed)));
        }
    }

    /**
     * Route a call to its handler, or answer 404 if no endpoint matches.
     *
     * @param _call
     *            the call to dispatch
     * @throws IOException
     *             if the response cannot be written
     */
    private void dispatch(final HttpCall _call) throws IOException {
        final ServiceHandler handler = route(_call.getPath());
        if (null == handler) {
            _call.setServiceName("unknown");
            final ObjectNode err = Json.object();
            err.put("error", "No mock endpoint matches " + _call.getMethod() + " " + Redactor.redactPath(_call.getPath()));
            err.put("hint", "GET /mock/health lists the supported endpoints");
            _call.sendJson(404, err);
            return;
        }
        _call.setServiceName(handler.getServiceName());
        handler.handle(_call);
        if (!_call.hasResponded()) {
            sendInternalError(_call, "Mock handler produced no response");
        }
    }

    /**
     * Pick the handler for a normalized request path.
     *
     * @param _path
     *            the normalized request path
     * @return the handler, or null if no service owns the path
     */
    private ServiceHandler route(final String _path) {
        if ("/".equals(_path) || "/mock".equals(_path) || _path.startsWith("/mock/")) {
            return m_admin;
        }
        // watsonx.ai: the toolkit hard-codes the IBM Cloud hosts, so the paths are identical here
        if ("/identity/token".equals(_path) || _path.startsWith("/ml/v1/")) {
            return m_watsonx;
        }
        if (_path.startsWith("/api/")) {
            return m_ollama;
        }
        if (_path.startsWith("/2010-04-01/")) {
            return m_twilio;
        }
        if (_path.startsWith("/services/")) {
            return m_slack;
        }
        if ("/topics".equals(_path) || _path.startsWith("/topics/")) {
            return m_kafka;
        }
        // OpenAI-compatible endpoints live under a user-configurable base path, so match on the suffix
        if (_path.endsWith("/completions") || _path.endsWith("/models")) {
            return m_openAi;
        }
        return null;
    }

    /**
     * Send a 500 response if nothing has been sent yet. A failure to send is logged and the exchange is left for the HTTP server to clean up.
     *
     * @param _call
     *            the call to answer
     * @param _message
     *            the error message; must not contain secrets
     */
    private void sendInternalError(final HttpCall _call, final String _message) {
        if (_call.hasResponded()) {
            return;
        }
        final ObjectNode err = Json.object();
        err.put("error", _message);
        try {
            _call.sendJson(500, err);
        } catch (final IOException _e) {
            MockLog.warn("Could not send 500 response", _e);
        }
    }
}
