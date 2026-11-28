--variables for watsonx
create or replace variable aisqltkt.wx_region              varchar(16) ccsid 1208 default NULL;
create or replace variable aisqltkt.wx_apiVersion          varchar(10) ccsid 1208 default NULL;
create or replace variable aisqltkt.wx_apikey              varchar(100) ccsid 1208 default NULL;
create or replace variable aisqltkt.wx_projectid           varchar(100) ccsid 1208 default NULL;
create or replace variable aisqltkt.wx_JobBearerToken      varchar(10000) ccsid 1208 default null;
create or replace variable aisqltkt.wx_JobTokenExpires     timestamp;
create or replace variable aisqltkt.wx_iam_baseurl         varchar(1000) ccsid 1208 default NULL;
create or replace variable aisqltkt.wx_ml_baseurl          varchar(1000) ccsid 1208 default NULL;

-- variables for ollama
create or replace variable aisqltkt.ollama_protocol     varchar(16)   ccsid 1208 default null;
create or replace variable aisqltkt.ollama_server       varchar(1000) ccsid 1208 default null;
create or replace variable aisqltkt.ollama_port         INT                      default NULL;
create or replace variable aisqltkt.ollama_model        varchar(1000) ccsid 1208 default NULL;

-- variables for openai compatible
create or replace variable aisqltkt.openai_compatible_protocol     varchar(16)   ccsid 1208 default null;
create or replace variable aisqltkt.openai_compatible_server       varchar(1000) ccsid 1208 default null;
create or replace variable aisqltkt.openai_compatible_port         INT                      default NULL;
create or replace variable aisqltkt.openai_compatible_model        varchar(1000) ccsid 1208 default NULL;
create or replace variable aisqltkt.openai_compatible_apikey       varchar(8000) ccsid 1208 default NULL;
create or replace variable aisqltkt.openai_compatible_basepath     varchar(1000) ccsid 1208 default NULL;

-- variables for kafka
create or replace variable aisqltkt.kafka_protocol     varchar(16)   ccsid 1208 default null;
create or replace variable aisqltkt.kafka_broker       varchar(1000) ccsid 1208 default null;
create or replace variable aisqltkt.kafka_port         INT                      default NULL;
create or replace variable aisqltkt.kafka_topic        varchar(1000) ccsid 1208 default NULL;

-- variables for Slack
create or replace variable aisqltkt.slack_webhook      varchar(1000) ccsid 1208 default NULL;

-- variables for Twilio
create or replace variable aisqltkt.twilio_number      varchar(1000) ccsid 1208 default NULL;
create or replace variable aisqltkt.twilio_sid         varchar(1000) ccsid 1208 default NULL;
create or replace variable aisqltkt.twilio_authtoken   varchar(1000) ccsid 1208 default NULL;
create or replace variable aisqltkt.twilio_baseurl     varchar(1000) ccsid 1208 default NULL;

create or replace table aisqltkt.conf
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

create or replace procedure aisqltkt.conf_register_user(usrprf varchar(10) ccsid 1208 default CURRENT_USER) 
  program type sub
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  MERGE INTO aisqltkt.conf tt USING (
      SELECT usrprf AS usrprf
        FROM sysibm.sysdummy1
    ) live
    ON tt.usrprf = live.usrprf
    WHEN NOT MATCHED THEN INSERT (usrprf) VALUES (live.usrprf);
end;
create or replace procedure aisqltkt.conf_initialize(usrprf varchar(10) ccsid 1208 default CURRENT_USER) 
  program type sub
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  call aisqltkt.conf_register_user(usrprf);
end;
create or replace procedure aisqltkt.conf_remove_user(usrprftoremove varchar(10) ccsid 1208 default CURRENT_USER) 
  program type sub
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  DELETE FROM aisqltkt.conf WHERE usrprf = usrprftoremove; 
end;

