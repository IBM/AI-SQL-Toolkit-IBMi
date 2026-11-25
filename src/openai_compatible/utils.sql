-- ## OpenAI-compatible endpoints utility functions

create or replace function dbsdk_v1.openai_compatible_getserver(hostname varchar(1000) ccsid 1208 default NULL) 
  returns varchar(1000) ccsid 1208
  modifies sql data
begin
  declare returnval varchar(1000) ccsid 1208;
  call dbsdk_v1.conf_initialize();
  set returnval = hostname;
  if (returnval is not null) then return returnval;end if;
  set returnval = dbsdk_v1.openai_compatible_server;
  if (returnval is not null) then return returnval;end if;
  set returnval = (select openai_compatible_server from dbsdk_v1.conf where USRPRF = CURRENT_USER);
  return returnval;
end;

-- ### procedure: `openai_compatible_setserverforjob`

-- **Description:** sets the OpenAI-compatible server to be used for this job

-- **Input parameters:**
-- - `HOSTNAME` (required): The IP address or hostname of the OpenAI-compatible server.
create or replace procedure dbsdk_v1.openai_compatible_setserverforjob(hostname varchar(1000) ccsid 1208 default NULL) 
  modifies SQL DATA
begin
  set dbsdk_v1.openai_compatible_server = hostname;
end;

-- ### procedure: `openai_compatible_setserverforme`

-- **Description:** sets the OpenAI-compatible server to be used for this user profile (persists across jobs)

-- **Input parameters:**
-- - `HOSTNAME` (required): The IP address or hostname of the OpenAI-compatible server.
create or replace procedure dbsdk_v1.openai_compatible_setserverforme(hostname varchar(1000) ccsid 1208 default NULL)
  MODIFIES SQL DATA
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  MERGE INTO dbsdk_v1.conf tt USING (
    SELECT CURRENT_USER AS usrprf, hostname AS openai_compatible_server
      FROM sysibm.sysdummy1
  ) live
  ON tt.usrprf = live.usrprf
  WHEN NOT MATCHED THEN INSERT (usrprf, openai_compatible_server) VALUES (live.usrprf,
      live.openai_compatible_server)
  WHEN MATCHED THEN UPDATE SET openai_compatible_server = live.openai_compatible_server;
end;

create or replace function dbsdk_v1.openai_compatible_getport(port INT default NULL) 
  returns INT
  modifies sql data
begin
  declare returnval varchar(1000) ccsid 1208;
  call dbsdk_v1.conf_initialize();
  set returnval = port;
  if (returnval is not null) then return returnval;end if;
  set returnval = dbsdk_v1.openai_compatible_port;
  if (returnval is not null) then return returnval;end if;
  set returnval = (select openai_compatible_port from dbsdk_v1.conf where USRPRF = CURRENT_USER);
  return returnval;
end;

-- ### procedure: `openai_compatible_setportforjob`

-- **Description:** sets the OpenAI-compatible server port to be used for this job

-- **Input parameters:**
-- - `PORT` (required): The OpenAI-compatible server port.
create or replace procedure dbsdk_v1.openai_compatible_setportforjob(port INT default NULL) 
  modifies SQL DATA
begin
  set dbsdk_v1.openai_compatible_port = port;
end;

-- ### procedure: `openai_compatible_setportforme`

-- **Description:** sets the OpenAI-compatible server port to be used for this user profile (persists across jobs).

-- **Input parameters:**
-- - `PORT` (required): The OpenAI-compatible server port.
create or replace procedure dbsdk_v1.openai_compatible_setportforme(port INT default NULL)
  MODIFIES SQL DATA
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  MERGE INTO dbsdk_v1.conf tt USING (
    SELECT CURRENT_USER AS usrprf, port AS openai_compatible_port
      FROM sysibm.sysdummy1
  ) live
  ON tt.usrprf = live.usrprf
  WHEN NOT MATCHED THEN INSERT (usrprf, openai_compatible_port) VALUES (live.usrprf,
      live.openai_compatible_port)
  WHEN MATCHED THEN UPDATE SET openai_compatible_port = live.openai_compatible_port;
