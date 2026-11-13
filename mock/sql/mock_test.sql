-- =============================================================================================
-- End-to-end exercise of the dbsdk_v1 toolkit against the dbsdk mock server.
--
-- 1. Start the mock somewhere the IBM i can reach (it can also run ON the IBM i, Java 8+):
--      java -jar dbsdk-mock-server-1.0.0-jar-with-dependencies.jar --port 8888 --https-port 443
-- 2. Replace every MOCKHOST below with the host name / IP address of the machine running it.
-- 3. Run the statements one at a time (e.g. from VS Code for IBM i / ACS Run SQL Scripts).
--    The expected result is noted above each statement.
-- 4. Inspect what the toolkit actually sent:  http://MOCKHOST:8888/mock/requests
--
-- Prompt directives let you exercise error paths from SQL:
--   [[mock:status=500]]  [[mock:delay=2000]]  [[mock:reply=text]]  [[mock:malformed]]
-- =============================================================================================


-- ---------------------------------------------------------------------------------------------
-- Ollama
-- ---------------------------------------------------------------------------------------------
call dbsdk_v1.ollama_setprotocolforjob('http');
call dbsdk_v1.ollama_setserverforjob('MOCKHOST');
call dbsdk_v1.ollama_setportforjob(8888);
call dbsdk_v1.ollama_setmodelforjob('granite3.2:8b');

-- Expected: Mock ollama response from granite3.2:8b: Why is the sky blue?
values dbsdk_v1.ollama_generate('Why is the sky blue?');

-- Expected: Mock ollama response from llama3:latest: Hello
values dbsdk_v1.ollama_generate('Hello', model_id => 'llama3:latest');

-- Expected: SQLSTATE 38002 (the mock returns HTTP 500)
values dbsdk_v1.ollama_generate('Fail please [[mock:status=500]]');


-- ---------------------------------------------------------------------------------------------
-- OpenAI-compatible (/completions and /chat/completions)
-- ---------------------------------------------------------------------------------------------
call dbsdk_v1.openai_compatible_setprotocolforjob('http');
call dbsdk_v1.openai_compatible_setserverforjob('MOCKHOST');
call dbsdk_v1.openai_compatible_setportforjob(8888);
call dbsdk_v1.openai_compatible_setbasepathforjob('/v1');
call dbsdk_v1.openai_compatible_setmodelforjob('llama3');
-- Only needed if the mock was started with --openai-api-key sk-test
call dbsdk_v1.openai_compatible_setapikeyforjob('sk-test');

-- Expected: Mock openai response from llama3: why are armadillos so cute?
values dbsdk_v1.openai_compatible_generate('why are armadillos so cute?');

-- Expected: Mock openai response   (truncated to max_tokens = 3)
values dbsdk_v1.openai_compatible_generate('why are armadillos so cute?', '{"max_tokens": 3, "temperature": 0.7}');

-- Expected: the full JSON body ({"id":"cmpl-...","object":"text_completion","choices":[...]})
values dbsdk_v1.openai_compatible_generate_json('List the top 5 programming languages as a JSON array.',
                                                '{"temperature": 0.5, "max_tokens": 100}');

-- Expected: the full JSON body (no choices[0].text, so the function falls back to the raw response)
values dbsdk_v1.openai_compatible_generate('fallback path [[mock:malformed]]');

-- Expected: Mock chat response from llama3: Hello Gemini
values dbsdk_v1.openai_compatible_chat_generate('Hello Gemini');

-- Expected: Mock chat response from gemini-2.0-flash: Hi   (base_url overrides the job settings)
values dbsdk_v1.openai_compatible_chat_generate('Hi', '{"model_id": "gemini-2.0-flash", "temperature": 0.2}',
                                                NULL, 'http://MOCKHOST:8888/v1beta/openai');

-- Expected: SQLSTATE 38002 ("HTTP response code was 429")
values dbsdk_v1.openai_compatible_chat_generate('rate limit me [[mock:status=429]]');

-- Tip: start the mock with --openai-flavor gemini to verify openai_compatible_generate fails (404)
-- while openai_compatible_chat_generate succeeds, like it does against the real Gemini endpoint.


-- ---------------------------------------------------------------------------------------------
-- Kafka (REST Proxy)
-- ---------------------------------------------------------------------------------------------
call dbsdk_v1.kafka_setprotocolforjob('http');
call dbsdk_v1.kafka_setbrokerforjob('MOCKHOST');
call dbsdk_v1.kafka_setportforjob(8888);
call dbsdk_v1.kafka_settopicforjob('orders');

-- Expected: NULL (success). Then GET http://MOCKHOST:8888/mock/kafka shows the record.
values dbsdk_v1.kafka_publish('{"order": 1001, "status": "shipped"}', key => 'order-1001');

