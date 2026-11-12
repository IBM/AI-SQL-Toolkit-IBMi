# dbsdk test harness

An end-to-end test suite for the `dbsdk_v1` SQL toolkit. It connects to Db2 for i through the
[Mapepire JDBC driver](https://github.com/Mapepire-IBMi/mapepire-jdbc), calls the toolkit's SQL
functions and procedures, and checks both what they return and what they actually sent over HTTP.

The external services (Ollama, OpenAI-compatible endpoints, Kafka, Slack, Twilio, watsonx) are
provided by the [dbsdk mock server](../mock), so the results are deterministic and no cloud
accounts or API keys are needed.

- Java 8 or newer. Built with Maven into a single runnable jar.
- Runs on any machine that can reach the Mapepire server, including the IBM i itself.
- 131 tests, covering every HTTP-calling toolkit function and its configuration.

## How it fits together

```
 this machine                     IBM i                              mock host
 ------------                     -----                              ---------
 test harness --JDBC/Mapepire--> mapepire-server --SQL--> dbsdk_v1 --HTTP--> dbsdk mock server
      |                                                                          ^
      +---------------- inspects /mock/requests (what dbsdk_v1 sent) -----------+
```

The toolkit's HTTP calls come **from the IBM i**, so the IBM i must be able to reach the mock.
The harness itself only talks to the mock to check what was sent. Simplest setup: run the mock
and the harness on the IBM i, and everything is `localhost`.

## Prerequisites

1. **The toolkit is installed** in schema `DBSDK_V1` (see the root `makefile`). The admin
   procedures in `src/admin/sql` are optional; their tests are skipped if they're not installed.
2. **mapepire-server is running.** The default is `localhost:8076`.
3. **The mock server is running** somewhere the IBM i can reach, by default `localhost:8080`:
   ```sh
   java -jar dbsdk-mock-server-1.0.0-jar-with-dependencies.jar --port 8080
   ```
4. **The user profile** you connect as needs `*EXECUTE` on the toolkit's routines and
   `SELECT`/`INSERT`/`UPDATE`/`DELETE` on `DBSDK_V1.CONF`. See `test/README_TESTING.md` and
   `test/grant_test_user.sql`.
5. **XMLSERVICE** for the PASE tests (`--skip pase` otherwise).

## Build

```sh
cd test-harness
./mvnw package          # Windows: mvnw.cmd package
```

This produces `target/dbsdk-test-harness-1.0.0-jar-with-dependencies.jar`. The build also runs
the harness's own unit tests, which don't need an IBM i.

## Run

```sh
java -jar target/dbsdk-test-harness-1.0.0-jar-with-dependencies.jar
```

With no options, the harness connects to Mapepire on `localhost:8076` and prompts for your IBM i
user profile and password. The password is read without echoing it to the screen. You can also
supply credentials up front:

```sh
# Environment variables (preferred for scripts and CI)
MAPEPIRE_USER=DBSDKTEST MAPEPIRE_PASSWORD=... java -jar dbsdk-test-harness-1.0.0-jar-with-dependencies.jar

# Command line (visible to other users of the machine; avoid on shared systems)
java -jar dbsdk-test-harness-1.0.0-jar-with-dependencies.jar --user DBSDKTEST --password ...
```

Without an interactive console (in CI, for example), missing credentials are an error rather
than a prompt.

Other common runs:

```sh
# Mapepire with its default self-signed certificate
java -jar dbsdk-test-harness-...jar --insecure

# Harness on a workstation, mapepire and mock on the IBM i
java -jar dbsdk-test-harness-...jar --host myibmi --mock-url http://myibmi:8080

# Only the OpenAI-compatible tests, with JUnit XML reports
java -jar dbsdk-test-harness-...jar --only openai --report-dir reports

# Everything except the tests for open bugs
java -jar dbsdk-test-harness-...jar --skip known-issue

# See what would run, without connecting
java -jar dbsdk-test-harness-...jar --list
```

Before any test runs, the harness checks four things and reports problems once, clearly:
- the connection works;
- `DBSDK_V1` contains routines;
- the IBM i can reach the mock (it calls `QSYS2.HTTP_GET` on the mock's health URL);
- this machine can reach the mock.

If this machine can't reach the mock, the tests still run, but the checks of what was sent are
skipped.

### Options

| Option | Default | Purpose |
| --- | --- | --- |
| `-h, --host <host>` | `localhost` | Mapepire server host |
| `-P, --port <port>` | `8076` | Mapepire server port |
| `-u, --user <user>` | prompt / `MAPEPIRE_USER` | IBM i user profile |
| `--password <pw>` | prompt / `MAPEPIRE_PASSWORD` | Password |
| `--insecure` | off | Don't verify the Mapepire server's TLS certificate |
| `--query-timeout <s>` | `120` | Per-statement timeout |
| `--mock-host <host>` | `localhost` | Mock server host **as the IBM i sees it** |
| `--mock-port <port>` | `8080` | Mock server port |
| `--mock-url <url>` | `http://<mock-host>:<mock-port>` | Mock server URL as this machine sees it |
| `--skip <tags>` | none | Skip tests with these tags |
| `--only <tags>` | all | Run only tests with these tags |
| `--list` | | List selected tests and exit |
| `--report-dir <dir>` | none | Also write JUnit XML reports |
| `-v, --verbose` | off | Show stack traces |

Exit status: `0` all tests passed, `1` at least one test failed, `2` usage or environment error.

## What is tested

| Tag | Covers |
| --- | --- |
| `openai` | `openai_compatible_generate`, `_generate_json` and `_chat_generate`, plus configuration. Every extended option is checked on the wire and, where the mock reacts to it, in the result: `temperature` (including 0), `max_tokens` (the reply is truncated and `finish_reason` is `length`), `top_p`, `n` (the number of choices), `presence_penalty`, `frequency_penalty`, `stop`, `seed`, `user`, `suffix`, `logprobs` and `model_id`. Also checked: the defaults that are sent, that unsupported options aren't forwarded, the `api_key` and `base_url` arguments, out-of-range values, HTTP errors, and the full-body fallback. For chat, only the options you pass are forwarded, which matters for Gemini. |
| `ollama` | `ollama_generate` (default and explicit model, request shape, CCSID 1208 text, errors) and configuration |
| `kafka` | `kafka_publish` (stored records, explicit topic, no key, request envelope, errors) and configuration |
| `slack` | `slack_sendmessage` (job webhook and explicit webhook) and configuration |
| `twilio` | `twilio_sendsms` (status, request fields, errors) and configuration, including the base URL setting (`twilio_getbaseurl` and its setters) |
| `watsonx` | `wx_authenticate`, `wx_generate` (default and explicit model, `parameters`, errors) and `wx_getmodels`; `parameters()`, `GetUrl`, the IAM and watsonx.ai base URL settings (`wx_GetIamBaseUrl`, `wx_GetMlBaseUrl` and their setters), API key and project settings, the token lifecycle (`wx_ShouldGetNewToken`, `wx_SetBearerTokenForJob`, `wx_logoutJob`), and `wx_generate` without a token |
| `conf` | `conf_register_user`, `conf_initialize`, `conf_remove_user`, every `*forme` setter (21), precedence of job settings over `CONF`, multi-user row isolation (#21/#27), and the `CONF_*` admin procedures |
| `json` | `json_object_update`, using the cases documented in `test/test_json_object_update.sql` |
| `pase` | `pase_call` |

The watsonx and Twilio tests point the toolkit at the mock with its base URL settings
(`wx_SetIamBaseUrlForJob`, `wx_SetMlBaseUrlForJob`, `twilio_setbaseurlforjob`), so they need no
host redirection. They need a toolkit build that includes those settings.

### Tests for open bugs (`known-issue`)

Some tests encode documented behavior that the toolkit doesn't meet yet. They're expected to fail
until the bug is fixed, and you can leave them out with `--skip known-issue`:

| Test | Issue |
| --- | --- |
| Slack: error text is raised with SQLSTATE 38002 | #48 |
| watsonx: `GetUrl` includes a version by default; `wx_generate` works without setting `wx_apiVersion` | #49 |
| Twilio: `+` in E.164 numbers is preserved | #50 |
| Kafka: REST Proxy `Content-Type` and `Accept` headers are both sent | #51 |
| Kafka: the per-record error is returned to the caller, as documented | none yet |
| `json_object_update`: update values overwrite base values; two NULLs give `{}` | none yet |

## Side effects

- **Your `CONF` row:** the `conf` tests change the `DBSDK_V1.CONF` row of the user you connect
  as. The row is saved before those tests and written back afterwards, or deleted if it didn't
  exist before. The tests also create and delete a row for the fictional profile `HARNESSX1`.
  Use `--skip conf` to leave `CONF` alone entirely.
- **Job settings:** every other change is made with `*forjob` settings, including the watsonx and
  Twilio base URLs. These live only in the test's own connection.
- **Mock server state:** the harness resets the mock (`POST /mock/reset`) at the start of a run.

## Project layout

```
test-harness/
├── pom.xml                              Maven build (Java 8 target, single jar)
├── src/assembly/jar-with-dependencies.xml   jar-with-dependencies + META-INF/services merging
├── src/main/java/com/ibm/dbsdk/harness/
│   ├── HarnessMain.java                 Entry point, credential prompts, preflight, JUnit launcher
│   ├── HarnessConfig.java               Command-line options and tag selection
│   ├── Db.java                          Mapepire JDBC connection helpers
│   ├── MockClient.java                  Mock server inspection API client
│   ├── ConsoleReporter.java             PASS/FAIL/SKIP console output
│   └── tests/                           The toolkit tests (JUnit 5), one class per area
└── src/test/java/...                    Unit tests for the harness itself
```

The toolkit tests are in `src/main`, not `src/test`. That way they ship inside the jar and run
through the JUnit Platform Launcher against whichever IBM i you point the harness at.