end;

create or replace function dbsdk_v1.openai_compatible_getmodel(model varchar(1000) ccsid 1208 default NULL) 
  returns varchar(1000) ccsid 1208
  modifies sql data
begin
  declare returnval varchar(1000) ccsid 1208;
  call dbsdk_v1.conf_initialize();
  set returnval = model;
  if (returnval is not null) then return returnval;end if;
  set returnval = dbsdk_v1.openai_compatible_model;
  if (returnval is not null) then return returnval;end if;
  set returnval = (select openai_compatible_model from dbsdk_v1.conf where USRPRF = CURRENT_USER);
  return returnval;
end;

-- ### procedure: `openai_compatible_setmodelforjob`

-- **Description:** sets the large language model (LLM) to be used for this job

-- **Input parameters:**
-- - `MODEL` (required): The model identifier to use.
create or replace procedure dbsdk_v1.openai_compatible_setmodelforjob(model varchar(1000) ccsid 1208 default NULL) 
  modifies SQL DATA
begin
  set dbsdk_v1.openai_compatible_model = model;
end;

-- ### procedure: `openai_compatible_setmodelforme`

-- **Description:** sets the large language model (LLM) to be used for this user profile (persists across jobs)

-- **Input parameters:**
-- - `MODEL` (required): The model identifier to use.
create or replace procedure dbsdk_v1.openai_compatible_setmodelforme(model varchar(1000) ccsid 1208 default NULL)
  MODIFIES SQL DATA
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  MERGE INTO dbsdk_v1.conf tt USING (
    SELECT CURRENT_USER AS usrprf, model AS openai_compatible_model
      FROM sysibm.sysdummy1
  ) live
  ON tt.usrprf = live.usrprf
  WHEN NOT MATCHED THEN INSERT (usrprf, openai_compatible_model) VALUES (live.usrprf,
      live.openai_compatible_model)
  WHEN MATCHED THEN UPDATE SET openai_compatible_model = live.openai_compatible_model;
end;

create or replace function dbsdk_v1.openai_compatible_getprotocol(protocol varchar(1000) ccsid 1208 default NULL) 
  returns varchar(1000) ccsid 1208
  modifies sql data
begin
  declare returnval varchar(1000) ccsid 1208;
  call dbsdk_v1.conf_initialize();
  set returnval = protocol;
  if (returnval is not null) then return returnval;end if;
  set returnval = dbsdk_v1.openai_compatible_protocol;
  if (returnval is not null) then return returnval;end if;
  set returnval = (select openai_compatible_protocol from dbsdk_v1.conf where USRPRF = CURRENT_USER);
  return returnval;
end;

-- ### procedure: `openai_compatible_setprotocolforjob`

-- **Description:** sets the protocol to be used for this job

-- **Input parameters:**
-- - `PROTOCOL` (required): `http`/`https`
create or replace procedure dbsdk_v1.openai_compatible_setprotocolforjob(protocol varchar(1000) ccsid 1208 default NULL) 
  modifies SQL DATA
begin
  set dbsdk_v1.openai_compatible_protocol = protocol;
end;

-- ### procedure: `openai_compatible_setprotocolforme`
-- 
-- **Description:** sets the protocol to be used for this user profile (persists across jobs)
-- 
-- **Input parameters:**
-- - `PROTOCOL` (required): `http`/`https`
create or replace procedure dbsdk_v1.openai_compatible_setprotocolforme(protocol varchar(1000) ccsid 1208 default NULL)
  MODIFIES SQL DATA
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  MERGE INTO dbsdk_v1.conf tt USING (
    SELECT CURRENT_USER AS usrprf, protocol AS openai_compatible_protocol
      FROM sysibm.sysdummy1
  ) live
  ON tt.usrprf = live.usrprf
  WHEN NOT MATCHED THEN INSERT (usrprf, openai_compatible_protocol) VALUES (live.usrprf,
      live.openai_compatible_protocol)
  WHEN MATCHED THEN UPDATE SET openai_compatible_protocol = live.openai_compatible_protocol;
end;

