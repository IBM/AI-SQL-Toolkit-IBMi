package com.ibm.dbsdk.mock.handlers;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ibm.dbsdk.mock.Directives;
import com.ibm.dbsdk.mock.HttpCall;
import com.ibm.dbsdk.mock.Json;
import com.ibm.dbsdk.mock.MockConfig;
import com.ibm.dbsdk.mock.MockState;
import com.ibm.dbsdk.mock.TextGen;

/**
 * Emulates OpenAI-compatible endpoints under any base path (e.g. {@code /v1}, {@code /v1beta/openai}), as used by {@code openai_compatible_generate}, {@code openai_compatible_generate_json} and
 * {@code openai_compatible_chat_generate}:
 * <ul>
 * <li>{@code POST {base}/completions} (legacy text completions)</li>
 * <li>{@code POST {base}/chat/completions}</li>
 * <li>{@code GET  {base}/models}</li>
 * </ul>
 */
public final class OpenAiHandler extends ServiceHandler {

    /** Approximation of the request fields accepted by Gemini's OpenAI-compatible chat endpoint. */
    private static final Set<String> s_geminiChatFields = new HashSet<String>(Arrays.asList("model", "messages", "max_tokens", "max_completion_tokens", "temperature", "top_p", "n", "stop", "stream",
        "stream_options", "seed", "presence_penalty", "frequency_penalty", "response_format", "tools", "tool_choice", "reasoning_effort", "extra_body"));
    private static final List<String> s_models = Arrays.asList("llama3", "gpt-4o-mini", "gemini-2.0-flash", "granite-3.3-8b-instruct");
    private static final Set<String> s_roles = new HashSet<String>(Arrays.asList("system", "developer", "user", "assistant", "tool", "function"));

    public OpenAiHandler(final MockState _state) {
        super(_state);
    }

    /**
     * Join the text of every message, so directives can appear in any of them.
     *
     * @param _messages
     *            the chat messages
     * @return the concatenated message text, or null if there are no messages
     */
    private static String allMessageText(final JsonNode _messages) {
        if (null == _messages || !_messages.isArray()) {
            return null;
        }
        final StringBuilder sb = new StringBuilder();
        for (final JsonNode m : _messages) {
            if (0 < sb.length()) {
                sb.append('\n');
            }
            sb.append(contentText(m.get("content")));
        }
        return sb.toString();
    }

    /**
     * Cut generated text at the earliest stop sequence.
     *
     * @param _text
     *            the generated text
     * @param _stop
     *            the request's {@code stop} value: a string, an array of strings, or null
     * @return the text up to (not including) the first stop sequence
     */
    private static String applyStop(final String _text, final JsonNode _stop) {
        if (null == _stop || _stop.isNull()) {
            return _text;
        }
        final List<String> stops = new ArrayList<String>();
        if (_stop.isArray()) {
            for (final JsonNode s : _stop) {
                stops.add(s.asText());
            }
        } else {
            stops.add(_stop.asText());
        }
        int cut = -1;
        for (final String s : stops) {
            final int idx = s.isEmpty() ? -1 : _text.indexOf(s);
            if (idx >= 0 && (cut < 0 || idx < cut)) {
                cut = idx;
            }
        }
        return cut < 0 ? _text : _text.substring(0, cut);
    }

    /**
     * Extract text from a message's {@code content}, which may be a string or an array of content parts.
     *
     * @param _content
     *            the content node; may be null
     * @return the text, or an empty string
     */
    private static String contentText(final JsonNode _content) {
        if (null == _content || _content.isNull()) {
            return "";
        }
        if (!_content.isArray()) {
            return _content.asText();
        }
        final StringBuilder sb = new StringBuilder();
        for (final JsonNode part : _content) {
            final String t = part.isTextual() ? part.asText() : Json.text(part, "text");
            if (null != t) {
                if (0 < sb.length()) {
                    sb.append(' ');
                }
                sb.append(t);
            }
        }
        return sb.toString();
    }

    /**
     * Send the error for a {@code [[mock:status=NNN]]} directive.
     *
     * @param _call
     *            the call to answer
     * @param _status
     *            the requested status
     * @throws IOException
     *             if the response cannot be written
     */
    private static void directiveError(final HttpCall _call, final int _status) throws IOException {
        final String type = 429 == _status ? "rate_limit_exceeded" : _status >= 500 ? "server_error" : "invalid_request_error";
        error(_call, _status, "Mock error requested via directive (" + reason(_status) + ").", type, null, null);
    }

