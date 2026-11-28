
-- ## Kafka main functionality

-- #### **Function:** `kafka_publish`
-- 
-- **Description:** publishes a message to the kafka topic
-- 
-- **Input parameters:**
-- - `TOPIC` (optional): The topic to publish the message on.
-- - `KEY` (required): The message to publish.
-- - `MSGDATA` (required): The message to publish.
-- 
-- **Return type:** 
-- - `varchar(32000) ccsid 1208`
-- 
-- **Return value:**
-- - API error, if any was encountered
create or replace function aisqltkt.kafka_publish(msgdata varchar(32000) ccsid 1208 default NULL, topic varchar(1000) ccsid 1208 default NULL, key varchar(1000) ccsid 1208 default NULL)
  RETURNS varchar(32000) ccsid 1208
  modifies sql data
  not deterministic
  no external action
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  declare fullUrl varchar(32500) ccsid 1208 default NULL;
  declare apierr  varchar(32500) CCSID 1208 default NULL;
  declare response_header Varchar(10000) CCSID 1208;
  declare response_message Varchar(10000) CCSID 1208;
  declare response_code int default 500;
  
  declare http_options varchar(32400) ccsid 1208 default '{"headers":{"Content-Type":"application/vnd.kafka.json.v2+json","Accept":"application/vnd.kafka.v2+json"}}';
  set fullUrl = aisqltkt.kafka_getprotocol() concat '://' concat aisqltkt.kafka_getbroker() concat ':' concat aisqltkt.kafka_getport() concat '/topics/' concat aisqltkt.kafka_gettopic(topic);

  select RESPONSE_MESSAGE, RESPONSE_HTTP_HEADER
  into response_message, response_header
  from table(QSYS2.HTTP_POST_VERBOSE(
                        fullUrl,
                        json_object('records': json_array(json_object('key': key, 'value':msgdata))),
                        http_options));
  set response_code = json_value(response_header, '$.HTTP_STATUS_CODE');
  set apierr = json_value(response_message, '$.offsets[0].error');
  if(apierr is not null) then
    call systools.lprintf('Kafka publish returned error: ' concat apierr);
    return apierr;
  end if;
  
  if (response_code = 200) then
    return null;
  end if;

  signal sqlstate '38002' set message_text = 'Add error has occured. Check the job log.';
  return apierr;
end;