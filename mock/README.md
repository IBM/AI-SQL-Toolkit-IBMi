# dbsdk mock server

A self-contained mock of every external service the `dbsdk_v1` SQL toolkit talks to, so you can
test the toolkit without cloud accounts, API keys, or any LLM, Kafka, Slack or Twilio
infrastructure.

- Pure Java. It uses only the JDK's built-in HTTP server and Jackson for JSON.
- Runs on **Java 8 or newer**, including directly on IBM i.
- Builds into a single runnable jar with `jar-with-dependencies`.
- One port serves every service. Requests are routed by URL path.
- Every request is recorded and can be inspected over HTTP, which makes it useful for asserting
  exactly what the toolkit sent.

## Build

```sh
cd mock
./mvnw package          # Windows: mvnw.cmd package
```

This produces `target/dbsdk-mock-server-1.0.0-jar-with-dependencies.jar`. If Maven is already
installed, `mvn package` works too. You can build with any JDK from 8 upward. On JDK 9+ the build
compiles with `--release 8`, so the jar always runs on Java 8.

## Run

```sh
java -jar target/dbsdk-mock-server-1.0.0-jar-with-dependencies.jar
```

By default the server listens on HTTP port 8080. Useful variations:

```sh
# Listen on the toolkit's default ports (Ollama 11434, OpenAI-compatible 8000, Kafka 8082) as well
java -jar dbsdk-mock-server-1.0.0-jar-with-dependencies.jar --port 8080,11434,8000,8082

# Also serve HTTPS, which watsonx and Twilio need (see "watsonx and Twilio" below)
java -jar dbsdk-mock-server-1.0.0-jar-with-dependencies.jar --port 8080 --https-port 443

# Behave like Gemini's OpenAI-compatible endpoint and require an API key
java -jar dbsdk-mock-server-1.0.0-jar-with-dependencies.jar --openai-flavor gemini --openai-api-key sk-test
```

`--help` prints every option:

| Option | Default | Purpose |
| --- | --- | --- |
| `-p, --port <p[,p...]>` | `8080` | HTTP port(s) |
| `--https-port <p[,p...]>` | none | HTTPS port(s) |
| `--keystore <file>` | generated | PKCS12/JKS keystore for HTTPS. If you omit it, `mock-server.p12` and `mock-server.pem` are generated in the working directory with `keytool`. |
| `--keystore-password <pw>` | `changeit` | Keystore password |
| `-b, --bind <addr>` | `0.0.0.0` | Bind address |
| `--watsonx-apikey <key>` | any | The only IBM Cloud API key IAM accepts |
| `--openai-api-key <key>` | none | Bearer key the OpenAI-compatible endpoints require |
| `--openai-flavor openai\|gemini` | `openai` | `gemini` returns 404 for the legacy `/completions` endpoint and rejects unknown chat request fields |
| `--twilio-auth-token <t>` | any | Twilio auth token |
| `--token-ttl <seconds>` | `3600` | Lifetime of issued watsonx tokens. Set it low to test token expiry. |
| `--max-recorded <n>` | `1000` | Number of requests kept for `/mock/requests` |
| `-q, --quiet` | | Don't log each request |

When you don't configure a credential, the mock accepts any non-empty value. One exception:
watsonx API keys that start with `BAD`, `-BAD` or `invalid` are always rejected, which matches the
existing `test/a.sql`.

## What is mocked

| Toolkit function(s) | Mock endpoint | Notes |
| --- | --- | --- |
| `wx_authenticate` | `POST /identity/token` | IBM Cloud IAM API-key grant. Returns an `access_token` and `expires_in`. |
| `wx_generate` | `POST /ml/v1/text/generation?version=…` | Requires a token issued by the mock, `version`, `model_id`, `input` and `project_id`. Honours `parameters.max_new_tokens`. |
| `wx_getmodels` | `GET /ml/v1/foundation_model_specs` | No authentication |
| `ollama_generate` | `POST /api/generate` | Streaming (NDJSON) and non-streaming. Also `GET /api/tags`. |
| `openai_compatible_generate`, `openai_compatible_generate_json` | `POST {basepath}/completions` | Works under any base path. Validates `temperature`, `top_p`, `n`, penalties and `max_tokens`, and honours `n`, `max_tokens`, `stop`, `echo` and `stream`. |
| `openai_compatible_chat_generate` | `POST {basepath}/chat/completions` | Validates `messages`. In `gemini` flavor, unknown fields are rejected. |
| `kafka_publish` | `POST /topics/{topic}` | Kafka REST Proxy v2. Records are stored and visible at `GET /mock/kafka`. |
| `slack_sendmessage` | `POST /services/{T}/{B}/{X}` | Plain-text `ok`, `no_text` or `invalid_payload`, like Slack |
| `twilio_sendsms` | `POST /2010-04-01/Accounts/{sid}/Messages.json` | Basic auth must use the account SID. Validates `To`, `From` and `Body`. |