-- Function for API key management
create or replace function dbsdk_v1.openai_compatible_getapikey(api_key varchar(8000) ccsid 1208 default NULL) 
  returns varchar(8000) ccsid 1208
  modifies sql data
begin
  declare returnval varchar(8000) ccsid 1208;
  call dbsdk_v1.conf_initialize();
  set returnval = api_key;
  if (returnval is not null) then return returnval;end if;
  set returnval = dbsdk_v1.openai_compatible_apikey;
  if (returnval is not null) then return returnval;end if;
  set returnval = (select openai_compatible_apikey from dbsdk_v1.conf where USRPRF = CURRENT_USER);
  return returnval;
end;

-- ### procedure: `openai_compatible_setapikeyforjob`

-- **Description:** sets the API key to be used for this job

-- **Input parameters:**
-- - `API_KEY` (required): The API key for authentication.
create or replace procedure dbsdk_v1.openai_compatible_setapikeyforjob(api_key varchar(8000) ccsid 1208 default NULL) 
  modifies SQL DATA
begin
  set dbsdk_v1.openai_compatible_apikey = api_key;
end;

-- ### procedure: `openai_compatible_setapikeyforme`
-- 
-- **Description:** sets the API key to be used for this user profile (persists across jobs)
-- 
-- **Input parameters:**
-- - `API_KEY` (required): The API key for authentication.
create or replace procedure dbsdk_v1.openai_compatible_setapikeyforme(api_key varchar(8000) ccsid 1208 default NULL)
  MODIFIES SQL DATA
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  MERGE INTO dbsdk_v1.conf tt USING (
    SELECT CURRENT_USER AS usrprf, api_key AS openai_compatible_apikey
      FROM sysibm.sysdummy1
  ) live
  ON tt.usrprf = live.usrprf
  WHEN NOT MATCHED THEN INSERT (usrprf, openai_compatible_apikey) VALUES (live.usrprf,
      live.openai_compatible_apikey)
  WHEN MATCHED THEN UPDATE SET openai_compatible_apikey = live.openai_compatible_apikey;
end;

-- Function for base path configuration (for servers with non-standard API paths)
create or replace function dbsdk_v1.openai_compatible_getbasepath(base_path varchar(1000) ccsid 1208 default NULL) 
  returns varchar(1000) ccsid 1208
  modifies sql data
begin
  declare returnval varchar(1000) ccsid 1208;
  call dbsdk_v1.conf_initialize();
  set returnval = base_path;
  if (returnval is not null) then return returnval;end if;
  set returnval = dbsdk_v1.openai_compatible_basepath;
  if (returnval is not null) then return returnval;end if;
  set returnval = (select openai_compatible_basepath from dbsdk_v1.conf where USRPRF = CURRENT_USER);
  return coalesce(returnval, '/v1'); -- Default to standard OpenAI path if not set
end;

-- ### procedure: `openai_compatible_setbasepathforjob`

-- **Description:** sets the API base path to be used for this job

-- **Input parameters:**
-- - `BASE_PATH` (required): The base path for API endpoints.
create or replace procedure dbsdk_v1.openai_compatible_setbasepathforjob(base_path varchar(1000) ccsid 1208 default NULL) 
  modifies SQL DATA
begin
  set dbsdk_v1.openai_compatible_basepath = base_path;
end;

-- ### procedure: `openai_compatible_setbasepathforme`
-- 
-- **Description:** sets the API base path to be used for this user profile (persists across jobs)
-- 
-- **Input parameters:**
-- - `BASE_PATH` (required): The base path for API endpoints.
create or replace procedure dbsdk_v1.openai_compatible_setbasepathforme(base_path varchar(1000) ccsid 1208 default NULL)
  MODIFIES SQL DATA
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  MERGE INTO dbsdk_v1.conf tt USING (
    SELECT CURRENT_USER AS usrprf, base_path AS openai_compatible_basepath
      FROM sysibm.sysdummy1
  ) live
  ON tt.usrprf = live.usrprf
  WHEN NOT MATCHED THEN INSERT (usrprf, openai_compatible_basepath) VALUES (live.usrprf,
      live.openai_compatible_basepath)
  WHEN MATCHED THEN UPDATE SET openai_compatible_basepath = live.openai_compatible_basepath;
