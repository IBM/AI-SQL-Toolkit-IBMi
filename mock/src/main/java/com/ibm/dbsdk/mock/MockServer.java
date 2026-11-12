package com.ibm.dbsdk.mock;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

/**
 * Mock server for the AI-SDK-Db2-IBMi (dbsdk_v1) toolkit. Uses only the JDK's built-in {@code com.sun.net.httpserver} so it runs on any Java 8+ runtime, including IBM i.
 */
public final class MockServer {

    private static final int BACKLOG = 128;

    private final MockConfig m_config;
    private ExecutorService m_executor = null;
    private final List<HttpServer> m_httpServers = new ArrayList<HttpServer>();
    private final List<HttpsServer> m_httpsServers = new ArrayList<HttpsServer>();
    private final PrintStream m_log;
    private final MockState m_state;
    private final CountDownLatch m_stopped = new CountDownLatch(1);

    /**
     * Create a server that logs to standard output unless the configuration asks for quiet operation.
     *
     * @param _config
     *            the server configuration
     */
    public MockServer(final MockConfig _config) {
        this(_config, _config.isQuiet() ? null : System.out);
    }

    /**
     * Create a server with an explicit log destination.
     *
     * @param _config
     *            the server configuration
     * @param _log
     *            where to log, or null for no logging
     */
    public MockServer(final MockConfig _config, final PrintStream _log) {
        m_config = _config;
        m_state = new MockState(_config);
        m_log = _log;
    }

    /**
     * Command-line entry point. Starts the server and blocks until the JVM is shut down (e.g. with Ctrl+C).
     *
     * @param _args
     *            the command-line arguments; see {@link MockConfig#usage()}
     */
    public static void main(final String[] _args) {
        final MockConfig config;
        try {
            config = MockConfig.parse(_args);
        } catch (final IllegalArgumentException _e) {
            System.err.println("Error: " + _e.getMessage());
            System.err.println();
            System.err.println(MockConfig.usage());
            System.exit(2);
            return;
        }
        if (config.isHelp()) {
            System.out.println(MockConfig.usage());
            return;
        }
        if (config.isVersion()) {
            System.out.println("dbsdk-mock-server " + version());
            return;
        }

        final MockServer server = new MockServer(config);
        try {
            server.start();
        } catch (final IOException | GeneralSecurityException | RuntimeException _e) {
            MockLog.error("Failed to start mock server", _e);
            System.exit(1);
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                server.stop();
            }
        }, "mock-shutdown"));
        server.awaitStop();
        MockLog.info("dbsdk-mock-server stopped");
    }

    /**
     * Block until the server has been stopped, by {@link #stop()}, {@code POST /mock/shutdown}, or JVM shutdown.
     */
    public void awaitStop() {
        try {
            m_stopped.await();
        } catch (final InterruptedException _e) {
            MockLog.info("Interrupted while waiting for the server to stop");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * @return the implementation version from the jar manifest, or "dev" when run from classes
     */
    public static String version() {
        final Package pkg = MockServer.class.getPackage();
        final String version = null == pkg ? null : pkg.getImplementationVersion();
        return null == version ? "dev" : version;
    }

    public MockState getState() {
        return m_state;
    }

    /**
     * @return the actual bound HTTP ports (useful when port 0 was requested)
     */
    public synchronized List<Integer> httpPorts() {
        final List<Integer> ports = new ArrayList<Integer>();
        for (final HttpServer s : m_httpServers) {
            ports.add(Integer.valueOf(s.getAddress().getPort()));
        }
        return ports;
    }

    /**
     * @return the actual bound HTTPS ports
     */
    public synchronized List<Integer> httpsPorts() {
        final List<Integer> ports = new ArrayList<Integer>();
        for (final HttpsServer s : m_httpsServers) {
            ports.add(Integer.valueOf(s.getAddress().getPort()));
        }
        return ports;
    }

    /**
     * Start all configured listeners. If any listener fails to start, the ones already started are stopped again.
     *
     * @throws IOException
     *             if a port cannot be bound or the keystore cannot be read
     * @throws GeneralSecurityException
     *             if the TLS context cannot be created
     */
    public synchronized void start() throws IOException, GeneralSecurityException {
        MockLog.setOutput(m_log);
        m_executor = Executors.newCachedThreadPool(new ThreadFactory() {
            private final AtomicInteger m_count = new AtomicInteger();

            @Override
            public Thread newThread(final Runnable _task) {
                final Thread t = new Thread(_task, "mock-http-" + m_count.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
        m_state.setShutdownAction(this::stop);
        final Router router = new Router(m_state);
        try {
            for (final Integer port : m_config.getHttpPorts()) {
                final HttpServer s = HttpServer.create(new InetSocketAddress(m_config.getBindAddress(), port.intValue()), BACKLOG);
                s.createContext("/", router);
                s.setExecutor(m_executor);
                s.start();
                m_httpServers.add(s);
            }
            if (!m_config.getHttpsPorts().isEmpty()) {
                final SSLContext ssl = Tls.context(m_config);
                for (final Integer port : m_config.getHttpsPorts()) {
                    final HttpsServer s = HttpsServer.create(new InetSocketAddress(m_config.getBindAddress(), port.intValue()), BACKLOG);
                    s.setHttpsConfigurator(new HttpsConfigurator(ssl));
                    s.createContext("/", router);
                    s.setExecutor(m_executor);
                    s.start();
                    m_httpsServers.add(s);
                }
            }
        } catch (final IOException | GeneralSecurityException | RuntimeException _e) {
            stop();
            throw _e;
        }
        logBanner();
    }

    /**
     * Stop all listeners and release {@link #awaitStop()}. Safe to call more than once.
     */
    public synchronized void stop() {
        for (final HttpServer s : m_httpServers) {
            s.stop(0);
        }
        for (final HttpsServer s : m_httpsServers) {
            s.stop(0);
        }
        m_httpServers.clear();
        m_httpsServers.clear();
        if (null != m_executor) {
            m_executor.shutdownNow();
            m_executor = null;
        }
        m_stopped.countDown();
    }

    /**
     * Log the startup summary. Credentials from the configuration are deliberately not included.
     */
    private void logBanner() {
        MockLog.info("dbsdk-mock-server " + version() + " started");
        for (final Integer p : httpPorts()) {
            MockLog.info("  HTTP  listening on " + m_config.getBindAddress() + ":" + p);
        }
        for (final Integer p : httpsPorts()) {
            MockLog.info("  HTTPS listening on " + m_config.getBindAddress() + ":" + p);
        }
        MockLog.info("  OpenAI flavor: " + m_config.getOpenAiFlavor().name().toLowerCase(Locale.ROOT) + ", token TTL: " + m_config.getTokenTtlSeconds() + "s");
        MockLog.info("  GET /mock/health for the endpoint catalogue. Press Ctrl+C to stop.");
    }
}
