-- ## Twilio SMS utility functions
-- 
create or replace function dbsdk_v1.twilio_getnumber(phnum varchar(1000) ccsid 1208 default NULL) 
  returns varchar(1000) ccsid 1208
  modifies sql data
begin
  declare returnval varchar(1000) ccsid 1208;
  call dbsdk_v1.conf_initialize();
  set returnval = phnum;
  if (returnval is not null) then return returnval;end if;
  set returnval = dbsdk_v1.twilio_number;
  if (returnval is not null) then return returnval;end if;
  set returnval = (select twilio_number from dbsdk_v1.conf where USRPRF = CURRENT_USER);
  return returnval;
end;

create or replace procedure dbsdk_v1.twilio_setnumberforjob(phnum varchar(1000) ccsid 1208 default NULL) 
  modifies SQL DATA
begin
  set dbsdk_v1.twilio_number= phnum;
end;
create or replace procedure dbsdk_v1.twilio_setnumberforme(phnum varchar(1000) ccsid 1208 default NULL) 
  MODIFIES SQL DATA
begin
  MERGE INTO dbsdk_v1.conf tt USING (
    SELECT CURRENT_USER AS usrprf, phnum AS twilio_number
      FROM sysibm.sysdummy1
  ) live
  ON tt.usrprf = live.usrprf
  WHEN NOT MATCHED THEN INSERT (usrprf, twilio_number) VALUES (live.usrprf,
      live.twilio_number)
  WHEN MATCHED THEN UPDATE SET twilio_number = live.twilio_number;
end;

create or replace function dbsdk_v1.twilio_getsid(account_sid varchar(1000) ccsid 1208 default NULL) 
  returns varchar(1000) ccsid 1208
  modifies sql data
begin
  declare returnval varchar(1000) ccsid 1208;
  call dbsdk_v1.conf_initialize();
  set returnval = account_sid;
  if (returnval is not null) then return returnval;end if;
  set returnval = dbsdk_v1.twilio_sid;
  if (returnval is not null) then return returnval;end if;
  set returnval = (select twilio_sid from dbsdk_v1.conf where USRPRF = CURRENT_USER);
  return returnval;
end;

-- #### **Function:** `twilio_setsidforjob`
-- 
-- **Description:** Sets the Twilio Account SID for the current job
-- 
-- **Input parameters:**
-- - `ACCOUNT_SID` (required): The Twilio Account SID.
create or replace procedure dbsdk_v1.twilio_setsidforjob(account_sid varchar(1000) ccsid 1208 default NULL) 
  modifies SQL DATA
begin
  set dbsdk_v1.twilio_sid= account_sid;
end;


-- #### **Function:** `twilio_setsidforme`
-- 
-- **Description:** Sets the Twilio Account SID for the current user profile (persists across jobs)
-- 
-- **Input parameters:**
-- - `ACCOUNT_SID` (required): The Twilio Account SID.
create or replace procedure dbsdk_v1.twilio_setsidforme(account_sid varchar(1000) ccsid 1208 default NULL) 
  MODIFIES SQL DATA
begin
  MERGE INTO dbsdk_v1.conf tt USING (
    SELECT CURRENT_USER AS usrprf, account_sid AS twilio_sid
      FROM sysibm.sysdummy1
  ) live
  ON tt.usrprf = live.usrprf
  WHEN NOT MATCHED THEN INSERT (usrprf, twilio_sid) VALUES (live.usrprf,
      live.twilio_sid)
  WHEN MATCHED THEN UPDATE SET twilio_sid = live.twilio_sid;
end;







create or replace function dbsdk_v1.twilio_getauthtoken(authtoken varchar(1000) ccsid 1208 default NULL) 
  returns varchar(1000) ccsid 1208
  modifies sql data
begin
  declare returnval varchar(1000) ccsid 1208;
  call dbsdk_v1.conf_initialize();
  set returnval = authtoken;
  if (returnval is not null) then return returnval;end if;
  set returnval = dbsdk_v1.twilio_authtoken;
  if (returnval is not null) then return returnval;end if;
  set returnval = (select twilio_authtoken from dbsdk_v1.conf where USRPRF = CURRENT_USER);
  return returnval;
end;

