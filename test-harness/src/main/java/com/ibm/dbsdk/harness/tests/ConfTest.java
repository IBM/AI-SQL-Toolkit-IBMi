package com.ibm.dbsdk.harness.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * The persistent per-user configuration in {@code DBSDK_V1.CONF}: user registration, every {@code *forme} setter, precedence over job settings, multi-user row isolation, and the
 * {@code CONF_*} admin procedures.
 *
 * These tests write to the running user's CONF row. The row is saved before the first test and restored (or deleted, if it did not exist) after the last one. Skip them with
 * {@code --skip conf}.
 */
@DisplayName("CONF per-user configuration")
@Tag("conf")
final class ConfTest extends ToolkitTest {

    /** A fictional user profile used to check that one user's changes do not touch another's row. */
    private static final String OTHER_USER = "HARNESSX1";
    private static final String USRPRF_MARKER = "cast(? as varchar(10) ccsid 1208)";

    private final Set<String> m_integerColumns = new HashSet<String>();
    private Map<String, String> m_savedRow = null;
    private String m_user = null;

    /**
     * Remember the running user's CONF row (and which columns are integers) so it can be restored afterwards.
     *
     * @throws SQLException
     *             if reading fails
     */
    @BeforeAll
    void saveConfRow() throws SQLException {
        m_user = value("current_user").trim();
        for (final Map<String, String> column : db().queryRows("select column_name, data_type from qsys2.syscolumns where table_schema = 'DBSDK_V1' and table_name = 'CONF'")) {
            if (column.get("DATA_TYPE").trim().toUpperCase(Locale.ROOT).contains("INT")) {
                m_integerColumns.add(column.get("COLUMN_NAME").trim());
            }
        }
        final List<Map<String, String>> rows = confRows(m_user);
        m_savedRow = rows.isEmpty() ? null : rows.get(0);
        db().execute("delete from dbsdk_v1.conf where usrprf = " + USRPRF_MARKER, OTHER_USER);
    }

    /**
     * Put the running user's CONF row back exactly as it was (re-creating it if a test removed it), and remove the fictional user's row. Each column is restored with its own UPDATE: a single
     * statement setting every column is too complex for Db2 (SQL0101).
     *
     * @throws SQLException
     *             if restoring fails; the failure is reported against this class so it is not missed
     */
    @AfterAll
    void restoreConfRow() throws SQLException {
        db().execute("delete from dbsdk_v1.conf where usrprf = " + USRPRF_MARKER, OTHER_USER);
        if (null == m_savedRow) {
            db().execute("delete from dbsdk_v1.conf where usrprf = " + USRPRF_MARKER, m_user);
            return;
        }
        call("conf_register_user()");
        for (final Map.Entry<String, String> column : m_savedRow.entrySet()) {
            if ("USRPRF".equals(column.getKey())) {
                continue;
            }
            final boolean isInteger = m_integerColumns.contains(column.getKey());
            final Object value = null == column.getValue() ? null : isInteger ? Integer.valueOf(column.getValue().trim()) : column.getValue();
            db().execute("update dbsdk_v1.conf set " + column.getKey() + " = " + (isInteger ? INT : STR) + " where usrprf = " + USRPRF_MARKER, value, m_user);
        }
    }

