-- ## OpenAI-compatible endpoints main functionality

-- #### **Function:** `openai_compatible_generate`

-- **Description:** Uses OpenAI-compatible API to generate a reply to the given prompt
-- 
-- **Input parameters:**
-- - `PROMPT` (required): The input prompt for the LLM.
-- - `options` (optional): JSON object containing optional parameters:
--   - `model_id`: The model identifier to use for generation.
--   - `max_tokens`: Maximum number of tokens to generate. Default 16.
--   - `temperature`: Sampling temperature between 0 and 2. Default 1.
--   - `top_p`: Nucleus sampling probability mass. Default 1.
--   - `n`: Number of completions to generate. Default 1.
--   - `stream`: Whether to stream back partial progress. Default false.
--   - `logprobs`: Include log probabilities on most likely tokens, max 5.
--   - `echo`: Echo back the prompt in the completion. Default false.
--   - `stop`: Up to 4 sequences where generation should stop.
--   - `presence_penalty`: Penalty between -2.0 and 2.0 for tokens based on presence. Default 0.
--   - `frequency_penalty`: Penalty between -2.0 and 2.0 for tokens based on frequency. Default 0.
--   - `best_of`: Generate best_of completions server-side. Default 1.
--   - `logit_bias`: JSON object mapping tokens to bias values.
--   - `user`: Unique identifier for the end user.
--   - `seed`: Seed for deterministic sampling.
--   - `suffix`: Suffix after completion insertion (for compatible models only).
-- - `apikey` (optional): API key for authentication. If not provided, uses configured key.
-- - `base_url` (optional): Base URL for the API endpoint. If not provided, uses configured endpoint settings.
-- 
-- **Return type:** 
-- - `clob(2G) ccsid 1208`
-- 
-- **Return value:**
-- - The generated reply.
--
-- Unsupported options:
--  - set best_of = coalesce(json_value(options, '$.best_of'), 1);
 
/*
  =====================================================================
  STALE ROUTINE CLEANUP
  =====================================================================
  Older toolkit versions registered openai_compatible_generate and
  openai_compatible_generate_json with only 2 parameters. Because
  the current version defines them with 4 parameters, "CREATE OR REPLACE"
  registers an overload instead of replacing the existing function.
  
  To prevent upgraded systems from silently executing the stale
  2-parameter overloads, we query QSYS2.SYSROUTINES and drop any
  signatures that do not take exactly 4 parameters.
  =====================================================================
*/
begin
  for stale_func as
    select specific_name
      from qsys2.sysroutines
      where routine_schema = 'AISQLTKT'
        and routine_name in ('OPENAI_COMPATIBLE_GENERATE', 'OPENAI_COMPATIBLE_GENERATE_JSON')
        and in_parms <> 4
  do
    execute immediate 'drop specific function aisqltkt.' concat stale_func.specific_name;
  end for;
end;

