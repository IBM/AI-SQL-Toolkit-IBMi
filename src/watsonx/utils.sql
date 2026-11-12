
-- ## Utility functions

-- ### Service locations
--
-- By default the watsonx functions call IBM Cloud: `https://iam.cloud.ibm.com` for access tokens
-- and `https://<region>.ml.cloud.ibm.com` for everything else. Both can be changed, for example to
-- route requests through a proxy or to a mock server for testing. The value used is the first one
-- that is set: the function argument, then the job setting (`...ForJob`), then the user profile
-- setting (`...ForMe`), then the default.

-- #### **Function:** `wx_GetIamBaseUrl`
--
-- **Description:** Gets the base URL of the IBM Cloud IAM service, which `wx_authenticate` calls
-- to get an access token.
--
-- **Input parameters:**
-- - `BASEURL` (optional): A base URL to return instead of the configured one.
--
-- **Return type:**
-- - `varchar(1000) ccsid 1208`
--
-- **Return value:**
-- - The base URL without a trailing `/`. Defaults to `https://iam.cloud.ibm.com`.
create or replace function dbsdk_v1.wx_GetIamBaseUrl(baseurl varchar(1000) ccsid 1208 default NULL)
  returns varchar(1000) ccsid 1208
  reads sql data
begin
  declare returnval varchar(1000) ccsid 1208;
  set returnval = baseurl;
  if (returnval is null) then
    set returnval = dbsdk_v1.wx_iam_baseurl;
  end if;
  if (returnval is null) then
    set returnval = (select watsonx_iam_baseurl from dbsdk_v1.conf where USRPRF = CURRENT_USER);
  end if;
  if (returnval is null or trim(returnval) = '') then
    set returnval = 'https://iam.cloud.ibm.com';
  end if;
  return rtrim(trim(returnval), '/');
end;

-- #### **Procedure:** `wx_SetIamBaseUrlForJob`
--
-- **Description:** Sets the IBM Cloud IAM base URL to be used for the current job.
--
-- **Input parameters:**
-- - `BASEURL` (required): The base URL, e.g. `https://iam.cloud.ibm.com`. NULL clears the job
--   setting, so the user profile setting or the default applies.
create or replace procedure dbsdk_v1.wx_SetIamBaseUrlForJob(baseurl varchar(1000) ccsid 1208 default NULL)
  program type sub
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  set dbsdk_v1.wx_iam_baseurl = baseurl;
end;

-- #### **Procedure:** `wx_SetIamBaseUrlForMe`
--
-- **Description:** Sets the IBM Cloud IAM base URL to be used for the current user profile
-- (persists across jobs).
--
-- **Input parameters:**
-- - `BASEURL` (required): The base URL, e.g. `https://iam.cloud.ibm.com`. NULL clears the user
--   profile setting, so the default applies.
create or replace procedure dbsdk_v1.wx_SetIamBaseUrlForMe(baseurl varchar(1000) ccsid 1208 default NULL)
  modifies sql data
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  MERGE INTO dbsdk_v1.conf tt USING (
    SELECT CURRENT_USER AS usrprf, baseurl AS watsonx_iam_baseurl
      FROM sysibm.sysdummy1
  ) live
  ON tt.usrprf = live.usrprf
  WHEN NOT MATCHED THEN INSERT (usrprf, watsonx_iam_baseurl) VALUES (live.usrprf,
      live.watsonx_iam_baseurl)
  WHEN MATCHED THEN UPDATE SET watsonx_iam_baseurl = live.watsonx_iam_baseurl;
end;

-- #### **Function:** `wx_GetMlBaseUrl`
--
-- **Description:** Gets the base URL of the watsonx.ai service, which `wx_generate` and
-- `wx_getmodels` call. The `/ml/v1/...` path is added to it.
--
-- **Input parameters:**
-- - `BASEURL` (optional): A base URL to return instead of the configured one.
--
-- **Return type:**
-- - `varchar(1000) ccsid 1208`
--
-- **Return value:**
-- - The base URL without a trailing `/`. Defaults to `https://<region>.ml.cloud.ibm.com`, where
--   `<region>` is the value of `dbsdk_v1.wx_region`.
create or replace function dbsdk_v1.wx_GetMlBaseUrl(baseurl varchar(1000) ccsid 1208 default NULL)
  returns varchar(1000) ccsid 1208
  reads sql data
begin
  declare returnval varchar(1000) ccsid 1208;
  set returnval = baseurl;
  if (returnval is null) then
    set returnval = dbsdk_v1.wx_ml_baseurl;
  end if;
  if (returnval is null) then
    set returnval = (select watsonx_ml_baseurl from dbsdk_v1.conf where USRPRF = CURRENT_USER);
  end if;
  if (returnval is null or trim(returnval) = '') then
    set returnval = 'https://' concat dbsdk_v1.wx_region concat '.ml.cloud.ibm.com';
  end if;
  return rtrim(trim(returnval), '/');
end;

