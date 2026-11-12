package com.ibm.dbsdk.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.platform.engine.TestTag;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherFactory;

/** Unit tests for test discovery and the harness's exit codes. No IBM i is needed. */
class HarnessMainTest {

    /**
     * Discover the toolkit tests for a set of options.
     *
     * @param _args
     *            command-line options
     * @return the number and tags of the discovered tests
     */
    private static Discovery discover(final String... _args) {
        final TestPlan plan = LauncherFactory.create().discover(HarnessMain.buildRequest(HarnessConfig.parse(_args)));
        final Discovery d = new Discovery();
        for (final TestIdentifier root : plan.getRoots()) {
            for (final TestIdentifier id : plan.getDescendants(root)) {
                if (id.isTest()) {
                    d.m_count++;
                }
                for (final TestTag tag : id.getTags()) {
                    d.m_tags.add(tag.getName());
                }
            }
        }
        return d;
    }

    @Test
    void badOptionExitsTwo() {
        assertEquals(HarnessMain.EXIT_USAGE, HarnessMain.run(new String[] { "--bogus" }));
    }

    /**
     * By default every suite is discovered.
     */
    @Test
    void defaultDiscoveryFindsEverySuite() {
        final Discovery d = discover();
        // @TestFactory children are only created at execution time, so they are not counted here
        assertTrue(d.m_count > 60, "expected the full suite, found " + d.m_count);
        for (final String tag : new String[] { "json", "conf", "ollama", "openai", "kafka", "slack", "twilio", "watsonx", "pase", "known-issue" }) {
            assertTrue(d.m_tags.contains(tag), "no tests tagged " + tag);
        }
    }

    @Test
    void helpAndListExitZero() {
        assertEquals(HarnessMain.EXIT_OK, HarnessMain.run(new String[] { "--help" }));
        assertEquals(HarnessMain.EXIT_OK, HarnessMain.run(new String[] { "--list", "--only", "json" }));
    }

    /**
     * --only selects the tests with that tag; some of them may also be tagged known-issue.
     */
    @Test
    void onlySelectsTaggedTests() {
        final Discovery d = discover("--only", "openai");
        assertTrue(d.m_count > 30, "found " + d.m_count);
        final Set<String> others = new HashSet<String>(d.m_tags);
        others.remove("openai");
        others.remove(HarnessConfig.TAG_KNOWN_ISSUE);
        assertTrue(d.m_tags.contains("openai") && others.isEmpty(), d.m_tags.toString());
    }

    @Test
    void skipKnownIssuesRemovesThem() {
        assertFalse(discover("--skip", "known-issue").m_tags.contains("known-issue"));
    }

    /**
     * A Mapepire server that cannot be reached is reported once, as a usage/environment error, without running any test.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void unreachableServerExitsTwo() {
        assertEquals(HarnessMain.EXIT_USAGE, HarnessMain.run(new String[] { "--host", "127.0.0.1", "--port", "1", "--user", "NOBODY", "--password", "x" }));
    }

    /** What a discovery run found. */
    private static final class Discovery {

        private int m_count = 0;
        private final Set<String> m_tags = new HashSet<String>();
    }
}