create or replace function aisqltkt.openai_compatible_generate(
  prompt varchar(32000) ccsid 1208,
  options varchar(32000) ccsid 1208 default '{}',
  api_key_  varchar(8000) ccsid 1208 default NULL,
  base_url varchar(1000) ccsid 1208 default NULL
)
  returns clob(2G) ccsid 1208
  modifies sql data
  not deterministic
  no external action
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  declare fullUrl varchar(32500) ccsid 1208 default NULL;
  declare apierr varchar(32500) ccsid 1208 default NULL;
  declare response_header clob(32K) ccsid 1208;
  declare response_message clob(2G) ccsid 1208;
  declare response_code int default 500;
  declare api_key varchar(8000) ccsid 1208;
  declare http_options varchar(32400) ccsid 1208;
  declare response_text clob(2G) ccsid 1208;
  declare req_body clob(64K) ccsid 1208;
  
  -- Extract parameters from options
  declare model_id varchar(1000) ccsid 1208;
  declare max_tokens integer default 256;
  declare temperature decimal(4,2);
  declare top_p decimal(4,2);
  declare n integer;
  declare stream varchar(5) ccsid 1208;
  declare logprobs integer;
  declare echo varchar(5) ccsid 1208;
  declare stop_token varchar(32000) ccsid 1208;
  declare presence_penalty decimal(4,2);
  declare frequency_penalty decimal(4,2);
  declare logit_bias varchar(32000) ccsid 1208;
  declare user_id varchar(1000) ccsid 1208;
  declare seed integer;
  declare suffix varchar(32000) ccsid 1208;
  
  -- Get parameters from options JSON
  set model_id = json_value(options, '$.model_id');
  set max_tokens = coalesce(json_value(options, '$.max_tokens'), 256);
  set temperature = coalesce(json_value(options, '$.temperature' returning decimal(4,2)), 1.00);
  set top_p = coalesce(json_value(options, '$.top_p' returning decimal(4,2)), 1.00);
  set n = coalesce(json_value(options, '$.n'), 1);
  set stream = coalesce(json_value(options, '$.stream'), 'false');
  set logprobs = json_value(options, '$.logprobs');
  set echo = coalesce(json_value(options, '$.echo'), 'false');
  set stop_token = json_value(options, '$.stop');
  set presence_penalty = coalesce(json_value(options, '$.presence_penalty' returning decimal(4,2)), 0.00);
  set frequency_penalty = coalesce(json_value(options, '$.frequency_penalty' returning decimal(4,2)), 0.00);
  set logit_bias = json_value(options, '$.logit_bias');
  set user_id = json_value(options, '$.user');
  set seed = json_value(options, '$.seed');
  set suffix = json_value(options, '$.suffix');

  -- unsupported options
  
  -- Get API key for authentication
  if (api_key_ is null) then
    set api_key = aisqltkt.openai_compatible_getapikey();
  else
    set api_key = api_key_;
  end if;
  -- Build the URL for the OpenAI compatible API endpoint
  if (base_url is not null and trim(base_url) <> '') then
    -- Use provided base URL
    set fullUrl = base_url concat '/completions';
  else
    -- Use configured endpoint settings
    set fullUrl = aisqltkt.openai_compatible_getprotocol() concat '://' 
                concat aisqltkt.openai_compatible_getserver() 
                concat ':' concat aisqltkt.openai_compatible_getport() 
                concat aisqltkt.openai_compatible_getbasepath() 
                concat '/completions';
  end if;
  
  -- Set HTTP options including headers
  if (api_key is not null and trim(api_key) <> '') then
    set http_options = json_object(
      'ioTimeout': 2000000,
      'headers': json_object(
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' concat api_key
      )
    );
  else 
    set http_options = json_object(
      'ioTimeout': 2000000,
      'headers': json_object(
        'Content-Type': 'application/json'
      )
    );
  end if;
  
  -- Build the request body with required parameters
  set req_body = json_object(
    'model': coalesce(model_id, aisqltkt.openai_compatible_getmodel()),
    'prompt': prompt,
    'max_tokens': max_tokens,
    'temperature': cast(temperature as decfloat),
    'top_p': cast(top_p as decfloat),
    'n': n,
    'presence_penalty': cast(presence_penalty as decfloat),
    'frequency_penalty': cast(frequency_penalty as decfloat)
  );

  -- Add optional parameters only if they are not null
  if (max_tokens > 0) then
    set req_body = JSON_UPDATE(req_body, 'SET', '$.max_tokens', max_tokens);
  end if;

  if (logprobs is not null) then
    set req_body = JSON_UPDATE(req_body, 'SET', '$.logprobs', logprobs);
  end if;

  if (stop_token is not null) then
    set req_body = JSON_UPDATE(req_body, 'SET', '$.stop', stop_token);
  end if;

  if (logit_bias is not null) then
    set req_body = JSON_UPDATE(req_body, 'SET', '$.logit_bias', logit_bias);
  end if;

  if (user_id is not null) then
    set req_body = JSON_UPDATE(req_body, 'SET', '$.user', user_id);
  end if;

  if (seed is not null) then
    set req_body = JSON_UPDATE(req_body, 'SET', '$.seed', seed);
  end if;

  if (suffix is not null) then
    set req_body = JSON_UPDATE(req_body, 'SET', '$.suffix', suffix);
  end if;
  
  -- Make the API call
  select response_message, response_http_header
  into response_message, response_header
  from table(qsys2.http_post_verbose(
                fullUrl,
                req_body,
                http_options));
  
  
  -- Get HTTP status code
  set response_code = json_value(response_header, '$.HTTP_STATUS_CODE');
  
  -- Process the response based on the schema in oai.json
  if (response_code >= 200 and response_code < 300) then
    -- Extract the content from the choices[0].text field as shown in oai.json
    set response_text = json_value(response_message, '$.choices[0].text' returning clob(2G) ccsid 1208);
    
    -- If parse fails, return the entire JSON response as a fallback
    if (response_text is null) then
      call systools.lprintf('OpenAI compatible parsing failed, returning full response');
      return response_message;
    end if;
    
    return response_text;
  end if;
  set response_text = 'An error has occured. Check the job log. HTTP response code was ' concat response_code concat ' from ' concat fullUrl;
  signal sqlstate '38002' set message_text = response_text;
  return null;