    /**
     * @return the persistent settings: setter, getter, job variable, CONF column (when it differs from the variable) and test value
     */
    private static List<Setting> settings() {
        //@formatter:off
        return Arrays.asList(
            new Setting("ollama_setserverforme", "ollama_getserver", "ollama_server", "conf-ollama-host"),
            new Setting("ollama_setportforme", "ollama_getport", "ollama_port", Integer.valueOf(11435)),
            new Setting("ollama_setmodelforme", "ollama_getmodel", "ollama_model", "conf-ollama-model"),
            new Setting("ollama_setprotocolforme", "ollama_getprotocol", "ollama_protocol", "https"),
            new Setting("openai_compatible_setserverforme", "openai_compatible_getserver", "openai_compatible_server", "conf-openai-host"),
            new Setting("openai_compatible_setportforme", "openai_compatible_getport", "openai_compatible_port", Integer.valueOf(8443)),
            new Setting("openai_compatible_setmodelforme", "openai_compatible_getmodel", "openai_compatible_model", "conf-openai-model"),
            new Setting("openai_compatible_setprotocolforme", "openai_compatible_getprotocol", "openai_compatible_protocol", "https"),
            new Setting("openai_compatible_setapikeyforme", "openai_compatible_getapikey", "openai_compatible_apikey", "conf-openai-key"),
            new Setting("openai_compatible_setbasepathforme", "openai_compatible_getbasepath", "openai_compatible_basepath", "/conf/v1"),
            new Setting("kafka_setbrokerforme", "kafka_getbroker", "kafka_broker", "conf-kafka-broker"),
            new Setting("kafka_setportforme", "kafka_getport", "kafka_port", Integer.valueOf(8083)),
            new Setting("kafka_settopicforme", "kafka_gettopic", "kafka_topic", "conf-kafka-topic"),
            new Setting("kafka_setprotocolforme", "kafka_getprotocol", "kafka_protocol", "https"),
            new Setting("slack_setwebhookforme", "slack_getwebhook", "slack_webhook", "https://example.invalid/conf-hook"),
            new Setting("twilio_setnumberforme", "twilio_getnumber", "twilio_number", "5553334444"),
            new Setting("twilio_setsidforme", "twilio_getsid", "twilio_sid", "ACconfAccountSid"),
            new Setting("twilio_setauthtokenforme", "twilio_getauthtoken", "twilio_authtoken", "conf-auth-token"),
            new Setting("twilio_setbaseurlforme", "twilio_getbaseurl", "twilio_baseurl", "http://conf-twilio.example:8080"),
            new Setting("wx_SetIamBaseUrlForMe", "wx_GetIamBaseUrl", "wx_iam_baseurl", "watsonx_iam_baseurl", "http://conf-iam.example:8080"),
            new Setting("wx_SetMlBaseUrlForMe", "wx_GetMlBaseUrl", "wx_ml_baseurl", "watsonx_ml_baseurl", "http://conf-ml.example:8080"));
        //@formatter:on
    }

    /**
     * Each CONF_UPDATE_* admin procedure writes its columns of the running user's row. Skipped if the admin procedures are not installed.
     *
     * @return one dynamic test per procedure
     */
    @TestFactory
    @DisplayName("CONF_UPDATE_* admin procedures write the user's row")
    List<DynamicTest> adminUpdateProcedures() {
        final List<AdminUpdate> updates = new ArrayList<AdminUpdate>();
        updates.add(new AdminUpdate("conf_update_watsonx", new Object[] { "eu-de", "2024-05-01", "admin-wx-key", "admin-project" }, "WATSONX_REGION", "WATSONX_APIVERSION", "WATSONX_APIKEY",
            "WATSONX_PROJECTID"));
        updates.add(new AdminUpdate("conf_update_ollama", new Object[] { "https", "admin-ollama", Integer.valueOf(1111), "admin-model" }, "OLLAMA_PROTOCOL", "OLLAMA_SERVER", "OLLAMA_PORT",
            "OLLAMA_MODEL"));
        updates.add(new AdminUpdate("conf_update_openai", new Object[] { "https", "admin-openai", Integer.valueOf(2222), "admin-model", "admin-key", "/admin/v1" }, "OPENAI_COMPATIBLE_PROTOCOL",
            "OPENAI_COMPATIBLE_SERVER", "OPENAI_COMPATIBLE_PORT", "OPENAI_COMPATIBLE_MODEL", "OPENAI_COMPATIBLE_APIKEY", "OPENAI_COMPATIBLE_BASEPATH"));
        updates.add(new AdminUpdate("conf_update_kafka", new Object[] { "https", "admin-broker", Integer.valueOf(3333), "admin-topic" }, "KAFKA_PROTOCOL", "KAFKA_BROKER", "KAFKA_PORT",
            "KAFKA_TOPIC"));
        updates.add(new AdminUpdate("conf_update_slack", new Object[] { "https://example.invalid/admin-hook" }, "SLACK_WEBHOOK"));
        updates.add(new AdminUpdate("conf_update_twilio", new Object[] { "5552223333", "ACadminSid", "admin-token" }, "TWILIO_NUMBER", "TWILIO_SID", "TWILIO_AUTHTOKEN"));

        final List<DynamicTest> tests = new ArrayList<DynamicTest>();
        for (final AdminUpdate update : updates) {
            tests.add(DynamicTest.dynamicTest(update.m_procedure.toUpperCase(Locale.ROOT), () -> runAdminUpdate(update)));
        }
        return tests;
    }

