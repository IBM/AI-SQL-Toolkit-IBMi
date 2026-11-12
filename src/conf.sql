--variables for watsonx
create or replace variable dbsdk_v1.wx_region              varchar(16) ccsid 1208 default NULL;
create or replace variable dbsdk_v1.wx_apiVersion          varchar(10) ccsid 1208 default NULL;
create or replace variable dbsdk_v1.wx_apikey              varchar(100) ccsid 1208 default NULL;
create or replace variable dbsdk_v1.wx_projectid           varchar(100) ccsid 1208 default NULL;
create or replace variable dbsdk_v1.wx_JobBearerToken      varchar(10000) ccsid 1208 default null;
create or replace variable dbsdk_v1.wx_JobTokenExpires     timestamp;
create or replace variable dbsdk_v1.wx_iam_baseurl         varchar(1000) ccsid 1208 default NULL;
create or replace variable dbsdk_v1.wx_ml_baseurl          varchar(1000) ccsid 1208 default NULL;

-- variables for ollama
create or replace variable dbsdk_v1.ollama_protocol     varchar(16)   ccsid 1208 default null;
create or replace variable dbsdk_v1.ollama_server       varchar(1000) ccsid 1208 default null;
create or replace variable dbsdk_v1.ollama_port         INT                      default NULL;
create or replace variable dbsdk_v1.ollama_model        varchar(1000) ccsid 1208 default NULL;

-- variables for openai compatible
create or replace variable dbsdk_v1.openai_compatible_protocol     varchar(16)   ccsid 1208 default null;
create or replace variable dbsdk_v1.openai_compatible_server       varchar(1000) ccsid 1208 default null;
create or replace variable dbsdk_v1.openai_compatible_port         INT                      default NULL;
create or replace variable dbsdk_v1.openai_compatible_model        varchar(1000) ccsid 1208 default NULL;
create or replace variable dbsdk_v1.openai_compatible_apikey       varchar(8000) ccsid 1208 default NULL;
create or replace variable dbsdk_v1.openai_compatible_basepath     varchar(1000) ccsid 1208 default NULL;

-- variables for kafka
create or replace variable dbsdk_v1.kafka_protocol     varchar(16)   ccsid 1208 default null;
create or replace variable dbsdk_v1.kafka_broker       varchar(1000) ccsid 1208 default null;
create or replace variable dbsdk_v1.kafka_port         INT                      default NULL;
create or replace variable dbsdk_v1.kafka_topic        varchar(1000) ccsid 1208 default NULL;

-- variables for Slack
create or replace variable dbsdk_v1.slack_webhook      varchar(1000) ccsid 1208 default NULL;

-- variables for Twilio
create or replace variable dbsdk_v1.twilio_number      varchar(1000) ccsid 1208 default NULL;
create or replace variable dbsdk_v1.twilio_sid         varchar(1000) ccsid 1208 default NULL;
create or replace variable dbsdk_v1.twilio_authtoken   varchar(1000) ccsid 1208 default NULL;
create or replace variable dbsdk_v1.twilio_baseurl     varchar(1000) ccsid 1208 default NULL;

create or replace table dbsdk_v1.conf
      (
          USRPRF varchar(10),
          watsonx_region varchar(16) ccsid 1208 default 'us-south',
          watsonx_apiVersion varchar(10) ccsid 1208 default '2023-07-07',
          watsonx_apikey varchar(100) ccsid 1208 default NULL,
          watsonx_projectid varchar(100) ccsid 1208 default NULL,
          ollama_protocol varchar(16) ccsid 1208 default 'http',
          ollama_server varchar(1000) ccsid 1208 default 'localhost',
          ollama_port INT default 11434,
          ollama_model varchar(1000) ccsid 1208 default 'granite3.2:8b',
          openai_server varchar(1000) ccsid 1208 default 'localhost',
          openai_port INT default 443,
          openai_compatible_protocol varchar(16) ccsid 1208 default 'http',
          openai_compatible_server varchar(1000) ccsid 1208 default 'localhost',
          openai_compatible_port INT default 8000,
          openai_compatible_model varchar(1000) ccsid 1208 default 'llama3',
          openai_compatible_apikey varchar(8000) ccsid 1208 default NULL,
          openai_compatible_basepath varchar(1000) ccsid 1208 default '/v1',
          kafka_protocol varchar(16) ccsid 1208 default 'http',
          kafka_broker varchar(1000) ccsid 1208 default 'localhost',
          kafka_port int default 8082,
          kafka_topic varchar(1000) ccsid 1208 default NULL,
          slack_webhook varchar(1000) ccsid 1208 default NULL,
          twilio_number varchar(1000) ccsid 1208 default NULL,
          twilio_sid varchar(1000) ccsid 1208 default NULL,
          twilio_authtoken varchar(1000) ccsid 1208 default NULL,
          watsonx_iam_baseurl varchar(1000) ccsid 1208 default NULL,
          watsonx_ml_baseurl varchar(1000) ccsid 1208 default NULL,
          twilio_baseurl varchar(1000) ccsid 1208 default NULL,
        PRIMARY KEY(USRPRF)
      )
      on replace preserve rows;

create or replace procedure dbsdk_v1.conf_register_user(usrprf varchar(10) ccsid 1208 default CURRENT_USER) 
  program type sub
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  MERGE INTO dbsdk_v1.conf tt USING (
      SELECT usrprf AS usrprf
        FROM sysibm.sysdummy1
    ) live
    ON tt.usrprf = live.usrprf
    WHEN NOT MATCHED THEN INSERT (usrprf) VALUES (live.usrprf);
end;
create or replace procedure dbsdk_v1.conf_initialize(usrprf varchar(10) ccsid 1208 default CURRENT_USER) 
  program type sub
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  call dbsdk_v1.conf_register_user(usrprf);
end;
create or replace procedure dbsdk_v1.conf_remove_user(usrprftoremove varchar(10) ccsid 1208 default CURRENT_USER) 
  program type sub
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  DELETE FROM dbsdk_v1.conf WHERE usrprf = usrprftoremove; 
end;