    /**
     * Send an OpenAI-style error ({@code {"error": {message, type, param, code}}}).
     *
     * @param _call
     *            the call to answer
     * @param _status
     *            the HTTP status
     * @param _message
     *            the error message
     * @param _type
     *            the error type
     * @param _param
     *            the offending parameter, or null
     * @param _code
     *            the error code, or null
     * @throws IOException
     *             if the response cannot be written
     */
    private static void error(final HttpCall _call, final int _status, final String _message, final String _type, final String _param, final String _code) throws IOException {
        final ObjectNode err = Json.object();
        final ObjectNode e = err.putObject("error");
        e.put("message", _message);
        e.put("type", _type);
        e.put("param", _param);
        e.put("code", _code);
        _call.sendJson(_status, err);
    }

    /**
     * Send a Gemini-style error (an array holding one {@code {"error": {code, message, status}}} object).
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
    private static void geminiError(final HttpCall _call, final int _status, final String _message) throws IOException {
        final ArrayNode arr = Json.array();
        final ObjectNode e = arr.addObject().putObject("error");
        e.put("code", _status);
        e.put("message", _message);
        final String status;
        if (400 == _status) {
            status = "INVALID_ARGUMENT";
        } else if (401 == _status) {
            status = "UNAUTHENTICATED";
        } else if (404 == _status) {
            status = "NOT_FOUND";
        } else if (429 == _status) {
            status = "RESOURCE_EXHAUSTED";
        } else {
            status = "INTERNAL";
        }
        e.put("status", status);
        _call.sendJson(_status, arr);
    }

    /**
     * Find the text the reply should answer: the last user message, or the last message of any role if there is no user message.
     *
     * @param _messages
     *            the chat messages
     * @return the message text, or null if there are no messages
     */
    private static String lastUserMessage(final JsonNode _messages) {
        if (null == _messages || !_messages.isArray() || 0 == _messages.size()) {
            return null;
        }
        String last = null;
        for (final JsonNode m : _messages) {
            if ("user".equals(Json.text(m, "role"))) {
                last = contentText(m.get("content"));
            }
        }
        return null == last ? contentText(_messages.get(_messages.size() - 1).get("content")) : last;
    }

    /**
     * @param _prompt
     *            the {@code prompt} field: a string, an array of strings, or null
     * @return the prompt text (the first element for arrays), or null if absent
     */
    private static String promptText(final JsonNode _prompt) {
        if (null == _prompt || _prompt.isNull()) {
            return null;
        }
        if (_prompt.isArray()) {
            return 0 == _prompt.size() ? "" : _prompt.get(0).asText();
        }
        return _prompt.asText();
    }

