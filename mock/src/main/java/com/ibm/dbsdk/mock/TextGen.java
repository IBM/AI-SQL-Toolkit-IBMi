package com.ibm.dbsdk.mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Deterministic "LLM" used by all generation endpoints. */
public final class TextGen {

    private static final int MAX_ECHO_CHARS = 200;

    private TextGen() {
    }

    /**
     * @param _text
     *            the text to measure; may be null
     * @return an approximate token count: the number of whitespace-delimited words
     */
    public static int countTokens(final String _text) {
        return words(_text).size();
    }

    /**
     * Build the reply for a prompt. The reply is deterministic so tests can assert on it: {@code Mock <provider> response from <model>: <prompt>}. A {@code [[mock:reply=...]]} directive replaces it
     * entirely.
     *
     * @param _provider
     *            the provider name used in the reply
     * @param _model
     *            the model name used in the reply; may be null or empty
     * @param _prompt
     *            the prompt to echo; may be null
     * @param _directives
     *            the directives found in the request
     * @param _maxTokens
     *            if non-null and positive, the reply is cut to this many whitespace tokens
     * @return the generated reply
     */
    public static Result generate(final String _provider, final String _model, final String _prompt, final Directives _directives, final Integer _maxTokens) {
        if (null != _directives.getReply()) {
            return limit(_directives.getReply(), _maxTokens);
        }
        final String cleaned = Directives.strip(null == _prompt ? "" : _prompt);
        final String echoed = cleaned.length() > MAX_ECHO_CHARS ? cleaned.substring(0, MAX_ECHO_CHARS) + "..." : cleaned;
        final String model = (null == _model || _model.isEmpty()) ? "default-model" : _model;
        return limit("Mock " + _provider + " response from " + model + ": " + echoed, _maxTokens);
    }

    /**
     * Truncate text to at most a number of whitespace-delimited tokens.
     *
     * @param _text
     *            the text to truncate
     * @param _maxTokens
     *            the limit; null or non-positive means no limit
     * @return the possibly truncated text
     */
    public static Result limit(final String _text, final Integer _maxTokens) {
        final List<String> words = words(_text);
        if (null == _maxTokens || _maxTokens.intValue() <= 0 || words.size() <= _maxTokens.intValue()) {
            return new Result(_text, words.size(), false);
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < _maxTokens.intValue(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(words.get(i));
        }
        return new Result(sb.toString(), _maxTokens.intValue(), true);
    }

    /**
     * @param _text
     *            the text to split; may be null
     * @return the whitespace-delimited words of the text
     */
    public static List<String> words(final String _text) {
        if (null == _text || _text.trim().isEmpty()) {
            return new ArrayList<String>();
        }
        return new ArrayList<String>(Arrays.asList(_text.trim().split("\\s+")));
    }

    /** Result of generating (and possibly truncating) a reply. */
    public static final class Result {

        private final String m_text;
        private final int m_tokens;
        private final boolean m_truncated;

        Result(final String _text, final int _tokens, final boolean _truncated) {
            m_text = _text;
            m_tokens = _tokens;
            m_truncated = _truncated;
        }

        public String getText() {
            return m_text;
        }

        public int getTokens() {
            return m_tokens;
        }

        public boolean isTruncated() {
            return m_truncated;
        }
    }
}
