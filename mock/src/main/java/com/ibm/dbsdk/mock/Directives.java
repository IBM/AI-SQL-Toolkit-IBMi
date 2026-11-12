package com.ibm.dbsdk.mock;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-band "directives" that let a caller steer the mock from inside SQL, where custom HTTP headers cannot be set. A directive is embedded in the prompt or message text, e.g.
 * {@code values dbsdk_v1.ollama_generate('hi [[mock:status=500]]')}.
 *
 * <ul>
 * <li>{@code [[mock:status=NNN]]} respond with HTTP status NNN and a service-shaped error body</li>
 * <li>{@code [[mock:delay=MS]]} wait MS milliseconds before responding (max 10 minutes)</li>
 * <li>{@code [[mock:reply=TEXT]]} use TEXT verbatim as the generated reply. TEXT ends at the first {@code ]]}, so it may contain single brackets (e.g. a JSON array)</li>
 * <li>{@code [[mock:malformed]]} respond 200 with a body lacking the expected fields</li>
 * <li>{@code [[mock:record-error]]} (Kafka) respond 200 with an error in {@code offsets[0]}</li>
 * </ul>
 */
public final class Directives {

    private static final long MAX_DELAY_MS = 10L * 60L * 1000L;

    private static final Pattern s_directivePattern = Pattern.compile("\\[\\[mock:([A-Za-z_-]+)(?:=(.*?))?\\]\\]", Pattern.DOTALL);
    private static final Directives s_none = new Directives(null, 0, null, false, false);

    private final long m_delayMs;
    private final boolean m_malformed;
    private final boolean m_recordError;
    private final String m_reply;
    private final Integer m_status;

    /**
     * @param _status
     *            the requested HTTP status, or null
     * @param _delayMs
     *            the requested delay in milliseconds
     * @param _reply
     *            the requested reply text, or null
     * @param _malformed
     *            whether a malformed response was requested
     * @param _recordError
     *            whether a Kafka record error was requested
     */
    private Directives(final Integer _status, final long _delayMs, final String _reply, final boolean _malformed, final boolean _recordError) {
        m_status = _status;
        m_delayMs = _delayMs;
        m_reply = _reply;
        m_malformed = _malformed;
        m_recordError = _recordError;
    }

    /**
     * Scan texts for directives. When a directive appears more than once, the last occurrence wins. Unknown or malformed directives are logged and ignored.
     *
     * @param _texts
     *            the texts to scan; null entries are skipped
     * @return the directives found, or an empty set of directives
     */
    public static Directives parse(final String... _texts) {
        Integer status = null;
        long delay = 0;
        String reply = null;
        boolean malformed = false;
        boolean recordError = false;
        boolean found = false;
        for (final String text : _texts) {
            if (null == text) {
                continue;
            }
            final Matcher m = s_directivePattern.matcher(text);
            while (m.find()) {
                found = true;
                final String name = m.group(1).toLowerCase(Locale.ROOT);
                final String value = null == m.group(2) ? "" : m.group(2);
                if ("status".equals(name)) {
                    status = parseStatus(value, status);
                } else if ("delay".equals(name)) {
                    delay = parseDelay(value, delay);
                } else if ("reply".equals(name)) {
                    reply = value;
                } else if ("malformed".equals(name)) {
                    malformed = true;
                } else if ("record-error".equals(name) || "record_error".equals(name)) {
                    recordError = true;
                } else {
                    MockLog.warn("Ignoring unknown directive [[mock:" + name + "]]", null);
                }
            }
        }
        return found ? new Directives(status, delay, reply, malformed, recordError) : s_none;
    }

    /**
     * Remove all directives from a text.
     *
     * @param _text
     *            the text; may be null
     * @return the text without directives, trimmed, or null if the input was null
     */
    public static String strip(final String _text) {
        if (null == _text) {
            return null;
        }
        return s_directivePattern.matcher(_text).replaceAll("").trim();
    }

    /**
     * Parse a delay directive value, clamped to the allowed range.
     *
     * @param _value
     *            the directive value
     * @param _current
     *            the delay to keep if the value is invalid
     * @return the new delay in milliseconds
     */
    private static long parseDelay(final String _value, final long _current) {
        try {
            return Math.max(0, Math.min(MAX_DELAY_MS, Long.parseLong(_value.trim())));
        } catch (final NumberFormatException _e) {
            MockLog.warn("Ignoring [[mock:delay=" + _value + "]]: not a number of milliseconds", null);
            return _current;
        }
    }

    /**
     * Parse a status directive value.
     *
     * @param _value
     *            the directive value
     * @param _current
     *            the status to keep if the value is invalid
     * @return the new status
     */
    private static Integer parseStatus(final String _value, final Integer _current) {
        try {
            final int status = Integer.parseInt(_value.trim());
            if (status >= 100 && status <= 599) {
                return Integer.valueOf(status);
            }
        } catch (final NumberFormatException _e) {
            MockLog.warn("Ignoring [[mock:status=" + _value + "]]: not a number", null);
            return _current;
        }
        MockLog.warn("Ignoring [[mock:status=" + _value + "]]: not between 100 and 599", null);
        return _current;
    }

    /**
     * Sleep for the requested delay, if any. An interrupt ends the sleep early and is re-asserted on the thread.
     */
    public void applyDelay() {
        if (m_delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(m_delayMs);
        } catch (final InterruptedException _e) {
            Thread.currentThread().interrupt();
        }
    }

    public long getDelayMs() {
        return m_delayMs;
    }

    public String getReply() {
        return m_reply;
    }

    public Integer getStatus() {
        return m_status;
    }

    /**
     * @return true when a status directive asks for a non-2xx response
     */
    public boolean isErrorRequested() {
        return null != m_status && (m_status.intValue() < 200 || m_status.intValue() >= 300);
    }

    public boolean isMalformed() {
        return m_malformed;
    }

    public boolean isRecordError() {
        return m_recordError;
    }
}