end;

-- ## JSON Object Update Function
--
-- **Description:** Merges two JSON objects into one. The merge is shallow: when a key exists in both
-- objects, the value from `update_object` replaces the whole value from `base_object`.
--
-- **Input parameters:**
-- - `base_object` (required): The base JSON object
-- - `update_object` (required): The object to merge into the base object. A value that is neither
--   NULL, empty, nor a JSON object is rejected with SQLSTATE `38002`.
--
-- **Return type:** 
-- - `clob(64K) ccsid 1208`
-- 
-- **Return value:**
-- - A merged JSON object

create or replace function dbsdk_v1.json_object_update(
  base_object CLOB(2G) ccsid 1208,
  update_object CLOB(2G) ccsid 1208
)
  returns clob(64K) ccsid 1208
  deterministic
  no external action
  not fenced
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  declare clean_update varchar(32000) ccsid 1208;
  declare clean_base clob(2G) ccsid 1208;
  declare kept_base clob(2G) ccsid 1208;
  declare len integer;
  declare pos integer default 1;
  declare c character(1) ccsid 1208;
  declare in_string boolean default false;
  declare escaped boolean default false;
  declare curly_depth integer default 0;
  declare square_depth integer default 0;
  
  declare key_start integer default 0;
  declare key_end integer default 0;
  declare key_name varchar(1000) ccsid 1208;
  declare skip_until_comma boolean default false;
  declare keys_to_overwrite varchar(32000) ccsid 1208;
  
  declare item_start integer default 1;
  declare item_str varchar(32000) ccsid 1208;
  declare has_overwrite_key boolean default false;
  declare closing integer default 0;

  -- 1. Handle NULL and empty cases
  -- "two NULLs give {}"
  if base_object is null and update_object is null then
    return '{}';
  end if;

  -- update_object must be a JSON object. This is checked before the early returns below, so it is
  -- rejected even when base_object is empty. Step 4 always strips the first and last character of
  -- update_object, so anything that is not a JSON object would silently produce invalid JSON.
  if update_object is not null and trim(update_object) <> '' then
    if not (left(trim(update_object), 1) = '{' and right(trim(update_object), 1) = '}') then
      signal sqlstate '38002' set message_text = 'update_object must be a JSON object.';
    end if;
  end if;

  -- "a NULL base object returns the update object"
  -- "an empty base object returns the update object"
  if base_object is null or trim(base_object) = '' or trim(base_object) = '{}' then
    if update_object is null then
      return '{}';
    end if;
    return update_object;
  end if;
  
  -- "a NULL update object returns the base object"
  -- "an empty update object returns the base object"
  if update_object is null or trim(update_object) = '' or trim(update_object) = '{}' then
    return base_object;
  end if;

  -- 2. Build the list of keys to overwrite from the update_object
  -- Strip the braces without combining LENGTH with SUBSTRING: for CCSID 1208 text LENGTH counts
  -- bytes but SUBSTRING counts characters, so substring(x, 2, length(x) - 2) keeps the closing
  -- brace when the text contains non-ASCII characters. Instead drop the first character with
  -- SUBSTRING(x, 2), then drop exactly one closing brace: remove the whole run of trailing
  -- braces and put back all but one (each brace is one byte, so the byte counts are exact).
  set clean_update = substring(trim(update_object), 2);
  set closing = length(clean_update) - length(trim(trailing '}' from clean_update));
  set clean_update = trim(trailing '}' from clean_update) concat repeat('}', closing - 1);

  -- an update object with only whitespace between the braces, such as '{ }', is empty too
  if trim(clean_update) = '' then
    return base_object;
  end if;

  -- Each key is stored as ,"key", so that a key containing a comma can't match a different key.
  set keys_to_overwrite = '';
  set len = length(clean_update);
  set pos = 1;
  
  while (pos <= len) do
    set c = substring(clean_update, pos, 1);
    
    if c = '\' and not escaped then
      set escaped = true;
    else
      if not escaped then
        if c = '"' then
          set in_string = not in_string;
          
          if not skip_until_comma then
            if in_string and curly_depth = 0 and square_depth = 0 then
              set key_start = pos + 1;
            elseif not in_string and curly_depth = 0 and square_depth = 0 then
              set key_end = pos - 1;
              set key_name = substring(clean_update, key_start, key_end - key_start + 1);
              set keys_to_overwrite = keys_to_overwrite concat ',"' concat key_name concat '",';
              set skip_until_comma = true;
            end if;
          end if;
        elseif not in_string then
          if c = '{' then
            set curly_depth = curly_depth + 1;
          elseif c = '}' then
            set curly_depth = curly_depth - 1;
          elseif c = '[' then
            set square_depth = square_depth + 1;
          elseif c = ']' then
            set square_depth = square_depth - 1;
          elseif c = ',' and curly_depth = 0 and square_depth = 0 then
            set skip_until_comma = false;
          end if;
        end if;
      end if;
      set escaped = false;
    end if;
    
    set pos = pos + 1;
  end while;

  -- 3. Filter out those keys from the base_object
  set clean_base = trim(base_object);
  if (left(clean_base, 1) = '{' and right(clean_base, 1) = '}') then
    -- same brace stripping as for clean_update above
    set clean_base = substring(clean_base, 2);
    set closing = length(clean_base) - length(trim(trailing '}' from clean_base));
    set clean_base = trim(trailing '}' from clean_base) concat repeat('}', closing - 1);
  end if;

  set kept_base = '';
  set len = length(clean_base);
  set pos = 1;
  set in_string = false;
  set escaped = false;
  set curly_depth = 0;
  set square_depth = 0;
  set skip_until_comma = false;
  set item_start = 1;
  set has_overwrite_key = false;

  while (pos <= len) do
    set c = substring(clean_base, pos, 1);
    
    if c = '\' and not escaped then
      set escaped = true;
    else
      if not escaped then
        if c = '"' then
          set in_string = not in_string;
          
          if not skip_until_comma then
            if in_string and curly_depth = 0 and square_depth = 0 then
              set key_start = pos + 1;
            elseif not in_string and curly_depth = 0 and square_depth = 0 then
              set key_end = pos - 1;
              set key_name = substring(clean_base, key_start, key_end - key_start + 1);
              if locate(',"' concat key_name concat '",', keys_to_overwrite) > 0 then
                set has_overwrite_key = true;
              else
                set has_overwrite_key = false;
              end if;
              set skip_until_comma = true;
            end if;
          end if;
        elseif not in_string then
          if c = '{' then
            set curly_depth = curly_depth + 1;
          elseif c = '}' then
            set curly_depth = curly_depth - 1;
          elseif c = '[' then
            set square_depth = square_depth + 1;
          elseif c = ']' then
            set square_depth = square_depth - 1;
          elseif c = ',' and curly_depth = 0 and square_depth = 0 then
            if not has_overwrite_key then
              set item_str = trim(substring(clean_base, item_start, pos - item_start));
              if length(item_str) > 0 then
                if length(kept_base) > 0 then
                  set kept_base = kept_base concat ', ';
                end if;
                set kept_base = kept_base concat item_str;
              end if;
            end if;
            set item_start = pos + 1;
            set skip_until_comma = false;
            set has_overwrite_key = false;
          end if;
        end if;
      end if;
      set escaped = false;
    end if;
    
    set pos = pos + 1;
  end while;

  -- Process the last item
  if not has_overwrite_key then
    set item_str = trim(substring(clean_base, item_start, len - item_start + 1));
    if length(item_str) > 0 then
      if length(kept_base) > 0 then
        set kept_base = kept_base concat ', ';
      end if;
      set kept_base = kept_base concat item_str;
    end if;
  end if;

  -- 4. Combine the filtered base and the update_object
  if kept_base is null or kept_base = '' then
    return update_object;
  end if;

  return '{' concat
         kept_base concat
         ', ' concat
         clean_update concat
         '}';
end;