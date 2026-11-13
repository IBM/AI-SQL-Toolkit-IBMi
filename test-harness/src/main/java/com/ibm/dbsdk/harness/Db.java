package com.ibm.dbsdk.harness;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import io.github.mapepire_ibmi.MapepireDriver;

/**
 * A Mapepire JDBC connection with small helpers for the statement shapes the tests use. Every Mapepire connection is its own IBM i job, so session global variables set through one {@code Db}
 * are not visible through another.
 *
 * Values are always read with {@code getString}: the driver does not support {@code getClob}, so tests cast CLOB-returning functions to VARCHAR in their SQL.
 */
public final class Db implements AutoCloseable {

    private final Connection m_conn;
    private final int m_queryTimeoutSeconds;

    private Db(final Connection _conn, final int _queryTimeoutSeconds) {
        m_conn = _conn;
        m_queryTimeoutSeconds = _queryTimeoutSeconds;
    }

    /**
     * Open a connection to the configured Mapepire server. The driver does not register itself with {@code DriverManager}, so it is invoked directly.
     *
     * @param _config
     *            the run configuration, including credentials
     * @return the open connection
     * @throws SQLException
     *             if the connection fails
     */
    public static Db open(final HarnessConfig _config) throws SQLException {
        final Properties props = new Properties();
        props.setProperty("USER", _config.getUser());
        props.setProperty("PASSWORD", _config.getPassword());
        props.setProperty("REJECTUNAUTHORIZED", _config.isInsecure() ? "false" : "true");
        final Connection conn = new MapepireDriver().connect(_config.getJdbcUrl(), props);
        if (null == conn) {
            throw new SQLException("The Mapepire driver did not accept URL " + _config.getJdbcUrl());
        }
        return new Db(conn, _config.getQueryTimeoutSeconds());
    }

    /**
     * Read every row of a result set into maps keyed by upper-case column label, in column order.
     *
     * @param _rs
     *            the result set
     * @return the rows
     * @throws SQLException
     *             if reading fails
     */
    private static List<Map<String, String>> readRows(final ResultSet _rs) throws SQLException {
        final ResultSetMetaData md = _rs.getMetaData();
        final int columns = md.getColumnCount();
        final List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
        while (_rs.next()) {
            final Map<String, String> row = new LinkedHashMap<String, String>();
            for (int c = 1; c <= columns; c++) {
                row.put(md.getColumnLabel(c).toUpperCase(Locale.ROOT), _rs.getString(c));
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * Run a {@code CALL} that returns a dynamic result set and read its rows.
     *
     * @param _sql
     *            the CALL statement, with {@code ?} markers
     * @param _params
     *            the marker values
     * @return the rows of the first result set, or an empty list if the procedure returned none
     * @throws SQLException
     *             if the statement fails
     */
    public List<Map<String, String>> callForRows(final String _sql, final Object... _params) throws SQLException {
        try (final PreparedStatement ps = prepare(_sql, _params)) {
            if (!ps.execute()) {
                return new ArrayList<Map<String, String>>();
            }
            try (final ResultSet rs = ps.getResultSet()) {
                return readRows(rs);
            }
        }
    }

    @Override
    public void close() throws SQLException {
        m_conn.close();
    }

    /**
     * Run a statement that returns no rows ({@code CALL}, {@code SET}, {@code INSERT}, ...).
     *
     * @param _sql
     *            the statement, with {@code ?} markers
     * @param _params
     *            the marker values
     * @throws SQLException
     *             if the statement fails
     */
    public void execute(final String _sql, final Object... _params) throws SQLException {
        try (final PreparedStatement ps = prepare(_sql, _params)) {
            ps.execute();
        }
    }

    /**
     * Prepare a statement and bind its markers. Null values are bound as SQL NULL, so the SQL should cast each marker to its intended type.
     *
     * @param _sql
     *            the statement
     * @param _params
     *            the marker values: String, Integer or null
     * @return the prepared statement; the caller must close it
     * @throws SQLException
     *             if preparing or binding fails
     */
    private PreparedStatement prepare(final String _sql, final Object... _params) throws SQLException {
        final PreparedStatement ps = m_conn.prepareStatement(_sql);
        try {
            ps.setQueryTimeout(m_queryTimeoutSeconds);
            for (int i = 0; i < _params.length; i++) {
                final Object p = _params[i];
                if (null == p) {
                    ps.setNull(i + 1, Types.VARCHAR);
                } else if (p instanceof Integer) {
                    ps.setInt(i + 1, ((Integer) p).intValue());
                } else {
                    ps.setString(i + 1, p.toString());
                }
            }
            return ps;
        } catch (final SQLException _e) {
            ps.close();
            throw _e;
        }
    }

    /**
     * Run a query and read all rows.
     *
     * @param _sql
     *            the query, with {@code ?} markers
     * @param _params
     *            the marker values
     * @return the rows, keyed by upper-case column label
     * @throws SQLException
     *             if the query fails
     */
    public List<Map<String, String>> queryRows(final String _sql, final Object... _params) throws SQLException {
        try (final PreparedStatement ps = prepare(_sql, _params); final ResultSet rs = ps.executeQuery()) {
            return readRows(rs);
        }
    }

    /**
     * Run a query and return the first column of the first row.
     *
     * @param _sql
     *            the query, with {@code ?} markers
     * @param _params
     *            the marker values
     * @return the value, or null if it is SQL NULL or there are no rows
     * @throws SQLException
     *             if the query fails
     */
    public String queryString(final String _sql, final Object... _params) throws SQLException {
        try (final PreparedStatement ps = prepare(_sql, _params); final ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}
