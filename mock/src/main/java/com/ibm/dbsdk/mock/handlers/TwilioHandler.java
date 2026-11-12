package com.ibm.dbsdk.mock.handlers;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ibm.dbsdk.mock.Directives;
import com.ibm.dbsdk.mock.HttpCall;
import com.ibm.dbsdk.mock.Json;
import com.ibm.dbsdk.mock.MockState;

/**
 * Emulates the Twilio Messages API used by {@code twilio_sendsms}: {@code POST /2010-04-01/Accounts/{AccountSid}/Messages.json} with a form-encoded {@code To}, {@code From} and {@code Body},
 * authenticated with HTTP Basic {@code AccountSid:AuthToken}.
 */
public final class TwilioHandler extends ServiceHandler {

    private static final int MAX_BODY_LENGTH = 1600;

    private static final String MESSAGES_SUFFIX = "/Messages.json";

    private static final String PREFIX = "/2010-04-01/Accounts/";

    private static final DateTimeFormatter s_rfc2822 = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);

    public TwilioHandler(final MockState _state) {
        super(_state);
    }

    /**
     * Send a Twilio-style error ({@code code}, {@code message}, {@code more_info}, {@code status}).
     *
     * @param _call
     *            the call to answer
     * @param _status
     *            the HTTP status
     * @param _code
     *            the Twilio error code
     * @param _message
     *            the error message
     * @throws IOException
     *             if the response cannot be written
     */
    private static void error(final HttpCall _call, final int _status, final int _code, final String _message) throws IOException {
        final ObjectNode err = Json.object();
        err.put("code", _code);
        err.put("message", _message);
        err.put("more_info", "https://www.twilio.com/docs/errors/" + _code);
        err.put("status", _status);
        _call.sendJson(_status, err);
    }

    /**
     * @param _status
     *            the HTTP status requested by a directive
     * @return a plausible Twilio error code for that status
     */
    private static int errorCodeFor(final int _status) {
        if (400 == _status) {
            return 21211;
        }
        if (429 == _status) {
            return 20429;
        }
        return _status >= 500 ? 20500 : 20001;
    }

    @Override
    public String getServiceName() {
        return "twilio";
    }

    /**
     * Handle a send-message request: check the path, credentials and form fields, then answer 201 with a queued message resource.
     *
     * @param _call
     *            the call to handle
     * @throws IOException
     *             if the response cannot be written
     */
    @Override
    public void handle(final HttpCall _call) throws IOException {
        final String path = _call.getPath();
        final String sid = path.startsWith(PREFIX) && path.endsWith(MESSAGES_SUFFIX) ? path.substring(PREFIX.length(), path.length() - MESSAGES_SUFFIX.length()) : "";
        if (isBlank(sid) || sid.contains("/")) {
            error(_call, 404, 20404, "The requested resource " + path + " was not found");
            return;
        }
        if (!"POST".equals(_call.getMethod())) {
            error(_call, 405, 20004, "Method not allowed");
            return;
        }
        if (!hasValidCredentials(_call, sid)) {
            error(_call, 401, 20003, "Authenticate");
            return;
        }
        final Map<String, String> form = _call.getForm();
        final String body = form.get("Body");
        final Directives directives = Directives.parse(body);
        directives.applyDelay();

        if (isBlank(form.get("To"))) {
            error(_call, 400, 21604, "A 'To' phone number is required.");
        } else if (isBlank(form.get("From")) && isBlank(form.get("MessagingServiceSid"))) {
            error(_call, 400, 21603, "A 'From' phone number is required.");
        } else if (isBlank(body) && isBlank(form.get("MediaUrl"))) {
            error(_call, 400, 21602, "Message body is required.");
        } else if (null != body && body.length() > MAX_BODY_LENGTH) {
            error(_call, 400, 21617, "The concatenated message body exceeds the 1600 character limit.");
        } else if (directives.isErrorRequested()) {
            final int status = directives.getStatus().intValue();
            error(_call, status, errorCodeFor(status), "Mock error requested via directive.");
        } else {
            _call.sendJson(201, messageResource(sid, form, directives));
        }
    }

    /**
     * Check HTTP Basic credentials: the user must be the account SID from the path, and the password must match {@code --twilio-auth-token} when configured.
     *
     * @param _call
     *            the call to check
     * @param _sid
     *            the account SID from the path
     * @return true if the credentials are accepted
     */
    private boolean hasValidCredentials(final HttpCall _call, final String _sid) {
        final String[] creds = _call.getBasicCredentials();
        if (null == creds || !_sid.equals(creds[0]) || isBlank(creds[1])) {
            return false;
        }
        return null == m_config.getTwilioAuthToken() || m_config.getTwilioAuthToken().equals(creds[1]);
    }

    /**
     * Build the message resource Twilio returns for a newly queued message.
     *
     * @param _sid
     *            the account SID
     * @param _form
     *            the request form fields
     * @param _directives
     *            the directives found in the body
     * @return the message resource
     */
    private ObjectNode messageResource(final String _sid, final Map<String, String> _form, final Directives _directives) {
        final String body = null == _form.get("Body") ? "" : _form.get("Body");
        final String messageSid = "SM" + randomHex(32);
        final String now = ZonedDateTime.now(ZoneOffset.UTC).format(s_rfc2822);
        final ObjectNode resp = Json.object();
        resp.put("account_sid", _sid);
        resp.put("api_version", "2010-04-01");
        resp.put("body", body);
        resp.put("date_created", now);
        resp.putNull("date_sent");
        resp.put("date_updated", now);
        resp.put("direction", "outbound-api");
        resp.putNull("error_code");
        resp.putNull("error_message");
        resp.put("from", _form.get("From"));
        resp.putNull("messaging_service_sid");
        resp.put("num_media", "0");
        resp.put("num_segments", String.valueOf(Math.max(1, (body.length() + 152) / 153)));
        resp.putNull("price");
        resp.put("price_unit", "USD");
        resp.put("sid", messageSid);
        if (!_directives.isMalformed()) {
            resp.put("status", "queued");
        }
        resp.putObject("subresource_uris").put("media", PREFIX + _sid + "/Messages/" + messageSid + "/Media.json");
        resp.put("to", _form.get("To"));
        resp.put("uri", PREFIX + _sid + "/Messages/" + messageSid + ".json");
        return resp;
    }
}