end;

-- #### **Function:** `openai_compatible_generate_json`

-- **Description:** Uses OpenAI-compatible API to process a prompt and return structured JSON
-- 
-- **Input parameters:**
-- - `PROMPT` (required): The input prompt for the LLM.
-- - `options` (optional): JSON object containing optional parameters:
--   - `model_id`: The model identifier to use for generation.
--   - `max_tokens`: Maximum number of tokens to generate. Default 16.
--   - `temperature`: Sampling temperature between 0 and 2. Default 1.
--   - `top_p`: Nucleus sampling probability mass. Default 1.
--   - `n`: Number of completions to generate. Default 1.
--   - `stream`: Whether to stream back partial progress. Default false.
--   - `logprobs`: Include log probabilities on most likely tokens, max 5.
--   - `echo`: Echo back the prompt in the completion. Default false.
--   - `stop`: Up to 4 sequences where generation should stop.
--   - `presence_penalty`: Penalty between -2.0 and 2.0 for tokens based on presence. Default 0.
--   - `frequency_penalty`: Penalty between -2.0 and 2.0 for tokens based on frequency. Default 0.
--   - `best_of`: Generate best_of completions server-side. Default 1.
--   - `logit_bias`: JSON object mapping tokens to bias values.
--   - `user`: Unique identifier for the end user.
--   - `seed`: Seed for deterministic sampling.
--   - `suffix`: Suffix after completion insertion (for compatible models only).
-- - `api_key_` (optional): API key for authentication. If not provided, uses configured key.
-- - `base_url` (optional): Base URL for the API endpoint. If not provided, uses configured endpoint settings.
-- 
-- **Return type:** 
-- - `clob(2G) ccsid 1208`
-- 
-- **Return value:**
-- - The complete JSON response from the API.
--
-- Unsupported options:
--  - set best_of = coalesce(json_value(options, '$.best_of'), 1);

