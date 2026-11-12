package com.ibm.dbsdk.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;

import org.junit.jupiter.api.Test;

/** Unit tests for command-line parsing and tag selection. */
class HarnessConfigTest {

    /**
     * With no options the harness targets local Mapepire and mock servers and runs every test.
     */
    @Test
    void defaults() {
        final HarnessConfig c = HarnessConfig.parse(new String[0]);
        assertEquals("jdbc:mapepire://localhost:8076", c.getJdbcUrl());
        assertEquals("http://localhost:8888", c.getMockBaseUrlForIbmi());
        assertEquals("http://localhost:8888", c.getMockUrl());
        assertNull(c.getUser());
        assertNull(c.getPassword());
        assertFalse(c.isInsecure());
        assertTrue(c.getExcludedTags().isEmpty());
        assertTrue(c.getIncludedTags().isEmpty());
    }

    /**
     * The password never appears in parse errors, even when it is malformed input to another option.
     */
    @Test
    void parseErrorsDoNotEchoPassword() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> HarnessConfig.parse(new String[] { "--password", "s3cr3t-value", "--bogus" }));
        assertFalse(e.getMessage().contains("s3cr3t-value"));
    }

    /**
     * Every option is parsed, in both separate and inline forms.
     */
    @Test
    void parsesOptions() {
        final HarnessConfig c = HarnessConfig.parse(new String[] { "--host", "myibmi", "-P", "9000", "-u", "TESTER", "--password=secret", "--insecure", "--mock-host", "mockbox",
            "--mock-port=9090", "--mock-url", "http://tunnel:7000/", "--query-timeout", "30", "--report-dir", "out", "-v" });
        assertEquals("jdbc:mapepire://myibmi:9000", c.getJdbcUrl());
        assertEquals("TESTER", c.getUser());
        assertEquals("secret", c.getPassword());
        assertTrue(c.isInsecure());
        assertEquals("http://mockbox:9090", c.getMockBaseUrlForIbmi());
        assertEquals("http://tunnel:7000", c.getMockUrl());
        assertEquals(30, c.getQueryTimeoutSeconds());
        assertEquals("out", c.getReportDir());
        assertTrue(c.isVerbose());
    }

    /**
     * Unknown options, missing values and bad numbers are rejected.
     */
    @Test
    void rejectsBadOptions() {
        final IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class, () -> HarnessConfig.parse(new String[] { "--nope" }));
        assertTrue(unknown.getMessage().startsWith("Unknown option"));
        assertThrows(IllegalArgumentException.class, () -> HarnessConfig.parse(new String[] { "--host" }));
        assertThrows(IllegalArgumentException.class, () -> HarnessConfig.parse(new String[] { "--port", "70000" }));
        assertThrows(IllegalArgumentException.class, () -> HarnessConfig.parse(new String[] { "--query-timeout", "0" }));
    }

    /**
     * --skip and --only map to tag filters, lower-cased.
     */
    @Test
    void tagSelection() {
        final HarnessConfig c = HarnessConfig.parse(new String[] { "--skip", "conf,Known-Issue", "--only", "openai" });
        assertEquals(new HashSet<String>(Arrays.asList("conf", "known-issue")), c.getExcludedTags());
        assertEquals(new HashSet<String>(Arrays.asList("openai")), c.getIncludedTags());
    }
}
