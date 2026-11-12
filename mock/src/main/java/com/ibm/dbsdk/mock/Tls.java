package com.ibm.dbsdk.mock;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/** Builds the {@link SSLContext} for HTTPS listeners, generating a self-signed keystore if needed. */
final class Tls {

    static final String GENERATED_CERT = "mock-server.pem";
    static final String GENERATED_KEYSTORE = "mock-server.p12";
    static final String KEY_ALIAS = "dbsdk-mock";
    /** keytool reads the store password from this variable so it never appears on a command line. */
    private static final String PASSWORD_ENV = "DBSDK_MOCK_STOREPASS";

    /** Host names the toolkit hard-codes (watsonx and Twilio) plus local names, used as SANs. */
    private static final List<String> s_defaultDnsNames = Arrays.asList("localhost", "iam.cloud.ibm.com", "us-south.ml.cloud.ibm.com", "eu-de.ml.cloud.ibm.com", "eu-gb.ml.cloud.ibm.com",
        "jp-tok.ml.cloud.ibm.com", "au-syd.ml.cloud.ibm.com", "ca-tor.ml.cloud.ibm.com", "api.twilio.com");

    private Tls() {
    }

    /**
     * Create the server-side TLS context from the configured keystore, generating {@value #GENERATED_KEYSTORE} in the working directory if no keystore was configured and none exists yet.
     *
     * @param _config
     *            the server configuration
     * @return an initialized TLS context
     * @throws IOException
     *             if the keystore cannot be read or generated
     * @throws GeneralSecurityException
     *             if the keystore is invalid or the password is wrong
     */
    static SSLContext context(final MockConfig _config) throws IOException, GeneralSecurityException {
        final char[] password = _config.getKeystorePassword().toCharArray();
        final String path;
        if (null == _config.getKeystorePath()) {
            final File generated = new File(GENERATED_KEYSTORE);
            if (generated.isFile()) {
                MockLog.info("Using existing keystore " + generated.getAbsolutePath());
            } else {
                generate(generated, _config.getKeystorePassword());
            }
            path = generated.getPath();
        } else {
            path = _config.getKeystorePath();
        }
        final KeyStore ks = KeyStore.getInstance(path.toLowerCase(Locale.ROOT).endsWith(".jks") ? "JKS" : "PKCS12");
        try (final InputStream in = new FileInputStream(path)) {
            ks.load(in, password);
        }
        final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password);
        final SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx;
    }

    /**
     * Generate a self-signed PKCS12 keystore with keytool and export its certificate as PEM next to it. The certificate's SANs cover the hosts the toolkit hard-codes, so DNS overrides on IBM i can
     * point them at the mock.
     *
     * @param _keystore
     *            the keystore file to create
     * @param _password
     *            the store and key password
     * @throws IOException
     *             if keytool cannot be run or fails
     */
    static void generate(final File _keystore, final String _password) throws IOException {
        final String keytool = keytoolPath();
        final Set<String> sans = new LinkedHashSet<String>();
        for (final String dns : s_defaultDnsNames) {
            sans.add("dns:" + dns);
        }
        final String host = localHostName();
        if (null != host && !host.isEmpty()) {
            sans.add("dns:" + host.toLowerCase(Locale.ROOT));
        }
        sans.add("ip:127.0.0.1");

        //@formatter:off
        run(Arrays.asList(keytool, "-genkeypair",
            "-alias", KEY_ALIAS,
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-validity", "3650",
            "-storetype", "PKCS12",
            "-keystore", _keystore.getPath(),
            "-storepass:env", PASSWORD_ENV,
            "-keypass:env", PASSWORD_ENV,
            "-dname", "CN=dbsdk-mock-server, OU=Testing, O=AI-SDK-Db2-IBMi",
            "-ext", "SAN=" + String.join(",", sans),
            "-ext", "BC:c=ca:true"), _password);

        final File pem = new File(_keystore.getAbsoluteFile().getParentFile(), GENERATED_CERT);
        run(Arrays.asList(keytool, "-exportcert", "-rfc",
            "-alias", KEY_ALIAS,
            "-keystore", _keystore.getPath(),
            "-storetype", "PKCS12",
            "-storepass:env", PASSWORD_ENV,
            "-file", pem.getPath()), _password);
        //@formatter:on

        MockLog.info("Generated self-signed keystore " + _keystore.getAbsolutePath());
        MockLog.info("Certificate exported to " + pem.getAbsolutePath() + " (import it into the IBM i *SYSTEM certificate store to trust the mock)");
    }

    /**
     * Locate keytool in the running JDK/JRE, falling back to the PATH.
     *
     * @return the keytool executable
     */
    private static String keytoolPath() {
        final String exe = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows") ? "keytool.exe" : "keytool";
        final File javaHome = new File(System.getProperty("java.home"));
        // JDK 8 sets java.home to the embedded jre directory, so also look one level up
        final List<File> candidates = new ArrayList<File>();
        candidates.add(new File(javaHome, "bin" + File.separator + exe));
        if (null != javaHome.getParentFile()) {
            candidates.add(new File(javaHome.getParentFile(), "bin" + File.separator + exe));
        }
        for (final File f : candidates) {
            if (f.isFile()) {
                return f.getPath();
            }
        }
        return "keytool";
    }

    /**
     * @return the local host name, or null if it cannot be determined (logged)
     */
    private static String localHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (final IOException _e) {
            MockLog.warn("Could not determine the local host name; it will not be added to the certificate", _e);
            return null;
        }
    }

    /**
     * Run keytool, passing the password through {@value #PASSWORD_ENV}.
     *
     * @param _command
     *            the command line, without the password
     * @param _password
     *            the keystore password
     * @throws IOException
     *             if keytool cannot be started, times out, or exits non-zero
     */
    private static void run(final List<String> _command, final String _password) throws IOException {
        final ProcessBuilder pb = new ProcessBuilder(_command);
        pb.environment().put(PASSWORD_ENV, _password);
        pb.redirectErrorStream(true);
        final Process p = pb.start();
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (final InputStream in = p.getInputStream()) {
            final byte[] buf = new byte[4096];
            int n = in.read(buf);
            while (-1 != n) {
                output.write(buf, 0, n);
                n = in.read(buf);
            }
        }
        try {
            if (!p.waitFor(60, TimeUnit.SECONDS)) {
                p.destroy();
                throw new IOException("keytool timed out");
            }
        } catch (final InterruptedException _e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running keytool", _e);
        }
        if (0 != p.exitValue()) {
            throw new IOException("keytool failed (exit " + p.exitValue() + "): " + new String(output.toByteArray(), StandardCharsets.UTF_8) + System.lineSeparator()
                + "Provide a keystore with --keystore instead.");
        }
    }
}