create or replace function aisqltkt.openai_compatible_generate_json(
  prompt varchar(32000) ccsid 1208,
  options varchar(32000) ccsid 1208 default '{}',
  api_key_  varchar(8000) ccsid 1208 default NULL,
  base_url varchar(1000) ccsid 1208 default NULL
) 
  returns clob(2G) ccsid 1208
  modifies sql data
  not deterministic
  no external action
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  declare fullUrl varchar(32500) ccsid 1208 default NULL;
  declare apierr varchar(32500) ccsid 1208 default NULL;
  declare response_header clob(32K) ccsid 1208;
  declare response_message clob(2G) ccsid 1208;
  declare response_code int default 500;
  declare api_key varchar(8000) ccsid 1208;
  declare http_options varchar(32400) ccsid 1208;
  declare response_text clob(2G) ccsid 1208;
  declare req_body clob(64K) ccsid 1208;
  
  -- Extract parameters from options
  declare model_id varchar(1000) ccsid 1208;
  declare max_tokens integer default 256;
  declare temperature decimal(4,2);
  declare top_p decimal(4,2);
  declare n integer;
  declare stream varchar(5) ccsid 1208;
  declare logprobs integer;
  declare echo varchar(5) ccsid 1208;
  declare stop_token varchar(32000) ccsid 1208;
  declare presence_penalty decimal(4,2);
  declare frequency_penalty decimal(4,2);
  declare logit_bias varchar(32000) ccsid 1208;
  declare user_id varchar(1000) ccsid 1208;
  declare seed integer;
  declare suffix varchar(32000) ccsid 1208;
  
  -- Get parameters from options JSON
  set model_id = json_value(options, '$.model_id');
  set max_tokens = coalesce(json_value(options, '$.max_tokens'), 256);
  set temperature = coalesce(json_value(options, '$.temperature' returning decimal(4,2)), 1.00);
  set top_p = coalesce(json_value(options, '$.top_p' returning decimal(4,2)), 1.00);
  set n = coalesce(json_value(options, '$.n'), 1);
  set stream = coalesce(json_value(options, '$.stream'), 'false');
  set logprobs = json_value(options, '$.logprobs');
  set echo = coalesce(json_value(options, '$.echo'), 'false');
  set stop_token = json_value(options, '$.stop');
  set presence_penalty = coalesce(json_value(options, '$.presence_penalty' returning decimal(4,2)), 0.00);
  set frequency_penalty = coalesce(json_value(options, '$.frequency_penalty' returning decimal(4,2)), 0.00);
  set logit_bias = json_value(options, '$.logit_bias');
  set user_id = json_value(options, '$.user');
  set seed = json_value(options, '$.seed');
  set suffix = json_value(options, '$.suffix');

  -- unsupported options
  
  -- Get API key for authentication
  if (api_key_ is null) then
    set api_key = aisqltkt.openai_compatible_getapikey();
  else
    set api_key = api_key_;
  end if;
  
  -- Build the URL for the OpenAI compatible API endpoint
  if (base_url is not null and trim(base_url) <> '') then
    -- Use provided base URL
    set fullUrl = base_url concat '/completions';
  else
    -- Use configured endpoint settings
    set fullUrl = aisqltkt.openai_compatible_getprotocol() concat '://' 
                concat aisqltkt.openai_compatible_getserver() 
                concat ':' concat aisqltkt.openai_compatible_getport() 
                concat aisqltkt.openai_compatible_getbasepath() 
                concat '/completions';
  end if;
  
  -- Set HTTP options including headers
  if (api_key is not null and trim(api_key) <> '') then
    set http_options = json_object(
      'ioTimeout': 2000000,
      'headers': json_object(
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' concat api_key
      )
    );
  else 
    set http_options = json_object(
      'ioTimeout': 2000000,
      'headers': json_object(
        'Content-Type': 'application/json'
      )
    );
  end if;
  
  -- Build the request body with required parameters
  set req_body = json_object(
    'model': coalesce(model_id, aisqltkt.openai_compatible_getmodel()),
    'prompt': prompt,
    'temperature': cast(temperature as decfloat),
    'top_p': cast(top_p as decfloat),
    'n': n,
    'presence_penalty': cast(presence_penalty as decfloat),
    'frequency_penalty': cast(frequency_penalty as decfloat)
  );

  -- Add optional parameters only if they are not null
  if (max_tokens > 0) then
    set req_body = JSON_UPDATE(req_body, 'SET', '$.max_tokens', max_tokens);
  end if;

  if (logprobs is not null) then
    set req_body = JSON_UPDATE(req_body, 'SET', '$.logprobs', logprobs);
  end if;

  if (stop_token is not null) then
    set req_body = JSON_UPDATE(req_body, 'SET', '$.stop', stop_token);
  end if;

  if (logit_bias is not null) then
    set req_body = JSON_UPDATE(req_body, 'SET', '$.logit_bias', logit_bias);
  end if;

  if (user_id is not null) then
    set req_body = JSON_UPDATE(req_body, 'SET', '$.user', user_id);
  end if;

  if (seed is not null) then
    set req_body = JSON_UPDATE(req_body, 'SET', '$.seed', seed);
  end if;

  if (suffix is not null) then
    set req_body = JSON_UPDATE(req_body, 'SET', '$.suffix', suffix);
  end if;
  
  -- Make the API call
  select response_message, response_http_header
  into response_message, response_header
  from table(qsys2.http_post_verbose(
                fullUrl,
                req_body,
                http_options));
  
  
  -- Get HTTP status code
  set response_code = json_value(response_header, '$.HTTP_STATUS_CODE');
  
  -- Process the response
  if (response_code >= 200 and response_code < 300) then
    -- Return the complete JSON response
    return response_message;
  end if;

  return null;
