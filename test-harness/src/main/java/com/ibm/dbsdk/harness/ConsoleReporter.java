package com.ibm.dbsdk.harness;

import java.util.Optional;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import com.github.theprez.jcmdutils.AppLogger;

/**
 * Prints one colorized line per test as it finishes: PASS (green), FAIL (red), or SKIP (yellow, with the reason). Each test class gets a heading.
 */
final class ConsoleReporter implements TestExecutionListener {

    private final AppLogger m_logger;

    ConsoleReporter(final AppLogger _logger) {
        m_logger = _logger;
    }

    /**
     * @param _throwable
     *            an optional failure cause
     * @return the cause's message, or its type if it has no message
     */
    private static String describe(final Optional<Throwable> _throwable) {
        if (!_throwable.isPresent()) {
            return "";
        }
        final Throwable t = _throwable.get();
        return null == t.getMessage() ? t.getClass().getName() : t.getMessage();
    }

    /**
     * Print a heading when a test class starts.
     *
     * @param _id
     *            the started test or container
     */
    @Override
    public void executionStarted(final TestIdentifier _id) {
        if (_id.isContainer() && _id.getSource().isPresent() && _id.getSource().get() instanceof ClassSource) {
            m_logger.println();
            m_logger.println(_id.getDisplayName());
        }
    }

    /**
     * Print the outcome of a finished test, or of a container that failed (e.g. a {@code @BeforeAll} error, which otherwise would not be visible).
     *
     * @param _id
     *            the finished test or container
     * @param _result
     *            its result
     */
    @Override
    public void executionFinished(final TestIdentifier _id, final TestExecutionResult _result) {
        final boolean isTest = _id.isTest();
        final String name = isTest ? _id.getDisplayName() : _id.getDisplayName() + " (setup/teardown)";
        switch (_result.getStatus()) {
            case SUCCESSFUL:
                if (isTest) {
                    m_logger.println_success("  PASS  " + name);
                }
                break;
            case ABORTED:
                m_logger.println_warn("  SKIP  " + name + " -- " + describe(_result.getThrowable()));
                break;
            case FAILED:
            default:
                m_logger.println_err("  FAIL  " + name);
                m_logger.println_err("        " + describe(_result.getThrowable()).replace("\n", "\n        "));
                if (_result.getThrowable().isPresent()) {
                    m_logger.printExceptionStack_verbose(_result.getThrowable().get());
                }
                break;
        }
    }

    @Override
    public void executionSkipped(final TestIdentifier _id, final String _reason) {
        m_logger.println_warn("  SKIP  " + _id.getDisplayName() + " -- " + _reason);
    }

    @Override
    public void reportingEntryPublished(final TestIdentifier _id, final ReportEntry _entry) {
        m_logger.println_verbose("        " + _entry.getKeyValuePairs());
    }

    @Override
    public void testPlanExecutionStarted(final TestPlan _plan) {
        m_logger.printfln("Running %d tests", Long.valueOf(_plan.countTestIdentifiers(TestIdentifier::isTest)));
    }
}