-- Expected: NULL, published to topic "audit"
values dbsdk_v1.kafka_publish('user logged in', 'audit', 'u1');

-- Expected: SQLSTATE 38002
values dbsdk_v1.kafka_publish('broker down [[mock:status=503]]');


-- ---------------------------------------------------------------------------------------------
-- Slack (incoming webhook)
-- ---------------------------------------------------------------------------------------------
call dbsdk_v1.slack_setwebhookforjob('http://MOCKHOST:8888/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX');

-- Expected: ok
values dbsdk_v1.slack_sendmessage('Hello from IBM i!');

-- Expected: SQLSTATE 38002 with message text "invalid_token"
values dbsdk_v1.slack_sendmessage('nope [[mock:status=403]]');


-- ---------------------------------------------------------------------------------------------
-- watsonx.ai and Twilio
--
-- These call https://iam.cloud.ibm.com, https://<region>.ml.cloud.ibm.com and
-- https://api.twilio.com by default. Point them at the mock with their base URL settings.
-- (Toolkit builds without these settings need host redirection instead; see the mock README.)
-- ---------------------------------------------------------------------------------------------
call dbsdk_v1.wx_SetIamBaseUrlForJob('http://MOCKHOST:8888');
call dbsdk_v1.wx_SetMlBaseUrlForJob('http://MOCKHOST:8888');
call dbsdk_v1.twilio_setbaseurlforjob('http://MOCKHOST:8888');

-- Expected: http://MOCKHOST:8888 for both
values (dbsdk_v1.wx_GetIamBaseUrl(), dbsdk_v1.wx_GetMlBaseUrl());

set dbsdk_v1.wx_region = 'us-south';
set dbsdk_v1.wx_apiVersion = '2023-07-07';
call dbsdk_v1.wx_logoutJob();
call dbsdk_v1.wx_SetApiKeyForJob('any-key');
call dbsdk_v1.wx_SetProjectIdForJob('mock-project');

-- Expected: Y
values dbsdk_v1.wx_ShouldGetNewToken();
-- Expected: Y
values dbsdk_v1.wx_authenticate();
-- Expected: N
values dbsdk_v1.wx_ShouldGetNewToken();

-- Expected: Mock watsonx response from meta-llama/llama-2-13b-chat: Hello world
values dbsdk_v1.wx_generate('Hello world');

-- Expected: Mock watsonx response from ibm/granite-3-8b-instruct: Hi
values dbsdk_v1.wx_generate('Hi', model_id => 'ibm/granite-3-8b-instruct',
                            parameters => dbsdk_v1.parameters(max_new_tokens => 50));

-- Expected: result set of 5 models
call dbsdk_v1.wx_getmodels();

-- Expected: N (keys starting with BAD/-BAD/invalid are rejected unless --watsonx-apikey is set)
call dbsdk_v1.wx_logoutJob();
call dbsdk_v1.wx_SetApiKeyForJob('-BAD');
values dbsdk_v1.wx_authenticate();

call dbsdk_v1.twilio_setsidforjob('ACmockAccountSid');
call dbsdk_v1.twilio_setauthtokenforjob('mock-token');
call dbsdk_v1.twilio_setnumberforjob('5557654321');

-- Expected: queued
values dbsdk_v1.twilio_sendsms('5551234567', 'Hello from IBM i');


-- ---------------------------------------------------------------------------------------------
-- Clean up job-level settings
-- ---------------------------------------------------------------------------------------------
call dbsdk_v1.wx_logoutJob();
call dbsdk_v1.wx_SetIamBaseUrlForJob(NULL);
call dbsdk_v1.wx_SetMlBaseUrlForJob(NULL);
call dbsdk_v1.twilio_setbaseurlforjob(NULL);
set dbsdk_v1.ollama_server = NULL;
set dbsdk_v1.ollama_port = NULL;
set dbsdk_v1.openai_compatible_server = NULL;
set dbsdk_v1.openai_compatible_port = NULL;
set dbsdk_v1.openai_compatible_protocol = NULL;
set dbsdk_v1.openai_compatible_basepath = NULL;
set dbsdk_v1.openai_compatible_model = NULL;
set dbsdk_v1.openai_compatible_apikey = NULL;
set dbsdk_v1.kafka_broker = NULL;
set dbsdk_v1.kafka_port = NULL;
set dbsdk_v1.kafka_topic = NULL;
set dbsdk_v1.slack_webhook = NULL;
set dbsdk_v1.twilio_sid = NULL;
set dbsdk_v1.twilio_authtoken = NULL;
set dbsdk_v1.twilio_number = NULL;