Generated text is deterministic so you can assert on it:
`Mock <provider> response from <model>: <prompt>`, for example
`Mock ollama response from granite3.2:8b: Why is the sky blue?`.

The mock does **not** cover:
- `pase_call`, which runs locally through XMLSERVICE.
- The `conf_*` and `*_set…forjob` / `*_set…forme` procedures. These only touch Db2, but they are
  exercised indirectly because the generate/publish functions read their settings.

## Prompt directives: driving error paths from SQL

SQL can't add custom HTTP headers, so you steer the mock with markers inside the prompt or message
text. The marker is removed before the prompt is echoed back.

| Directive | Effect |
| --- | --- |
| `[[mock:status=503]]` | Respond with that HTTP status and an error body in that service's own format |
| `[[mock:delay=2000]]` | Wait that many milliseconds first (useful for timeout testing) |
| `[[mock:reply=any text]]` | Return exactly this generated text. The text ends at the first `]]`. |
| `[[mock:malformed]]` | Return 200 with a body that lacks the expected fields. This exercises the "return full response" fallback in the SQL functions. |
| `[[mock:record-error]]` | Kafka only: return 200 with an error in `offsets[0]` |

Directives are read from the prompt (LLMs), the record value or key (Kafka), the `text` field
(Slack), the `Body` (Twilio) and the API key (watsonx IAM).

```sql
values dbsdk_v1.ollama_generate('hi [[mock:status=500]]');        -- SQLSTATE 38002
values dbsdk_v1.openai_compatible_generate('[[mock:reply={"ok":true}]]');
```

## Inspecting traffic

| Endpoint | Purpose |
| --- | --- |
| `GET /mock/health` (or `/`) | Status, version and endpoint catalogue |
| `GET /mock/requests?service=slack&limit=5` | Recorded requests, oldest first. Each entry has the method, path, headers, raw and parsed body, and the response the mock sent. |
| `DELETE /mock/requests` | Clear the request log |
| `GET /mock/kafka` | Records published per topic |
| `POST /mock/reset` | Clear requests, issued tokens and Kafka topics |

Service names used for filtering are `watsonx`, `ollama`, `openai`, `kafka`, `slack` and
`twilio`.

## watsonx and Twilio

`wx_*` and `twilio_sendsms` hard-code HTTPS hosts (`iam.cloud.ibm.com`,
`<region>.ml.cloud.ibm.com`, `api.twilio.com`). To redirect them to the mock:

1. Start the mock with `--https-port 443`. On first start it generates `mock-server.p12` and exports
   the certificate as `mock-server.pem`. The certificate's SANs cover those host names, every
   watsonx region, `localhost` and the local host name.
2. Use Digital Certificate Manager to import `mock-server.pem` into the IBM i `*SYSTEM` certificate
   store as a trusted CA.
3. Map the host names to the mock's IP address and make sure the local host table is searched
   first:
   ```
   QSYS/ADDTCPHTE INTNETADR('<mock ip>') HOSTNAME(('iam.cloud.ibm.com') ('us-south.ml.cloud.ibm.com') ('api.twilio.com'))
   QSYS/CHGTCPDMN HOSTSCHPTY(*LOCAL)
   ```
   Remove the entry with `QSYS/RMVTCPHTE` when you are done.

`wx_generate` needs a `version` query parameter, just like the real API. `dbsdk_v1.GetUrl` only
adds it when the `dbsdk_v1.wx_apiVersion` global variable is set, so run
`set dbsdk_v1.wx_apiVersion = '2023-07-07';` first. The variable defaults to NULL.

## Exercising the whole toolkit

[sql/mock_test.sql](sql/mock_test.sql) calls every HTTP-based toolkit function against the mock.
The expected result is noted above each statement. Replace `MOCKHOST` with the address of the
machine running the mock.

## Project layout

```
mock/
├── pom.xml                         Maven build (Java 8 target, jar-with-dependencies)
├── mvnw, mvnw.cmd, .mvn/           Maven wrapper (no local Maven install needed)
├── sql/mock_test.sql               End-to-end SQL script for IBM i
└── src/
    ├── main/java/com/ibm/dbsdk/mock/
    │   ├── MockServer.java         Entry point / lifecycle
    │   ├── MockConfig.java         Command-line options
    │   ├── Router.java             Path-based routing and request recording
    │   ├── Directives.java         [[mock:...]] prompt directives
    │   ├── Tls.java                HTTPS + self-signed keystore generation
    │   └── handlers/               One handler per emulated service
    └── test/java/...               JUnit 5 end-to-end tests
```
