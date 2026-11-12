-- ## Twilio SMS main functionality
-- 
-- #### **Function:** `twilio_sendsms`
-- 
-- **Description:** Use Twilio to send an SMS message
-- through the Twilio API at the base URL returned by `twilio_getbaseurl` (by default
-- `https://api.twilio.com`).
-- 
-- **Input parameters:**
-- - `TO_CELL_NUMBER` (required): The destination cell phone number.
-- - `MSG` (required): The SMS message body.
-- - `TWILIO_NUMBER` (optional): The message to publish.
-- 
-- **Return type:** 
-- - `varchar(32000) ccsid 1208`
-- 
-- **Return value:**
-- - Response message from API call
create or replace function dbsdk_v1.twilio_sendsms(to_cell_number varchar(100) ccsid 1208 default NULL, msg varchar(110) ccsid 1208 default NULL, twilio_number varchar(100) ccsid 1208 default NULL)
  RETURNS varchar(32000) ccsid 1208
  modifies sql data
  not deterministic
  no external action
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  declare fullUrl varchar(32500) ccsid 1208 default NULL;
  declare payload varchar(32500) ccsid 1208 default NULL;
  declare http_options  varchar(32500) CCSID 1208 default NULL;
  declare msg_status Varchar(10000) CCSID 1208;
  declare apierr Varchar(10000) CCSID 1208;
  declare response_header Varchar(10000) CCSID 1208;
  declare response_message Varchar(10000) CCSID 1208;
  declare response_code int default 500;
  
  set http_options = json_object('basicAuth': dbsdk_v1.twilio_getsid() concat ',' concat dbsdk_v1.twilio_getauthtoken(), 'header': 'content-type,application/x-www-form-urlencoded');
  
  set fullUrl = dbsdk_v1.twilio_getbaseurl() concat '/2010-04-01/Accounts/' concat dbsdk_v1.twilio_getsid() concat '/Messages.json';
  set payload = 'To=' concat to_cell_number concat
          '&From=' concat dbsdk_v1.twilio_getnumber(twilio_number) concat '&Body=' concat
          msg;
          
  select RESPONSE_MESSAGE, RESPONSE_HTTP_HEADER
  into response_message, response_header
  from table(QSYS2.HTTP_POST_VERBOSE(
                        fullUrl,
                        payload,
                        http_options
                        ));
                        
  set response_code = json_value(response_header, '$.HTTP_STATUS_CODE');
  set msg_status = json_value(response_message, '$.status');
  set apierr = json_value(response_message, '$.message');

  
  if (response_code >= 200 and response_code < 300) then
    return msg_status;
  end if;
  if(apierr is not null) then
    call systools.lprintf('Twilio publish gave message status: ' concat msg_status);
    signal sqlstate '38002' set message_text = apierr;
    return msg_status;
  end if;
  signal sqlstate '38002' set message_text = 'An error has occured. Check the job log.';
  return msg_status;
end;