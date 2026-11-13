package com.ibm.dbsdk.mock.handlers;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.ibm.dbsdk.mock.Directives;
import com.ibm.dbsdk.mock.HttpCall;
import com.ibm.dbsdk.mock.Json;
import com.ibm.dbsdk.mock.MockState;

/**
 * Emulates Slack incoming webhooks ({@code POST /services/T.../B.../xxxx}) as used by {@code slack_sendmessage}. Like Slack, responses are plain text: {@code ok} on success or an error code such as
 * {@code no_text} / {@code invalid_payload}.
 */
public final class SlackHandler extends ServiceHandler {

    private static final int MAX_TEXT_LENGTH = 40000;
    private static final String SERVICES_PREFIX = "/services/";

    public SlackHandler(final MockState _state) {
        super(_state);
    }

    /**
     * @param _body
     *            the webhook payload
     * @param _field
     *            "blocks" or "attachments"
     * @return true if the field is a non-empty array
     */
    private static boolean hasNonEmptyArray(final JsonNode _body, final String _field) {
        final JsonNode value = _body.get(_field);
        return null != value && value.isArray() && 0 < value.size();
    }

    /**
     * @param _status
     *            the HTTP status requested by a directive
     * @return the plain-text error code Slack returns with that status
     */
    private static String slackError(final int _status) {
        switch (_status) {
            case 400:
                return "invalid_payload";
            case 403:
                return "invalid_token";
            case 404:
                return "no_service";
            case 410:
                return "channel_is_archived";
            case 429:
                return "rate_limited";
            default:
                return _status >= 500 ? "rollup_error" : "action_prohibited";
        }
    }

    @Override
    public String getServiceName() {
        return "slack";
    }

    /**
     * Handle a webhook post. The payload may be a JSON body or a {@code payload=<json>} form field, as Slack accepts both.
     *
     * @param _call
     *            the call to handle
     * @throws IOException
     *             if the response cannot be written
     */
    @Override
    public void handle(final HttpCall _call) throws IOException {
        if (!"POST".equals(_call.getMethod())) {
            _call.sendText(405, "invalid_method");
            return;
        }
        final String[] parts = _call.getPath().substring(SERVICES_PREFIX.length()).split("/");
        if (3 != parts.length || isBlank(parts[0]) || isBlank(parts[1]) || isBlank(parts[2])) {
            _call.sendText(404, "no_service");
            return;
        }
        final JsonNode body = null == _call.getJson() ? Json.parse(_call.getForm().get("payload"), "Slack payload form field") : _call.getJson();
        if (null == body || !body.isObject()) {
            _call.sendText(400, "invalid_payload");
            return;
        }
        final String text = Json.text(body, "text");
        final Directives directives = Directives.parse(text);
        directives.applyDelay();
        if (directives.isErrorRequested()) {
            _call.sendText(directives.getStatus().intValue(), slackError(directives.getStatus().intValue()));
            return;
        }
        if (isBlank(text) && !hasNonEmptyArray(body, "blocks") && !hasNonEmptyArray(body, "attachments")) {
            _call.sendText(400, "no_text");
            return;
        }
        if (null != text && text.length() > MAX_TEXT_LENGTH) {
            _call.sendText(400, "msg_too_long");
            return;
        }
        _call.send(200, "text/html", directives.isMalformed() ? "" : "ok");
    }
}