    /**
     * Skip the calling test if the optional admin procedures (src/admin/sql) are not installed.
     *
     * @throws SQLException
     *             if the catalog query fails
     */
    private void assumeAdminInstalled() throws SQLException {
        final String count = value("(select count(*) from qsys2.sysroutines where routine_schema = 'DBSDK_V1' and routine_name = 'CONF_GET_USER')");
        assumeTrue(Integer.parseInt(count.trim()) > 0, "admin procedures from src/admin/sql are not installed");
    }

    /**
     * CONF_GET_USER returns the given user's row.
     *
     * @throws SQLException
     *             if a call fails
     */
    @Test
    @DisplayName("CONF_GET_USER returns the user's row")
    void confGetUserReturnsRow() throws SQLException {
        assumeAdminInstalled();
        call("conf_register_user()");
        final List<Map<String, String>> rows = db().callForRows("call dbsdk_v1.conf_get_user(" + USRPRF_MARKER + ")", m_user);
        assertEquals(1, rows.size(), rows.toString());
        assertEquals(m_user, rows.get(0).get("USRPRF").trim());
    }

    /**
     * CONF_LIST_USERS includes the running user once they are registered.
     *
     * @throws SQLException
     *             if a call fails
     */
    @Test
    @DisplayName("CONF_LIST_USERS lists the registered user")
    void confListUsersIncludesUser() throws SQLException {
        assumeAdminInstalled();
        call("conf_register_user()");
        boolean found = false;
        for (final Map<String, String> row : db().callForRows("call dbsdk_v1.conf_list_users()")) {
            found = found || m_user.equals(row.get("USRPRF").trim());
        }
        assertTrue(found, m_user + " not listed");
    }

    private List<Map<String, String>> confRows(final String _user) throws SQLException {
        return db().queryRows("select * from dbsdk_v1.conf where usrprf = " + USRPRF_MARKER, _user);
    }

    @Test
    @DisplayName("conf_initialize creates the user's row once")
    void initializeIsIdempotent() throws SQLException {
        call("conf_initialize()");
        call("conf_initialize()");
        assertEquals(1, confRows(m_user).size());
    }

    /**
     * A job setting wins over the CONF value; clearing it falls back to CONF.
     *
     * @throws SQLException
     *             if a call fails
     */
    @Test
    @DisplayName("a *forjob setting takes precedence over the *forme value")
    void jobSettingOverridesConfValue() throws SQLException {
        call("ollama_setserverforme(" + STR + ")", "conf-value");
        call("ollama_setserverforjob(" + STR + ")", "job-value");
        assertEquals("job-value", value("dbsdk_v1.ollama_getserver()"));
        call("ollama_setserverforjob(" + STR + ")", (Object) null);
        assertEquals("conf-value", value("dbsdk_v1.ollama_getserver()"));
    }

    /**
     * Each *forme setter writes the CONF column, and the getter returns it once the job setting is cleared with the matching *forjob setter and NULL.
     *
     * @return one dynamic test per setting
     */
    @TestFactory
    @DisplayName("*forme setters persist to CONF and are used by the getters")
    List<DynamicTest> persistentSettings() {
        final List<DynamicTest> tests = new ArrayList<DynamicTest>();
        for (final Setting setting : settings()) {
            tests.add(DynamicTest.dynamicTest(setting.m_setter, () -> {
                final boolean isInteger = setting.m_value instanceof Integer;
                call(setting.m_setter + "(" + (isInteger ? INT : STR) + ")", setting.m_value);
                final String stored = confRows(m_user).get(0).get(setting.m_column.toUpperCase(Locale.ROOT));
                assertEquals(String.valueOf(setting.m_value), null == stored ? null : stored.trim(), "CONF column " + setting.m_column);
                call(setting.m_setter.replace("forme", "forjob").replace("ForMe", "ForJob") + "(" + (isInteger ? INT : STR) + ")", (Object) null);
                assertEquals(String.valueOf(setting.m_value), value("dbsdk_v1." + setting.m_getter + "()"), setting.m_getter + "() with no job setting");
            }));
        }
        return tests;
    }

    @Test
    @DisplayName("conf_register_user creates the user's row once")
    void registerUserIsIdempotent() throws SQLException {
        call("conf_register_user()");
        call("conf_register_user()");
        assertEquals(1, confRows(m_user).size());
    }

