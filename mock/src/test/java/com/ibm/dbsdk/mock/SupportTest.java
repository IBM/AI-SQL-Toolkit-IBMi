package com.ibm.dbsdk.mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for configuration parsing, directives, text generation, redaction and TLS. */
class SupportTest {

    /**
     * @return a TLS context that trusts any server certificate, for talking to the self-signed mock
     * @throws Exception
     *             if the context cannot be created
     */
    private static SSLContext trustAllContext() throws Exception {
        final SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new TrustManager[] { new X509TrustManager() {
            @Override
            public void checkClientTrusted(final X509Certificate[] _chain, final String _authType) {
                // trust everything: test-only
            }

            @Override
            public void checkServerTrusted(final X509Certificate[] _chain, final String _authType) {
                // trust everything: test-only
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        } }, null);
        return ctx;
    }

    @Test
    void defaultsToPort8080() {
        assertEquals(Integer.valueOf(8080), MockConfig.parse(new String[0]).getHttpPorts().get(0));
    }

    /**
     * Status, delay and reply directives are parsed, and strip() removes them.
     */
    @Test
    void directives() {
        final Directives d = Directives.parse("hello [[mock:status=503]] [[mock:delay=10]] [[mock:reply=a b]]");
        assertEquals(Integer.valueOf(503), d.getStatus());
        assertEquals(10, d.getDelayMs());
        assertEquals("a b", d.getReply());
        assertTrue(d.isErrorRequested());
        assertEquals("hello", Directives.strip("hello [[mock:status=503]]"));
        assertFalse(Directives.parse("plain").isErrorRequested());
        assertNull(Directives.parse((String) null).getStatus());
    }

    /**
     * Generate a keystore with keytool, serve HTTPS from it, and complete an IAM request over TLS.
     *
     * @param _dir
     *            a temporary directory for the keystore
     * @throws Exception
     *             if generation, startup or the request fails
     */
    @Test
    void httpsWithGeneratedKeystore(@TempDir final File _dir) throws Exception {
        final File ks = new File(_dir, "test.p12");
        Tls.generate(ks, "changeit");
        assertTrue(ks.isFile());
        assertTrue(new File(_dir, Tls.GENERATED_CERT).isFile());
        final KeyStore loaded = KeyStore.getInstance("PKCS12");
        try (final InputStream in = new FileInputStream(ks)) {
            loaded.load(in, "changeit".toCharArray());
        }
        assertTrue(loaded.containsAlias(Tls.KEY_ALIAS));

        final MockServer server = new MockServer(new MockConfig().setBindAddress("127.0.0.1").addHttpsPort(0).setKeystorePath(ks.getPath()), null);
        server.start();
        try {
            assertTrue(server.httpPorts().isEmpty());
            final TestClient client = new TestClient("https://127.0.0.1:" + server.httpsPorts().get(0)).withSsl(trustAllContext());
            final TestClient.Response r = client.postForm("/identity/token", "grant_type=urn:ibm:params:oauth:grant-type:apikey&apikey=k");
            assertEquals(200, r.getStatus(), r.getBody());
        } finally {
            server.stop();
        }
    }

    /**
     * Ports, flavor, credentials, TTL and flags are parsed in both separate and inline forms.
     */
    @Test
    void parsesCommandLine() {
        final MockConfig c = MockConfig.parse(new String[] { "--port", "8080,11434", "--https-port=8443", "--openai-flavor", "gemini", "--openai-api-key", "k", "--token-ttl", "60", "-q" });
        assertEquals(2, c.getHttpPorts().size());
        assertEquals(Integer.valueOf(8443), c.getHttpsPorts().get(0));
        assertEquals(MockConfig.OpenAiFlavor.GEMINI, c.getOpenAiFlavor());
        assertEquals("k", c.getOpenAiApiKey());
        assertEquals(60, c.getTokenTtlSeconds());
        assertTrue(c.isQuiet());
    }

    /**
     * Credentials are masked in form bodies, headers, JSON and Slack webhook paths, while other data is untouched.
     */
    @Test
    void redactsSecrets() {
        assertEquals("grant_type=x&apikey=" + Redactor.MASK, Redactor.redactFormBody("grant_type=x&apikey=secret"));
        assertEquals("Bearer " + Redactor.MASK, Redactor.redactHeader("Authorization", "Bearer abc"));
        assertEquals("application/json", Redactor.redactHeader("Content-Type", "application/json"));
        assertEquals("/services/T/B/" + Redactor.MASK + "?a=1", Redactor.redactPath("/services/T/B/secret?a=1"));
        assertEquals("/v1/completions", Redactor.redactPath("/v1/completions"));
        final String redacted = Redactor.redactJsonText("{\"access_token\":\"t\",\"nested\":{\"apikey\":\"k\"},\"text\":\"keep\"}");
        assertFalse(redacted.contains("\"t\""));
        assertFalse(redacted.contains("\"k\""));
        assertTrue(redacted.contains("keep"));
    }

    /**
     * Unknown options, out-of-range ports and missing values are rejected.
     */
    @Test
    void rejectsBadOptions() {
        final IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class, () -> MockConfig.parse(new String[] { "--nope" }));
        assertTrue(unknown.getMessage().startsWith("Unknown option"));
        assertThrows(IllegalArgumentException.class, () -> MockConfig.parse(new String[] { "--port", "99999" }));
        assertThrows(IllegalArgumentException.class, () -> MockConfig.parse(new String[] { "--port" }));
    }

    @Test
    void textGenTruncates() {
        final TextGen.Result r = TextGen.limit("a b c d", Integer.valueOf(2));
        assertEquals("a b", r.getText());
        assertTrue(r.isTruncated());
        assertFalse(TextGen.limit("a b", Integer.valueOf(5)).isTruncated());
    }
}
