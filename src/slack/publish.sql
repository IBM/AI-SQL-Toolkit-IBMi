-- ## Slack main functionality
-- 
-- #### **Function:** `slack_sendmessage`
-- 
-- **Description:** Sends a slack message
-- 
-- **Input parameters:**
-- - `MSG` (required): The message to send.
-- - `WEBHOOK` (optional): The Slack webhook address.
-- 
-- **Return type:** 
-- - `varchar(32000) ccsid 1208`
-- 
-- **Return value:**
-- - Response message from underlying Slack API, if there was one

create or replace function dbsdk_v1.slack_sendmessage(msg varchar(32000) ccsid 1208 default NULL, webhook varchar(1000) ccsid 1208 default NULL)
  RETURNS varchar(32000) ccsid 1208
  modifies sql data
  not deterministic
  no external action
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  declare response_header Varchar(10000) CCSID 1208;
  declare response_message Varchar(10000) CCSID 1208;
  declare response_code int default 500;
          
  select RESPONSE_MESSAGE, RESPONSE_HTTP_HEADER
  into response_message, response_header
  from table(QSYS2.HTTP_POST_VERBOSE(
                        dbsdk_v1.slack_getwebhook(webhook),
                        json_object('text': msg),
                        json_object('header': 'Content-type,application/json')
                        ));
                        
  set response_code = json_value(response_header, '$.HTTP_STATUS_CODE');
  
  if (response_code >= 200 and response_code < 300) then
    return response_message;
  end if;
  if(response_message is not null) then
    call systools.lprintf('Slack publish gave message status: ' concat response_message);
    signal sqlstate '38002' set message_text = response_message;
    return response_message;
  end if;
  signal sqlstate '38002' set message_text = 'An error has occured. Check the job log.';
  return response_message;
end;