end;


-- #### **Function:** `openai_compatible_chat_generate`

-- **Description:** Uses an OpenAI-compatible `/chat/completions` endpoint to generate a
-- reply to the given prompt. Unlike `openai_compatible_generate` (which uses the legacy
-- `/completions` endpoint with a top-level `"prompt"` field), this function wraps the
-- prompt in a `messages` array as required by Gemini and other modern chat-completion
-- endpoints.
-- 
-- **Input parameters:**
-- - `PROMPT` (required): The input prompt for the LLM (sent as a `user` role message).
-- - `options` (optional): JSON object containing optional parameters:
--   - `model_id`: The model identifier to use for generation.
--   - `max_tokens`: Maximum number of tokens to generate. Default 256.
--   - `temperature`: Sampling temperature between 0 and 2. Default 1.
--   - `top_p`: Nucleus sampling probability mass. Default 1.
--   - `n`: Number of completions to generate. Default 1.
--   - `presence_penalty`: Penalty between -2.0 and 2.0 for tokens based on presence. Default 0.
--   - `frequency_penalty`: Penalty between -2.0 and 2.0 for tokens based on frequency. Default 0.
--   - `stop`: Up to 4 sequences where generation should stop.
--   - `seed`: Seed for deterministic sampling.
--   - `user`: Unique identifier for the end user.
-- - `api_key_` (optional): API key for authentication. If not provided, uses configured key.
-- - `base_url` (optional): Base URL for the API endpoint (must end before `/chat/completions`).
--   If not provided, uses configured endpoint settings.
-- 
-- **Return type:**
-- - `clob(2G) ccsid 1208`
-- 
-- **Return value:**
-- - The text content of `choices[0].message.content` from the API response,
--   or the full JSON body if that path cannot be parsed.

