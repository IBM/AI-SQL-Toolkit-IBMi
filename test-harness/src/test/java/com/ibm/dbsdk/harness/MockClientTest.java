package com.ibm.dbsdk.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Unit tests for the mock-inspection helpers. */
class MockClientTest {

    private static final ObjectMapper s_mapper = new ObjectMapper();

    /**
     * Form bodies decode the way a receiving service would, including '+' as a space.
     *
     * @throws IOException
     *             never; the JSON is a literal
     */
    @Test
    void formFieldsDecode() throws IOException {
        final JsonNode request = s_mapper.readTree("{\"body\": \"To=+15551234567&From=555&Body=Hello%20there%21\"}");
        final Map<String, String> form = MockClient.formFields(request);
        assertEquals(" 15551234567", form.get("To"));
        assertEquals("555", form.get("From"));
        assertEquals("Hello there!", form.get("Body"));
    }

    @Test
    void headerLookupIgnoresCase() throws IOException {
        final JsonNode request = s_mapper.readTree("{\"headers\": {\"Content-type\": \"application/json\", \"Authorization\": \"Bearer ****\"}}");
        assertEquals("application/json", MockClient.header(request, "Content-Type"));
        assertEquals("Bearer ****", MockClient.header(request, "authorization"));
        assertNull(MockClient.header(request, "Accept"));
    }

    @Test
    void unreachableMockIsReported() {
        assertNotNull(new MockClient("http://127.0.0.1:1").checkHealth());
    }
}
