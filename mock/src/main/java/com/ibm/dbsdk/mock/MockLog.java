package com.ibm.dbsdk.mock;

import java.io.PrintStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Process-wide console logger for the mock server. Output is disabled until {@link #setOutput(PrintStream)} is called with a non-null stream, which keeps unit tests quiet.
 *
 * Never pass credentials, tokens or other secrets to these methods; use {@link Redactor} first.
 */
public final class MockLog {

    private static volatile PrintStream s_out = null;
    private static final DateTimeFormatter s_timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private MockLog() {
    }

    /**
     * Log an error together with its stack trace.
     *
     * @param _message
     *            what failed; must not contain secrets
     * @param _e
     *            the cause, or null
     */
    public static void error(final String _message, final Throwable _e) {
        write("ERROR", _message, _e, true);
    }

    /**
     * Log an informational message.
     *
     * @param _message
     *            the message; must not contain secrets
     */
    public static void info(final String _message) {
        write("INFO ", _message, null, false);
    }

    /**
     * Set the destination for log output.
     *
     * @param _out
     *            the stream to write to, or null to disable logging
     */
    public static void setOutput(final PrintStream _out) {
        s_out = _out;
    }

    /**
     * Log a recoverable problem. Only the exception's type and message are printed, not its stack trace.
     *
     * @param _message
     *            what happened; must not contain secrets
     * @param _e
     *            the cause, or null
     */
    public static void warn(final String _message, final Throwable _e) {
        write("WARN ", _message, _e, false);
    }

    /**
     * Write one log record if output is enabled.
     *
     * @param _level
     *            the fixed-width level label
     * @param _message
     *            the message text
     * @param _e
     *            an optional cause
     * @param _withStack
     *            whether to print the cause's full stack trace
     */
    private static void write(final String _level, final String _message, final Throwable _e, final boolean _withStack) {
        final PrintStream out = s_out;
        if (null == out) {
            return;
        }
        final String suffix = (null == _e || _withStack) ? "" : ": " + _e;
        synchronized (out) {
            out.println(LocalTime.now().format(s_timeFormat) + " " + _level + " " + _message + suffix);
            if (null != _e && _withStack) {
                _e.printStackTrace(out);
            }
        }
    }
}