create or replace function aisqltkt.openai_compatible_chat_generate(
  prompt varchar(32000) ccsid 1208,
  options varchar(32000) ccsid 1208 default '{}',
  api_key_  varchar(8000) ccsid 1208 default NULL,
  base_url varchar(1000) ccsid 1208 default NULL
)
  returns clob(2G) ccsid 1208
  modifies sql data
  not deterministic
  no external action
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  declare fullUrl varchar(32500) ccsid 1208 default NULL;
  declare response_header clob(32K) ccsid 1208;
  declare response_message clob(2G) ccsid 1208;
  declare response_code int default 500;
  declare api_key varchar(8000) ccsid 1208;
  declare http_options varchar(32400) ccsid 1208;
  declare response_text clob(2G) ccsid 1208;
  declare req_body clob(64K) ccsid 1208;

  -- Extract parameters from options
  declare model_id varchar(1000) ccsid 1208;
  declare max_tokens integer default 256;
  declare temperature decimal(4,2);
  declare top_p decimal(4,2);
  declare n integer;
  declare stop_token varchar(32000) ccsid 1208;
  declare presence_penalty decimal(4,2);
  declare frequency_penalty decimal(4,2);
  declare user_id varchar(1000) ccsid 1208;
  declare seed integer;

  -- Get parameters from options JSON
  set model_id          = json_value(options, '$.model_id');
  set max_tokens        = coalesce(json_value(options, '$.max_tokens'), 256);
  set temperature       = coalesce(json_value(options, '$.temperature' returning decimal(4,2)), 1.00);
  set top_p             = coalesce(json_value(options, '$.top_p' returning decimal(4,2)), 1.00);
  set n                 = coalesce(json_value(options, '$.n'), 1);
  set stop_token        = json_value(options, '$.stop');
  set presence_penalty  = coalesce(json_value(options, '$.presence_penalty' returning decimal(4,2)), 0.00);
  set frequency_penalty = coalesce(json_value(options, '$.frequency_penalty' returning decimal(4,2)), 0.00);
  set user_id           = json_value(options, '$.user');
  set seed              = json_value(options, '$.seed');

  -- Get API key for authentication
  if (api_key_ is null) then
    set api_key = aisqltkt.openai_compatible_getapikey();
  else
    set api_key = api_key_;
  end if;

  -- Build the URL for the chat completions endpoint
  if (base_url is not null and trim(base_url) <> '') then
    set fullUrl = base_url concat '/chat/completions';
  else
    set fullUrl = aisqltkt.openai_compatible_getprotocol() concat '://'
                concat aisqltkt.openai_compatible_getserver()
                concat ':' concat aisqltkt.openai_compatible_getport()
                concat aisqltkt.openai_compatible_getbasepath()
                concat '/chat/completions';
  end if;

  -- Set HTTP options including headers
  if (api_key is not null and trim(api_key) <> '') then
    set http_options = json_object(
      'ioTimeout': 2000000,
      'headers': json_object(
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' concat api_key
      )
    );
  else
    set http_options = json_object(
      'ioTimeout': 2000000,
      'headers': json_object(
        'Content-Type': 'application/json'
      )
    );
  end if;

  -- Build the chat-completions request body.
  -- The prompt is wrapped in a messages array as a user-role message,
  -- which is required by chat-completion endpoints (e.g. Gemini, GPT-4).
  -- Only the mandatory fields are included unconditionally; all other
  -- parameters are added only when explicitly supplied in options, because
  -- some endpoints (e.g. Gemini) return 400 for unrecognised/unsupported
  -- fields even when they carry OpenAI default values.
  set req_body = json_object(
    'model':    coalesce(model_id, aisqltkt.openai_compatible_getmodel()),
    'messages': json_array(
                  json_object('role': 'user', 'content': prompt)
                )
  );

  -- Add optional parameters only when explicitly present in options
  if (json_value(options, '$.max_tokens') is not null) then
    set req_body = JSON_UPDATE(req_body, 'SET', '$.max_tokens', max_tokens);
  end if;

  if (json_value(options, '$.temperature') is not null) then
    set req_body = JSON_UPDATE(req_body, 'SET', '$.temperature', cast(temperature as decfloat));
  end if;

  if (json_value(options, '$.top_p') is not null) then
    set req_body = JSON_UPDATE(req_body, 'SET', '$.top_p', cast(top_p as decfloat));
  end if;

  if (json_value(options, '$.n') is not null) then
    set req_body = JSON_UPDATE(req_body, 'SET', '$.n', n);
  end if;

  if (json_value(options, '$.presence_penalty') is not null) then
    set req_body = JSON_UPDATE(req_body, 'SET', '$.presence_penalty', cast(presence_penalty as decfloat));
  end if;

  if (json_value(options, '$.frequency_penalty') is not null) then
    set req_body = JSON_UPDATE(req_body, 'SET', '$.frequency_penalty', cast(frequency_penalty as decfloat));
  end if;

  if (stop_token is not null) then
    set req_body = JSON_UPDATE(req_body, 'SET', '$.stop', stop_token);
  end if;

  if (seed is not null) then
    set req_body = JSON_UPDATE(req_body, 'SET', '$.seed', seed);
  end if;

  if (user_id is not null) then
    set req_body = JSON_UPDATE(req_body, 'SET', '$.user', user_id);
  end if;

  -- Make the API call
  select response_message, response_http_header
  into response_message, response_header
  from table(qsys2.http_post_verbose(
               fullUrl,
               req_body,
               http_options));

  -- Get HTTP status code
  set response_code = json_value(response_header, '$.HTTP_STATUS_CODE');

  -- Process the response
  if (response_code >= 200 and response_code < 300) then
    -- Extract the content from choices[0].message.content (chat completions schema)
    set response_text = json_value(response_message,
                          '$.choices[0].message.content'
                          returning clob(2G) ccsid 1208);

    -- If parse fails, return the entire JSON response as a fallback
    if (response_text is null) then
      call systools.lprintf('openai_compatible_chat_generate: content parse failed, returning full response');
      return response_message;
    end if;

    return response_text;
  end if;

  set response_text = 'An error has occured. Check the job log. HTTP response code was '
                      concat response_code concat ' from ' concat fullUrl;
  signal sqlstate '38002' set message_text = response_text;
  return null;
end;
