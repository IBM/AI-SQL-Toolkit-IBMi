package com.ibm.dbsdk.harness.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.sql.SQLException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.function.Executable;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.dbsdk.harness.Db;
import com.ibm.dbsdk.harness.HarnessConfig;
import com.ibm.dbsdk.harness.HarnessContext;
import com.ibm.dbsdk.harness.MockClient;

/**
 * Base class for the toolkit tests. Each test class gets its own Mapepire connection, and therefore its own IBM i job: the toolkit's {@code *forjob} settings are session global variables, so a
 * class configures them once in a {@code @BeforeAll} method and every test in the class sees them.
 *
 * Every parameter marker is cast ({@link #STR}, {@link #INT}) so that Db2 can resolve the toolkit's overloaded functions and default parameters.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ToolkitTest {

    /** Cast for an integer parameter marker. */
    static final String INT = "cast(? as integer)";
    /** Cast for a string parameter marker. */
    static final String STR = "cast(? as varchar(32000) ccsid 1208)";

    /** Rejects duplicate keys, so tests notice JSON that only "looks" right to lenient parsers. */
    private static final ObjectMapper s_strictMapper = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private HarnessConfig m_config = null;
    private Db m_db = null;
    private MockClient m_mock = null;
    private String m_mockProblem = null;

    /**
     * Assert that a numeric JSON field has the expected value. Accepts numbers and numeric strings, since Db2 may serialize decimals either way.
     *
     * @param _expected
     *            the expected value
     * @param _node
     *            the object holding the field
     * @param _field
     *            the field name
     */
    static void assertNumber(final double _expected, final JsonNode _node, final String _field) {
        final JsonNode value = _node.get(_field);
        assertNotNull(value, "field '" + _field + "' missing from " + _node);
        assertEquals(_expected, value.asDouble(Double.NaN), 1e-9, "field '" + _field + "' in " + _node);
    }

    /**
     * Assert that running some SQL fails with a specific SQLSTATE.
     *
     * @param _sqlState
     *            the expected SQLSTATE, e.g. 38002
     * @param _sql
     *            the code that runs the SQL
     * @return the exception, for further checks on its message
     */
    static SQLException assertSqlState(final String _sqlState, final Executable _sql) {
        final SQLException e = assertThrows(SQLException.class, _sql, "expected SQLSTATE " + _sqlState);
        assertEquals(_sqlState, e.getSQLState(), "SQLSTATE of: " + e.getMessage());
        return e;
    }

    /**
     * Parse JSON strictly (duplicate keys are an error).
     *
     * @param _text
     *            the JSON text
     * @return the parsed document
     */
    static JsonNode json(final String _text) {
        assertNotNull(_text, "expected JSON but got SQL NULL");
        try {
            return s_strictMapper.readTree(_text);
        } catch (final IOException _e) {
            return fail("not valid JSON (" + _e.getMessage() + "): " + _text);
        }
    }

    /**
     * @param _prefix
     *            a readable prefix
     * @return a value unique to this run, for topic names and the like
     */
    static String unique(final String _prefix) {
        return _prefix + "-" + Long.toString(System.currentTimeMillis(), 36);
    }

    /**
     * Run {@code CALL dbsdk_v1.<procedure>}.
     *
     * @param _procedureCall
     *            the procedure and its argument list, e.g. {@code ollama_setportforjob(cast(? as integer))}
     * @param _params
     *            the marker values
     * @throws SQLException
     *             if the call fails
     */
    void call(final String _procedureCall, final Object... _params) throws SQLException {
        m_db.execute("call dbsdk_v1." + _procedureCall, _params);
    }

    /**
     * Evaluate a CLOB-valued expression. The driver cannot read CLOBs, so the value is cast to VARCHAR.
     *
     * @param _expression
     *            the expression
     * @param _params
     *            the marker values
     * @return the value, or null for SQL NULL
     * @throws SQLException
     *             if evaluation fails
     */
    String clobValue(final String _expression, final Object... _params) throws SQLException {
        return value("cast(" + _expression + " as varchar(32000) ccsid 1208)", _params);
    }

    @AfterAll
    final void closeConnection() throws SQLException {
        if (null != m_db) {
            m_db.close();
        }
    }

    HarnessConfig config() {
        return m_config;
    }

    Db db() {
        return m_db;
    }

    /**
     * Get the most recent request the mock recorded for a service. If the mock cannot be reached from this machine, the calling test is skipped from this point on rather than failed.
     *
     * @param _service
     *            the mock service name
     * @return the recorded request
     * @throws IOException
     *             if the mock stops responding
     */
    JsonNode lastRequest(final String _service) throws IOException {
        assumeTrue(null == m_mockProblem, "mock server not reachable from this machine (" + m_mockProblem + "); cannot check what was sent");
        final JsonNode request = m_mock.lastRequest(_service);
        assertNotNull(request, "the mock recorded no " + _service + " request");
        return request;
    }

    /**
     * @param _service
     *            the mock service name
     * @return the parsed JSON body of the most recent request for the service
     * @throws IOException
     *             if the mock stops responding
     */
    JsonNode lastRequestJson(final String _service) throws IOException {
        final JsonNode request = lastRequest(_service);
        final JsonNode body = request.get("json");
        assertTrue(null != body && body.isObject(), "request body was not a JSON object: " + request.path("body").asText());
        return body;
    }

    MockClient mock() {
        assumeTrue(null == m_mockProblem, "mock server not reachable from this machine (" + m_mockProblem + ")");
        return m_mock;
    }

    /**
     * @return the mock server's base URL as the IBM i sees it
     */
    String mockBaseUrl() {
        return m_config.getMockBaseUrlForIbmi();
    }

    String mockHost() {
        return m_config.getMockHost();
    }

    Integer mockPort() {
        return Integer.valueOf(m_config.getMockPort());
    }

    /**
     * Open this class's connection (its IBM i job) and check whether the mock can be inspected from this machine.
     *
     * @throws SQLException
     *             if the connection fails
     */
    @BeforeAll
    final void openConnection() throws SQLException {
        m_config = HarnessContext.getConfig();
        m_db = Db.open(m_config);
        m_mock = new MockClient(m_config.getMockUrl());
        m_mockProblem = m_mock.checkHealth();
    }

    /**
     * Evaluate an expression with {@code select ... from sysibm.sysdummy1}.
     *
     * @param _expression
     *            the expression
     * @param _params
     *            the marker values
     * @return the value as a string, or null for SQL NULL
     * @throws SQLException
     *             if evaluation fails
     */
    String value(final String _expression, final Object... _params) throws SQLException {
        return m_db.queryString("select " + _expression + " from sysibm.sysdummy1", _params);
    }
}
