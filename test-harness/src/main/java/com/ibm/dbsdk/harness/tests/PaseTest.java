package com.ibm.dbsdk.harness.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** {@code pase_call}, which runs a PASE command through XMLSERVICE. Needs XMLSERVICE (QXMLSERV) on the IBM i but not the mock server. */
@DisplayName("PASE")
@Tag("pase")
final class PaseTest extends ToolkitTest {

    @Test
    @DisplayName("ignores standard error")
    void ignoresStandardError() throws SQLException {
        assertEquals(Arrays.asList("to-stdout"), pase("echo to-stdout; echo to-stderr 1>&2"));
    }

    /**
     * Run a PASE command and collect its output lines.
     *
     * @param _command
     *            the command
     * @return the output lines, in order
     * @throws SQLException
     *             if the call fails
     */
    private List<String> pase(final String _command) throws SQLException {
        final List<String> lines = new ArrayList<String>();
        for (final Map<String, String> row : db().queryRows("select output from table(dbsdk_v1.pase_call(" + STR + ")) x", _command)) {
            lines.add(row.get("OUTPUT"));
        }
        return lines;
    }

    @Test
    @DisplayName("returns one row per output line, in order")
    void returnsOneRowPerLine() throws SQLException {
        assertEquals(Arrays.asList("one", "two", "three"), pase("printf 'one\\ntwo\\nthree\\n'"));
    }

    @Test
    @DisplayName("returns the command's standard output")
    void returnsStandardOutput() throws SQLException {
        assertEquals(Arrays.asList("harness-ok"), pase("echo harness-ok"));
    }
}
