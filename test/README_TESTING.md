# Testing

The toolkit's tests are in [`test-harness/`](../test-harness/README.md). It's a JDBC test suite that
calls the `dbsdk_v1` SQL functions through Mapepire, and uses the [mock server](../mock/README.md)
in place of every external service (watsonx, Ollama, OpenAI-compatible endpoints, Kafka, Slack and
Twilio). No live endpoints, API keys or cloud accounts are needed.

On IBM i, run everything from the repository root with:

```sh
make test
```

See [test-harness/README.md](../test-harness/README.md) for prerequisites, credentials and options.

## Testing as a separate user profile

To run the tests as a dedicated profile (e.g. `DBSDKTEST`) rather than the schema owner, grant it
authority first with [`test-harness/sql/grant_test_user.sql`](../test-harness/sql/grant_test_user.sql).
Run it as the schema owner or a `*SECADM` profile, and again after each rebuild of the toolkit:

```
RUNSQLSTM SRCSTMF('test-harness/sql/grant_test_user.sql') COMMIT(*NONE) NAMING(*SQL)
```

## Where the former SQL test scripts went

This directory used to hold SQL scripts that were run by hand in ACS. Every check they made is now
part of the test suite:

| Former script | Now covered by |
| --- | --- |
| `a.sql` (watsonx) | `WatsonxTest`, `WatsonxServiceTest` |
| `ollama_test.sql` | `OllamaTest`, `ConfTest` |
| `openai_compatible_test.sql` | `OpenAiConfigTest`, `OpenAiCompletionsTest` |
| `openai_compatible_chat_generate_test.sql` | `OpenAiChatTest`, `ConfTest` |
| `test_fix.sql` (CONF row isolation, #21/#27) | `ConfTest` |
| `test_json_object_update.sql` | `JsonObjectUpdateTest` |
| `grant_test_user.sql` | moved to `test-harness/sql/grant_test_user.sql` |