    /**
     * Send a single-event server-sent-events stream followed by the {@code [DONE]} marker.
     *
     * @param _call
     *            the call to answer
     * @param _chunk
     *            the event payload
     * @throws IOException
     *             if the response cannot be written
     */
    private static void sse(final HttpCall _call, final ObjectNode _chunk) throws IOException {
        try (final OutputStream out = _call.startStream(200, "text/event-stream")) {
            out.write(("data: " + Json.mapper().writeValueAsString(_chunk) + "\n\n").getBytes(StandardCharsets.UTF_8));
            out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Add an OpenAI {@code usage} object.
     *
     * @param _resp
     *            the response to add to
     * @param _promptTokens
     *            the prompt token count
     * @param _completionTokens
     *            the completion token count
     */
    private static void usage(final ObjectNode _resp, final int _promptTokens, final int _completionTokens) {
        final ObjectNode u = _resp.putObject("usage");
        u.put("prompt_tokens", _promptTokens);
        u.put("completion_tokens", _completionTokens);
        u.put("total_tokens", _promptTokens + _completionTokens);
    }

    /**
     * Handle {@code POST {base}/chat/completions}.
     *
     * @param _call
     *            the call to handle
     * @throws IOException
     *             if the response cannot be written
     */
    private void chat(final HttpCall _call) throws IOException {
        final JsonNode body = _call.getJson();
        if (null == body || !body.isObject()) {
            if (isGemini()) {
                geminiError(_call, 400, "Invalid JSON payload received.");
            } else {
                error(_call, 400, "We could not parse the JSON body of your request.", "invalid_request_error", null, null);
            }
            return;
        }
        if (isGemini() && !checkGeminiFields(_call, body)) {
            return;
        }
        final JsonNode messages = body.get("messages");
        final Directives directives = Directives.parse(allMessageText(messages));
        directives.applyDelay();

        final String model = Json.text(body, "model");
        if (isBlank(model)) {
            error(_call, 400, "You must provide a model parameter.", "invalid_request_error", "model", null);
            return;
        }
        if (!checkMessages(_call, messages)) {
            return;
        }
        final Sampling sampling = validateSampling(_call, body);
        if (null == sampling) {
            return;
        }
        if (directives.isErrorRequested()) {
            if (isGemini()) {
                geminiError(_call, directives.getStatus().intValue(), "Mock error requested via directive.");
            } else {
                directiveError(_call, directives.getStatus().intValue());
            }
            return;
        }
        sendChat(_call, body, model, messages, sampling, directives);
    }

    /**
     * Reject fields Gemini does not understand, as the real Gemini endpoint does.
     *
     * @param _call
     *            the call being validated
     * @param _body
     *            the request body
     * @return true if every field is accepted
     * @throws IOException
     *             if an error response cannot be written
     */
    private boolean checkGeminiFields(final HttpCall _call, final JsonNode _body) throws IOException {
        final Iterator<String> names = _body.fieldNames();
        while (names.hasNext()) {
            final String field = names.next();
            if (!s_geminiChatFields.contains(field)) {
                geminiError(_call, 400, "Invalid JSON payload received. Unknown name \"" + field + "\": Cannot find field.");
                return false;
            }
        }
        return true;
    }

    /**
     * Validate the {@code messages} array: non-empty, known roles, and content on every non-assistant message.
     *
     * @param _call
     *            the call being validated
     * @param _messages
     *            the messages node; may be null
     * @return true if the messages are valid
     * @throws IOException
     *             if an error response cannot be written
     */
    private boolean checkMessages(final HttpCall _call, final JsonNode _messages) throws IOException {
        if (null == _messages || !_messages.isArray() || 0 == _messages.size()) {
            error(_call, 400, "'messages' must be a non-empty array.", "invalid_request_error", "messages", null);
            return false;
        }
        for (int i = 0; i < _messages.size(); i++) {
            final JsonNode m = _messages.get(i);
            final String role = Json.text(m, "role");
            if (null == role || !s_roles.contains(role)) {
                error(_call, 400, "Invalid value for 'messages[" + i + "].role': " + role, "invalid_request_error", "messages[" + i + "].role", null);
                return false;
            }
            if (!m.has("content") && !"assistant".equals(role)) {
                error(_call, 400, "Missing required parameter: 'messages[" + i + "].content'.", "invalid_request_error", "messages[" + i + "].content", null);
                return false;
            }
        }
        return true;
    }

    /**
     * Handle {@code POST {base}/completions}.
     *
     * @param _call
     *            the call to handle
     * @throws IOException
     *             if the response cannot be written
     */
    private void completions(final HttpCall _call) throws IOException {
        final JsonNode body = _call.getJson();
        if (null == body || !body.isObject()) {
            error(_call, 400, "We could not parse the JSON body of your request.", "invalid_request_error", null, null);
            return;
        }
        final String prompt = promptText(body.get("prompt"));
        final Directives directives = Directives.parse(prompt);
        directives.applyDelay();

        final String model = Json.text(body, "model");
        if (isBlank(model)) {
            error(_call, 400, "You must provide a model parameter.", "invalid_request_error", "model", null);
            return;
        }
        if (null == prompt) {
            error(_call, 400, "'prompt' is a required property", "invalid_request_error", "prompt", null);
            return;
        }
        final Sampling sampling = validateSampling(_call, body);
        if (null == sampling) {
            return;
        }
        if (directives.isErrorRequested()) {
            directiveError(_call, directives.getStatus().intValue());
            return;
        }
        sendCompletion(_call, body, model, prompt, sampling, directives);
    }

    @Override
    public String getServiceName() {
        return "openai";
    }

    /**
     * Route an OpenAI-compatible request by path suffix and method.
     *
     * @param _call
     *            the call to handle
     * @throws IOException
     *             if the response cannot be written
     */
    @Override
    public void handle(final HttpCall _call) throws IOException {
        final String path = _call.getPath();
        if (!isAuthorized(_call)) {
            return;
        }
        if (path.endsWith("/models")) {
            models(_call);
        } else if (!"POST".equals(_call.getMethod())) {
            error(_call, 405, "Method " + _call.getMethod() + " not allowed; use POST.", "invalid_request_error", null, null);
        } else if (path.endsWith("/chat/completions")) {
            chat(_call);
        } else if (isGemini()) {
            error(_call, 404, "The legacy /completions endpoint is not supported by this server. Use /chat/completions (dbsdk_v1.openai_compatible_chat_generate).", "not_found", null, null);
        } else {
            completions(_call);
        }
    }

    /**
     * Enforce {@code --openai-api-key} when it is configured, sending OpenAI's 401 errors otherwise.
     *
     * @param _call
     *            the call to check
     * @return true if the call may proceed
     * @throws IOException
     *             if an error response cannot be written
     */
    private boolean isAuthorized(final HttpCall _call) throws IOException {
        final String required = m_config.getOpenAiApiKey();
        if (null == required) {
            return true;
        }
        final String token = _call.getBearerToken();
        if (required.equals(token)) {
            return true;
        }
        if (isBlank(token)) {
            error(_call, 401, "You didn't provide an API key. You need to provide your API key in an Authorization header using Bearer auth (i.e. Authorization: Bearer YOUR_KEY).",
                "invalid_request_error", null, null);
        } else {
            error(_call, 401, "Incorrect API key provided.", "invalid_request_error", null, "invalid_api_key");
        }
        return false;
    }

    private boolean isGemini() {
        return MockConfig.OpenAiFlavor.GEMINI == m_config.getOpenAiFlavor();
    }

    /**
     * Handle {@code GET {base}/models}.
     *
     * @param _call
     *            the call to handle
     * @throws IOException
     *             if the response cannot be written
     */
    private void models(final HttpCall _call) throws IOException {
        final ObjectNode resp = Json.object();
        resp.put("object", "list");
        final ArrayNode data = resp.putArray("data");
        for (final String id : s_models) {
            final ObjectNode o = data.addObject();
            o.put("id", id);
            o.put("object", "model");
            o.put("created", m_state.getStartedAt().getEpochSecond());
            o.put("owned_by", "dbsdk-mock");
        }
        _call.sendJson(200, resp);
    }

    /**
     * Send a successful chat completion, as JSON or as a server-sent-events stream.
     *
     * @param _call
     *            the call to answer
     * @param _body
     *            the request body
     * @param _model
     *            the requested model
     * @param _messages
     *            the chat messages
     * @param _sampling
     *            the validated sampling parameters
     * @param _directives
     *            the directives found in the messages
     * @throws IOException
     *             if the response cannot be written
     */
    private void sendChat(final HttpCall _call, final JsonNode _body, final String _model, final JsonNode _messages, final Sampling _sampling, final Directives _directives) throws IOException {
        final TextGen.Result result = TextGen.generate("chat", _model, lastUserMessage(_messages), _directives, _sampling.m_maxTokens);
        final String text = applyStop(result.getText(), _body.get("stop"));
        final String finish = result.isTruncated() && text.equals(result.getText()) ? "length" : "stop";
        final String id = "chatcmpl-" + randomHex(24);

        if (Json.isTrue(_body, "stream") && !_directives.isMalformed()) {
            final ObjectNode chunk = Json.object();
            chunk.put("id", id);
            chunk.put("object", "chat.completion.chunk");
            chunk.put("created", nowEpochSeconds());
            chunk.put("model", _model);
            final ObjectNode choice = chunk.putArray("choices").addObject();
            choice.put("index", 0);
            final ObjectNode delta = choice.putObject("delta");
            delta.put("role", "assistant");
            delta.put("content", text);
            choice.put("finish_reason", finish);
            sse(_call, chunk);
            return;
        }

        final ObjectNode resp = Json.object();
        resp.put("id", id);
        resp.put("object", "chat.completion");
        resp.put("created", nowEpochSeconds());
        resp.put("model", _model);
        if (_directives.isMalformed()) {
            resp.put("note", "malformed response requested via directive");
            _call.sendJson(200, resp);
            return;
        }
        final ArrayNode choices = resp.putArray("choices");
        for (int i = 0; i < _sampling.m_n; i++) {
            final ObjectNode c = choices.addObject();
            c.put("index", i);
            final ObjectNode msg = c.putObject("message");
            msg.put("role", "assistant");
            msg.put("content", text);
            c.putNull("logprobs");
            c.put("finish_reason", finish);
        }
        usage(resp, TextGen.countTokens(allMessageText(_messages)), result.getTokens() * _sampling.m_n);
        _call.sendJson(200, resp);
    }

    /**
     * Send a successful legacy completion, as JSON or as a server-sent-events stream.
     *
     * @param _call
     *            the call to answer
     * @param _body
     *            the request body
     * @param _model
     *            the requested model
     * @param _prompt
     *            the prompt
     * @param _sampling
     *            the validated sampling parameters
     * @param _directives
     *            the directives found in the prompt
     * @throws IOException
     *             if the response cannot be written
     */
    private void sendCompletion(final HttpCall _call, final JsonNode _body, final String _model, final String _prompt, final Sampling _sampling, final Directives _directives) throws IOException {
        final TextGen.Result result = TextGen.generate("openai", _model, _prompt, _directives, _sampling.m_maxTokens);
        final String stopped = applyStop(result.getText(), _body.get("stop"));
        final String text = Json.isTrue(_body, "echo") ? _prompt + stopped : stopped;
        final String finish = result.isTruncated() && stopped.equals(result.getText()) ? "length" : "stop";
        final String id = "cmpl-" + randomHex(24);

        if (Json.isTrue(_body, "stream") && !_directives.isMalformed()) {
            final ObjectNode chunk = Json.object();
            chunk.put("id", id);
            chunk.put("object", "text_completion");
            chunk.put("created", nowEpochSeconds());
            chunk.put("model", _model);
            final ObjectNode choice = chunk.putArray("choices").addObject();
            choice.put("index", 0);
            choice.put("text", text);
            choice.putNull("logprobs");
            choice.put("finish_reason", finish);
            sse(_call, chunk);
            return;
        }

        final ObjectNode resp = Json.object();
        resp.put("id", id);
        resp.put("object", "text_completion");
        resp.put("created", nowEpochSeconds());
        resp.put("model", _model);
        if (_directives.isMalformed()) {
            resp.put("note", "malformed response requested via directive");
            _call.sendJson(200, resp);
            return;
        }
        final ArrayNode choices = resp.putArray("choices");
        for (int i = 0; i < _sampling.m_n; i++) {
            final ObjectNode c = choices.addObject();
            c.put("text", text);
            c.put("index", i);
            c.putNull("logprobs");
            c.put("finish_reason", finish);
        }
        usage(resp, TextGen.countTokens(_prompt), result.getTokens() * _sampling.m_n);
        _call.sendJson(200, resp);
    }

    /**
     * Check that an optional numeric field, if present, lies within a range.
     *
     * @param _call
     *            the call being validated
     * @param _body
     *            the request body
     * @param _field
     *            the field name
     * @param _min
     *            the inclusive minimum
     * @param _max
     *            the inclusive maximum
     * @return true if the field is absent, null, or within range
     * @throws IOException
     *             if an error response cannot be written
     */
    private boolean validateRange(final HttpCall _call, final JsonNode _body, final String _field, final double _min, final double _max) throws IOException {
        if (!_body.has(_field) || _body.get(_field).isNull()) {
            return true;
        }
        final Double value = Json.number(_body, _field);
        if (null != value && value.doubleValue() >= _min && value.doubleValue() <= _max) {
            return true;
        }
        error(_call, 400, "Invalid '" + _field + "': must be a number between " + (int) _min + " and " + (int) _max + ".", "invalid_request_error", _field, null);
        return false;
    }

    /**
     * Validate the sampling parameters shared by both endpoints.
     *
     * @param _call
     *            the call being validated
     * @param _body
     *            the request body
     * @return the parameters, or null if an error response was sent
     * @throws IOException
     *             if an error response cannot be written
     */
    private Sampling validateSampling(final HttpCall _call, final JsonNode _body) throws IOException {
        if (!validateRange(_call, _body, "temperature", 0, 2) || !validateRange(_call, _body, "top_p", 0, 1) || !validateRange(_call, _body, "presence_penalty", -2, 2)
            || !validateRange(_call, _body, "frequency_penalty", -2, 2) || !validateRange(_call, _body, "n", 1, 128)) {
            return null;
        }
        final Integer n = Json.integer(_body, "n");
        final Integer completionTokens = Json.integer(_body, "max_completion_tokens");
        final Integer maxTokens = null == completionTokens ? Json.integer(_body, "max_tokens") : completionTokens;
        if (null != maxTokens && maxTokens.intValue() < 0) {
            error(_call, 400, "Invalid 'max_tokens': must be a positive integer.", "invalid_request_error", "max_tokens", null);
            return null;
        }
        return new Sampling(maxTokens, null == n ? 1 : n.intValue());
    }

    /** Sampling parameters that shape the generated reply. */
    private static final class Sampling {

        private final Integer m_maxTokens;
        private final int m_n;

        Sampling(final Integer _maxTokens, final int _n) {
            m_maxTokens = _maxTokens;
            m_n = _n;
        }
    }
}
