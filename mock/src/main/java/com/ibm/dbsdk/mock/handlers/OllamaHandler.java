package com.ibm.dbsdk.mock.handlers;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ibm.dbsdk.mock.Directives;
import com.ibm.dbsdk.mock.HttpCall;
import com.ibm.dbsdk.mock.Json;
import com.ibm.dbsdk.mock.MockState;
import com.ibm.dbsdk.mock.TextGen;

/**
 * Emulates the Ollama native API used by {@code ollama_generate}:
 * <ul>
 * <li>{@code POST /api/generate} (streaming and non-streaming)</li>
 * <li>{@code GET  /api/tags}</li>
 * <li>{@code GET  /api/version}</li>
 * </ul>
 */
public final class OllamaHandler extends ServiceHandler {

    private static final List<String> s_models = Arrays.asList("granite3.2:8b", "granite3.3:8b", "llama3:latest", "llama3.2:3b", "mistral:7b");

    public OllamaHandler(final MockState _state) {
        super(_state);
    }

    /**
     * @param _model
     *            the model name
     * @return a response object carrying the model name and a creation timestamp
     */
    private static ObjectNode baseChunk(final String _model) {
        final ObjectNode n = Json.object();
        n.put("model", _model);
        n.put("created_at", Instant.now().toString());
        return n;
    }

    /**
     * Send an Ollama-style error ({@code {"error": "..."}}).
     *
     * @param _call
     *            the call to answer
     * @param _status
     *            the HTTP status
     * @param _message
     *            the error message
     * @throws IOException
     *             if the response cannot be written
     */
    private static void error(final HttpCall _call, final int _status, final String _message) throws IOException {
        final ObjectNode err = Json.object();
        err.put("error", _message);
        _call.sendJson(_status, err);
    }

    /**
     * Add the fields Ollama sends on the final ({@code done: true}) response.
     *
     * @param _resp
     *            the response to complete
     * @param _prompt
     *            the prompt, for the token counts
     * @param _result
     *            the generated reply
     */
    private static void finish(final ObjectNode _resp, final String _prompt, final TextGen.Result _result) {
        _resp.put("done", true);
        _resp.put("done_reason", _result.isTruncated() ? "length" : "stop");
        _resp.putArray("context");
        _resp.put("total_duration", 1000000L);
        _resp.put("load_duration", 100000L);
        _resp.put("prompt_eval_count", TextGen.countTokens(_prompt));
        _resp.put("prompt_eval_duration", 200000L);
        _resp.put("eval_count", _result.getTokens());
        _resp.put("eval_duration", 700000L);
    }

    /**
     * Handle {@code POST /api/generate}. Streams NDJSON unless the request sets {@code "stream": false}, matching Ollama's default.
     *
     * @param _call
     *            the call to handle
     * @throws IOException
     *             if the response cannot be written
     */
    private void generate(final HttpCall _call) throws IOException {
        final JsonNode body = _call.getJson();
        if (null == body || !body.isObject()) {
            error(_call, 400, "invalid character in request body: expected a JSON object");
            return;
        }
        final String prompt = Json.text(body, "prompt");
        final Directives directives = Directives.parse(prompt);
        directives.applyDelay();

        final String model = Json.text(body, "model");
        if (isBlank(model)) {
            error(_call, 400, "model is required");
            return;
        }
        if (directives.isErrorRequested()) {
            final int status = directives.getStatus().intValue();
            error(_call, status, 404 == status ? "model \"" + model + "\" not found, try pulling it first" : "mock error requested via directive");
            return;
        }

        final TextGen.Result result = TextGen.generate("ollama", model, prompt, directives, Json.integer(body.get("options"), "num_predict"));
        final boolean stream = !body.has("stream") || Json.isTrue(body, "stream");
        if (stream && !directives.isMalformed()) {
            streamResponse(_call, model, prompt, result);
            return;
        }
        final ObjectNode resp = baseChunk(model);
        if (!directives.isMalformed()) {
            resp.put("response", result.getText());
            finish(resp, prompt, result);
        }
        _call.sendJson(200, resp);
    }

    @Override
    public String getServiceName() {
        return "ollama";
    }

    /**
     * Route an Ollama request by path.
     *
     * @param _call
     *            the call to handle
     * @throws IOException
     *             if the response cannot be written
     */
    @Override
    public void handle(final HttpCall _call) throws IOException {
        final String path = _call.getPath();
        if ("/api/generate".equals(path)) {
            if ("POST".equals(_call.getMethod())) {
                generate(_call);
            } else {
                error(_call, 405, "method not allowed");
            }
        } else if ("/api/tags".equals(path)) {
            tags(_call);
        } else if ("/api/version".equals(path)) {
            final ObjectNode v = Json.object();
            v.put("version", "0.0.0-mock");
            _call.sendJson(200, v);
        } else {
            error(_call, 404, "404 page not found");
        }
    }

    /**
     * Stream the reply as NDJSON, one word per chunk, followed by a final {@code done: true} chunk.
     *
     * @param _call
     *            the call to answer
     * @param _model
     *            the model name
     * @param _prompt
     *            the prompt
     * @param _result
     *            the generated reply
     * @throws IOException
     *             if the response cannot be written
     */
    private void streamResponse(final HttpCall _call, final String _model, final String _prompt, final TextGen.Result _result) throws IOException {
        try (final OutputStream out = _call.startStream(200, "application/x-ndjson")) {
            final List<String> words = TextGen.words(_result.getText());
            for (int i = 0; i < words.size(); i++) {
                final ObjectNode chunk = baseChunk(_model);
                chunk.put("response", (0 == i ? "" : " ") + words.get(i));
                chunk.put("done", false);
                out.write((Json.mapper().writeValueAsString(chunk) + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            final ObjectNode last = baseChunk(_model);
            last.put("response", "");
            finish(last, _prompt, _result);
            out.write((Json.mapper().writeValueAsString(last) + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Handle {@code GET /api/tags}: the list of "installed" models.
     *
     * @param _call
     *            the call to handle
     * @throws IOException
     *             if the response cannot be written
     */
    private void tags(final HttpCall _call) throws IOException {
        final ObjectNode resp = Json.object();
        final ArrayNode models = resp.putArray("models");
        for (final String name : s_models) {
            final ObjectNode o = models.addObject();
            o.put("name", name);
            o.put("model", name);
            o.put("modified_at", m_state.getStartedAt().toString());
            o.put("size", 4000000000L);
            o.put("digest", randomHex(64));
            final ObjectNode details = o.putObject("details");
            details.put("format", "gguf");
            details.put("family", name.split("[:.]")[0]);
            details.put("quantization_level", "Q4_K_M");
        }
        _call.sendJson(200, resp);
    }
}