-- #### **Procedure:** `wx_SetMlBaseUrlForJob`
--
-- **Description:** Sets the watsonx.ai base URL to be used for the current job.
--
-- **Input parameters:**
-- - `BASEURL` (required): The base URL, e.g. `https://us-south.ml.cloud.ibm.com`. NULL clears the job
--   setting, so the user profile setting or the default applies.
create or replace procedure dbsdk_v1.wx_SetMlBaseUrlForJob(baseurl varchar(1000) ccsid 1208 default NULL)
  program type sub
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  set dbsdk_v1.wx_ml_baseurl = baseurl;
end;

-- #### **Procedure:** `wx_SetMlBaseUrlForMe`
--
-- **Description:** Sets the watsonx.ai base URL to be used for the current user profile (persists
-- across jobs).
--
-- **Input parameters:**
-- - `BASEURL` (required): The base URL, e.g. `https://us-south.ml.cloud.ibm.com`. NULL clears the user
--   profile setting, so the default applies.
create or replace procedure dbsdk_v1.wx_SetMlBaseUrlForMe(baseurl varchar(1000) ccsid 1208 default NULL)
  modifies sql data
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  MERGE INTO dbsdk_v1.conf tt USING (
    SELECT CURRENT_USER AS usrprf, baseurl AS watsonx_ml_baseurl
      FROM sysibm.sysdummy1
  ) live
  ON tt.usrprf = live.usrprf
  WHEN NOT MATCHED THEN INSERT (usrprf, watsonx_ml_baseurl) VALUES (live.usrprf,
      live.watsonx_ml_baseurl)
  WHEN MATCHED THEN UPDATE SET watsonx_ml_baseurl = live.watsonx_ml_baseurl;
end;

create or replace function dbsdk_v1.GetUrl(route varchar(1000))
  returns varchar(2048) ccsid 1208
  reads sql data
begin
  declare finalUrl varchar(2048) ccsid 1208;

  set finalUrl = dbsdk_v1.wx_GetMlBaseUrl() concat '/ml/v1' concat route;

  if (dbsdk_v1.wx_apiVersion is not null and dbsdk_v1.wx_apiVersion != '') then
    set finalUrl = finalUrl concat '?version=' concat dbsdk_v1.wx_apiVersion;
  end if;

  return finalUrl;
end;

-- #### **Function:** `watsonx_SetApiKeyForJob`
-- 
-- **Description:** Sets the watsonx API key to be used for the current job
-- 
-- **Input parameters:**
-- - `APIKEY` (required): The API key.
create or replace procedure dbsdk_v1.wx_SetApiKeyForJob(apikey varchar(100))
  program type sub
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  set dbsdk_v1.wx_apikey = apikey;
end;

-- #### **Function:** `watsonx_SetProjectIdForJob`
-- 
-- **Description:** Sets the watsonx project ID to be used for the current job
-- 
-- **Input parameters:**
-- - `PROJECTID` (required): The project ID.
create or replace procedure dbsdk_v1.wx_SetProjectIdForJob(projectid varchar(100))
  program type sub
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  set dbsdk_v1.wx_projectid = projectid;
end;

create or replace procedure dbsdk_v1.wx_SetBearerTokenForJob(bearer_token varchar(10000), expires integer)
  modifies sql data
  program type sub
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  set dbsdk_v1.wx_JobBearerToken = bearer_token;
  -- We subtract 60 seconds from the expiration time to ensure we don't cut it too close
  set dbsdk_v1.wx_JobTokenExpires = current timestamp + expires seconds - 60 seconds;
end;

create or replace function dbsdk_v1.wx_ShouldGetNewToken() 
  returns char(1)
begin
  if (dbsdk_v1.wx_JobBearerToken is null) then
    return 'Y';
  end if;
  if (dbsdk_v1.wx_JobTokenExpires is null) then
    return 'Y';
  end if;
  if (current timestamp > dbsdk_v1.wx_JobTokenExpires) then
    return 'Y';
  end if;
  return 'N';
end;

-- #### **Function:** `watsonx_logoutJob`
-- 
-- **Description:** Log out from teh current job.
-- 
create or replace procedure dbsdk_v1.wx_logoutJob()
  program type sub
  modifies sql data
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  set dbsdk_v1.wx_JobBearerToken = null;
  set dbsdk_v1.wx_JobTokenExpires = null;
end;

create or replace function dbsdk_v1.parameters(
  decoding_method varchar(20) default null,
  temperature numeric(1, 1) default null,
  time_limit integer default null,
  top_p numeric(1, 1) default null,
  top_k integer default null,
  random_seed integer default null,
  repetition_penalty numeric(1, 1) default null,
  truncate_input_tokens integer default null,
  min_new_tokens integer default null,
  max_new_tokens integer default null,
  typical_p numeric(1, 1) default null
) 
  returns varchar(1000) ccsid 1208
begin
  return json_object(
    'decoding_method': decoding_method,
    'temperature': temperature format json,
    'time_limit': time_limit format json,
    'top_p': top_p format json, 
    'top_k': top_k format json, 
    'random_seed': random_seed format json, 
    'repetition_penalty': repetition_penalty format json, 
    'truncate_input_tokens': truncate_input_tokens format json,
    'min_new_tokens': min_new_tokens format json, 
    'max_new_tokens': max_new_tokens format json, 
    'typical_p': typical_p format json
    ABSENT ON NULL
  );
end;