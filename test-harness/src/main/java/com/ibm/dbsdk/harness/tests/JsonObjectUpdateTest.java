package com.ibm.dbsdk.harness.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.SQLException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.ibm.dbsdk.harness.HarnessConfig;

/**
 * {@code json_object_update}, with the cases and expected results formerly documented in {@code test/test_json_object_update.sql}. Results are compared as JSON, and duplicate keys count as
 * invalid.
 */
@DisplayName("json_object_update")
@Tag("json")
final class JsonObjectUpdateTest extends ToolkitTest {

    /**
     * Assert that merging two objects gives the expected object.
     *
     * @param _base
     *            the base object text, or null
     * @param _update
     *            the update object text, or null
     * @param _expected
     *            the expected merged object text
     * @throws SQLException
     *             if the call fails
     */
    private void assertMerge(final String _base, final String _update, final String _expected) throws SQLException {
        final String merged = clobValue("dbsdk_v1.json_object_update(" + STR + ", " + STR + ")", _base, _update);
        assertEquals(json(_expected), json(merged), "merged: " + merged);
    }

    @Test
    @DisplayName("merges two objects")
    void basicMerge() throws SQLException {
        assertMerge("{\"name\": \"John\", \"age\": 30}", "{\"city\": \"New York\", \"country\": \"USA\"}", "{\"name\": \"John\", \"age\": 30, \"city\": \"New York\", \"country\": \"USA\"}");
    }

    @Test
    @Tag(HarnessConfig.TAG_KNOWN_ISSUE)
    @DisplayName("two NULL objects give {} [known issue]")
    void bothNull() throws SQLException {
        assertMerge(null, null, "{}");
    }

    @Test
    @DisplayName("an empty base object returns the update object")
    void emptyBase() throws SQLException {
        assertMerge("{}", "{\"name\": \"John\", \"age\": 30}", "{\"name\": \"John\", \"age\": 30}");
    }

    @Test
    @DisplayName("an empty update object returns the base object")
    void emptyUpdate() throws SQLException {
        assertMerge("{\"name\": \"John\", \"age\": 30}", "{}", "{\"name\": \"John\", \"age\": 30}");
    }

    /**
     * Objects built with JSON_OBJECT work as either argument.
     *
     * @throws SQLException
     *             if a call fails
     */
    @Test
    @DisplayName("accepts JSON_OBJECT values as either argument")
    void jsonObjectArguments() throws SQLException {
        final String expected = "{\"name\": \"John\", \"age\": 30, \"occupation\": \"Dev\"}";
        assertEquals(json(expected), json(clobValue("dbsdk_v1.json_object_update('{\"name\": \"John\", \"age\": 30}', json_object('occupation': 'Dev'))")));
        assertEquals(json(expected), json(clobValue("dbsdk_v1.json_object_update(json_object('occupation': 'Dev'), '{\"name\": \"John\", \"age\": 30}')")));
    }

    /**
     * JSON booleans survive a merge, including ones built by JSON_OBJECT from BOOLEAN values (the BOOLEAN part needs a Db2 for i release with the BOOLEAN type).
     *
     * @throws SQLException
     *             if a call fails
     */
    @Test
    @DisplayName("keeps JSON booleans, including ones built from BOOLEAN values")
    void mergeBooleans() throws SQLException {
        assertMerge("{\"string_true\": \"true\", \"boolean_true\": true}", "{\"boolean_false\": false}", "{\"string_true\": \"true\", \"boolean_true\": true, \"boolean_false\": false}");
        final String merged;
        try {
            merged = clobValue("dbsdk_v1.json_object_update(json_object('string_true': 'true', 'boolean_true': cast('true' as boolean)), json_object('boolean_false': cast('false' as boolean)))");
        } catch (final SQLException _e) {
            assumeTrue(false, "this Db2 for i release has no BOOLEAN type: " + _e.getMessage());
            return;
        }
        assertEquals(json("{\"string_true\": \"true\", \"boolean_true\": true, \"boolean_false\": false}"), json(merged), "merged: " + merged);
    }

    @Test
    @DisplayName("merges objects containing arrays")
    void mergeArrays() throws SQLException {
        assertMerge("{\"numbers\": [1, 2, 3]}", "{\"letters\": [\"a\", \"b\", \"c\"]}", "{\"numbers\": [1, 2, 3], \"letters\": [\"a\", \"b\", \"c\"]}");
    }

    @Test
    @DisplayName("merges nested objects")
    void mergeNested() throws SQLException {
        assertMerge("{\"person\": {\"name\": \"John\", \"age\": 30}}", "{\"address\": {\"city\": \"New York\", \"country\": \"USA\"}}",
            "{\"person\": {\"name\": \"John\", \"age\": 30}, \"address\": {\"city\": \"New York\", \"country\": \"USA\"}}");
    }

    @Test
    @DisplayName("a NULL base object returns the update object")
    void nullBase() throws SQLException {
        assertMerge(null, "{\"name\": \"John\", \"age\": 30}", "{\"name\": \"John\", \"age\": 30}");
    }

    @Test
    @DisplayName("a NULL update object returns the base object")
    void nullUpdate() throws SQLException {
        assertMerge("{\"name\": \"John\", \"age\": 30}", null, "{\"name\": \"John\", \"age\": 30}");
    }

    /**
     * Keys present in both objects take the update's value. The function concatenates the objects instead, producing duplicate keys.
     *
     * @throws SQLException
     *             if the call fails
     */
    @Test
    @Tag(HarnessConfig.TAG_KNOWN_ISSUE)
    @DisplayName("update values overwrite base values for the same key [known issue]")
    void overwriteProperties() throws SQLException {
        assertMerge("{\"name\": \"John\", \"age\": 30}", "{\"age\": 31, \"occupation\": \"Developer\"}", "{\"name\": \"John\", \"age\": 31, \"occupation\": \"Developer\"}");
    }

    @Test
    @DisplayName("tolerates whitespace around and inside the objects")
    void whitespace() throws SQLException {
        assertMerge("  {  \"name\"  :  \"John\"  }  ", "  {  \"age\"  :  30  }  ", "{\"name\": \"John\", \"age\": 30}");
    }
}
