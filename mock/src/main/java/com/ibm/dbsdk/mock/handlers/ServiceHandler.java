package com.ibm.dbsdk.mock.handlers;

import java.io.IOException;
import java.util.UUID;

import com.ibm.dbsdk.mock.HttpCall;
import com.ibm.dbsdk.mock.MockConfig;
import com.ibm.dbsdk.mock.MockState;

/** Common base for the per-service handlers. */
public abstract class ServiceHandler {

    protected final MockConfig m_config;
    protected final MockState m_state;

    protected ServiceHandler(final MockState _state) {
        m_state = _state;
        m_config = _state.getConfig();
    }

    /**
     * @param _text
     *            the text to test; may be null
     * @return true if the text is null, empty or only whitespace
     */
    protected static boolean isBlank(final String _text) {
        return null == _text || _text.trim().isEmpty();
    }

    protected static long nowEpochSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    /**
     * @param _length
     *            the number of characters wanted
     * @return a random lowercase hexadecimal string of the given length
     */
    protected static String randomHex(final int _length) {
        final StringBuilder sb = new StringBuilder();
        while (sb.length() < _length) {
            sb.append(UUID.randomUUID().toString().replace("-", ""));
        }
        return sb.substring(0, _length);
    }

    /**
     * @param _status
     *            an HTTP status code
     * @return a short reason phrase for common status codes, or "HTTP nnn"
     */
    protected static String reason(final int _status) {
        switch (_status) {
            case 400:
                return "Bad Request";
            case 401:
                return "Unauthorized";
            case 403:
                return "Forbidden";
            case 404:
                return "Not Found";
            case 405:
                return "Method Not Allowed";
            case 408:
                return "Request Timeout";
            case 429:
                return "Too Many Requests";
            case 500:
                return "Internal Server Error";
            case 502:
                return "Bad Gateway";
            case 503:
                return "Service Unavailable";
            case 504:
                return "Gateway Timeout";
            default:
                return "HTTP " + _status;
        }
    }

    /**
     * @return the service name used when recording requests (e.g. "watsonx", "slack")
     */
    public abstract String getServiceName();

    /**
     * Handle the call and send exactly one response.
     *
     * @param _call
     *            the call to handle
     * @throws IOException
     *             if the response cannot be written
     */
    public abstract void handle(HttpCall _call) throws IOException;
}