    /**
     * conf_remove_user deletes only the named user's row. Removing another (here, nonexistent) user leaves the caller's row alone; removing the caller deletes it. The row is restored after the
     * class.
     *
     * @throws SQLException
     *             if a call fails
     */
    @Test
    @DisplayName("conf_remove_user deletes only the named user's row")
    void removeUserDeletesOnlyThatRow() throws SQLException {
        call("conf_register_user()");
        call("conf_remove_user(" + USRPRF_MARKER + ")", OTHER_USER);
        assertFalse(confRows(m_user).isEmpty(), "removing another user must leave the running user's row");
        call("conf_remove_user()");
        assertTrue(confRows(m_user).isEmpty(), "conf_remove_user() should remove the running user's row");
        call("conf_register_user()");
    }

    /**
     * Call one admin update procedure and check the columns it wrote.
     *
     * @param _update
     *            the procedure and its arguments
     * @throws SQLException
     *             if a call fails
     */
    private void runAdminUpdate(final AdminUpdate _update) throws SQLException {
        assumeAdminInstalled();
        call("conf_register_user()");
        final StringBuilder markers = new StringBuilder(USRPRF_MARKER);
        final List<Object> params = new ArrayList<Object>();
        params.add(m_user);
        for (final Object arg : _update.m_args) {
            markers.append(", ").append(arg instanceof Integer ? INT : STR);
            params.add(arg);
        }
        call(_update.m_procedure + "(" + markers + ")", params.toArray());
        final Map<String, String> row = confRows(m_user).get(0);
        for (int i = 0; i < _update.m_columns.length; i++) {
            final String stored = row.get(_update.m_columns[i]);
            assertEquals(String.valueOf(_update.m_args[i]), null == stored ? null : stored.trim(), _update.m_columns[i]);
        }
    }

    /**
     * Regression test for issues #21 and #27: a *forme setter must change only the calling user's row. With the toolkit's row access control in place (src/conf_rcac.sql), another user's row
     * cannot be written at all, which is checked instead; without it, the MERGE isolation is checked directly.
     *
     * @throws SQLException
     *             if a call fails
     */
    @Test
    @DisplayName("*forme setters change only the calling user's row (#21, #27)")
    void setForMeOnlyChangesCallersRow() throws SQLException {
        call("conf_register_user()");
        try {
            call("conf_register_user(" + USRPRF_MARKER + ")", OTHER_USER);
        } catch (final SQLException _e) {
            assertEquals("22542", _e.getSQLState(), "only a row access control rejection is expected here: " + _e.getMessage());
            call("ollama_setserverforme(" + STR + ")", "caller-server");
            assertEquals("caller-server", confRows(m_user).get(0).get("OLLAMA_SERVER").trim());
            return;
        }
        db().execute("update dbsdk_v1.conf set ollama_server = 'other-user-server' where usrprf = " + USRPRF_MARKER, OTHER_USER);
        call("ollama_setserverforme(" + STR + ")", "caller-server");
        assertEquals("caller-server", confRows(m_user).get(0).get("OLLAMA_SERVER").trim());
        assertEquals("other-user-server", confRows(OTHER_USER).get(0).get("OLLAMA_SERVER").trim());
    }

    /** One CONF_UPDATE_* admin procedure call and the columns it should write. */
    private static final class AdminUpdate {

        private final Object[] m_args;
        private final String[] m_columns;
        private final String m_procedure;

        AdminUpdate(final String _procedure, final Object[] _args, final String... _columns) {
            m_procedure = _procedure;
            m_args = _args;
            m_columns = _columns;
        }
    }

    /** One persistent setting: its *forme setter, getter, job variable and CONF column. */
    private static final class Setting {

        private final String m_column;
        private final String m_getter;
        private final String m_setter;
        private final Object m_value;
        private final String m_variable;

        /**
         * A setting whose CONF column has the same name as its job variable.
         *
         * @param _setter
         *            the *forme procedure
         * @param _getter
         *            the getter function
         * @param _variable
         *            the job variable, which is also the CONF column
         * @param _value
         *            the test value
         */
        Setting(final String _setter, final String _getter, final String _variable, final Object _value) {
            this(_setter, _getter, _variable, _variable, _value);
        }

        /**
         * A setting whose CONF column is named differently from its job variable.
         *
         * @param _setter
         *            the *forme procedure
         * @param _getter
         *            the getter function
         * @param _variable
         *            the job variable
         * @param _column
         *            the CONF column
         * @param _value
         *            the test value
         */
        Setting(final String _setter, final String _getter, final String _variable, final String _column, final Object _value) {
            m_setter = _setter;
            m_getter = _getter;
            m_variable = _variable;
            m_column = _column;
            m_value = _value;
        }
    }
}