-- #### **Function:** `twilio_setauthtokenforjob`
-- 
-- **Description:** Sets the Twilio authentication token for the current job
-- 
-- **Input parameters:**
-- - `AUTHTOKEN` (required): The Twilio authentication token.
create or replace procedure dbsdk_v1.twilio_setauthtokenforjob(authtoken varchar(1000) ccsid 1208 default NULL) 
  modifies SQL DATA
begin
  set dbsdk_v1.twilio_authtoken= authtoken;
end;


-- #### **Function:** `twilio_setauthtokenforme`
-- 
-- **Description:** Sets the Twilio authentication token for the current user profile (persists across jobs)
-- 
-- **Input parameters:**
-- - `AUTHTOKEN` (required): The Twilio authentication token.
create or replace procedure dbsdk_v1.twilio_setauthtokenforme(authtoken varchar(1000) ccsid 1208 default NULL) 
  MODIFIES SQL DATA
begin
  MERGE INTO dbsdk_v1.conf tt USING (
    SELECT CURRENT_USER AS usrprf, authtoken AS twilio_authtoken
      FROM sysibm.sysdummy1
  ) live
  ON tt.usrprf = live.usrprf
  WHEN NOT MATCHED THEN INSERT (usrprf, twilio_authtoken) VALUES (live.usrprf,
      live.twilio_authtoken)
  WHEN MATCHED THEN UPDATE SET twilio_authtoken = live.twilio_authtoken;
end;

-- #### **Function:** `twilio_getbaseurl`
--
-- **Description:** Gets the base URL of the Twilio API, which `twilio_sendsms` calls. It can be
-- changed, for example to route requests through a proxy or to a mock server for testing. The value
-- used is the first one that is set: the function argument, then the job setting
-- (`twilio_setbaseurlforjob`), then the user profile setting (`twilio_setbaseurlforme`), then the
-- default.
--
-- **Input parameters:**
-- - `BASEURL` (optional): A base URL to return instead of the configured one.
--
-- **Return type:**
-- - `varchar(1000) ccsid 1208`
--
-- **Return value:**
-- - The base URL without a trailing `/`. Defaults to `https://api.twilio.com`.
create or replace function dbsdk_v1.twilio_getbaseurl(baseurl varchar(1000) ccsid 1208 default NULL)
  returns varchar(1000) ccsid 1208
  reads sql data
begin
  declare returnval varchar(1000) ccsid 1208;
  set returnval = baseurl;
  if (returnval is null) then
    set returnval = dbsdk_v1.twilio_baseurl;
  end if;
  if (returnval is null) then
    set returnval = (select twilio_baseurl from dbsdk_v1.conf where USRPRF = CURRENT_USER);
  end if;
  if (returnval is null or trim(returnval) = '') then
    set returnval = 'https://api.twilio.com';
  end if;
  return rtrim(trim(returnval), '/');
end;

-- #### **Procedure:** `twilio_setbaseurlforjob`
--
-- **Description:** Sets the Twilio API base URL to be used for the current job.
--
-- **Input parameters:**
-- - `BASEURL` (required): The base URL, e.g. `https://api.twilio.com`. NULL clears the job
--   setting, so the user profile setting or the default applies.
create or replace procedure dbsdk_v1.twilio_setbaseurlforjob(baseurl varchar(1000) ccsid 1208 default NULL)
  modifies SQL DATA
begin
  set dbsdk_v1.twilio_baseurl = baseurl;
end;

-- #### **Procedure:** `twilio_setbaseurlforme`
--
-- **Description:** Sets the Twilio API base URL to be used for the current user profile (persists
-- across jobs).
--
-- **Input parameters:**
-- - `BASEURL` (required): The base URL, e.g. `https://api.twilio.com`. NULL clears the user
--   profile setting, so the default applies.
create or replace procedure dbsdk_v1.twilio_setbaseurlforme(baseurl varchar(1000) ccsid 1208 default NULL)
  MODIFIES SQL DATA
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  MERGE INTO dbsdk_v1.conf tt USING (
    SELECT CURRENT_USER AS usrprf, baseurl AS twilio_baseurl
      FROM sysibm.sysdummy1
  ) live
  ON tt.usrprf = live.usrprf
  WHEN NOT MATCHED THEN INSERT (usrprf, twilio_baseurl) VALUES (live.usrprf,
      live.twilio_baseurl)
  WHEN MATCHED THEN UPDATE SET twilio_baseurl = live.twilio_baseurl;
end;
