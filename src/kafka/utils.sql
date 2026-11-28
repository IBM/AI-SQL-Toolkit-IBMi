
-- ## Kafka utility functions

create or replace function aisqltkt.kafka_getbroker(hostname varchar(1000) ccsid 1208 default NULL) 
  returns varchar(1000) ccsid 1208
  modifies sql data
begin
  declare returnval varchar(1000) ccsid 1208;
  call aisqltkt.conf_initialize();
  set returnval = hostname;
  if (returnval is not null) then return returnval;end if;
  set returnval = aisqltkt.kafka_broker;
  if (returnval is not null) then return returnval;end if;
  set returnval = (select kafka_broker from aisqltkt.conf where USRPRF = CURRENT_USER);
  return returnval;
end;

-- ### procedure: `kafka_setbrokerforjob`

-- **Description:** sets the Kafka broker to be used for this job

-- **Input parameters:**
-- - `HOSTNAME` (required): The broker hostname or IP address.
create or replace procedure aisqltkt.kafka_setbrokerforjob(hostname varchar(1000) ccsid 1208 default NULL) 
  modifies SQL DATA
begin
  set aisqltkt.kafka_broker= hostname;
end;

-- ### procedure: `kafka_setbrokerforjob`

-- **Description:** sets the Kafka broker to be used persistently for the current user profile.

-- **Input parameters:**
-- - `HOSTNAME` (required): The broker hostname or IP address.
create or replace procedure aisqltkt.kafka_setbrokerforme(hostname varchar(1000) ccsid 1208 default NULL) 
  MODIFIES SQL DATA
begin
  MERGE INTO aisqltkt.conf tt USING (
    SELECT CURRENT_USER AS usrprf, hostname AS kafka_broker
      FROM sysibm.sysdummy1
  ) live
  ON tt.usrprf = live.usrprf
  WHEN NOT MATCHED THEN INSERT (usrprf, kafka_broker) VALUES (live.usrprf,
      live.kafka_broker)
  WHEN MATCHED THEN UPDATE SET kafka_broker = live.kafka_broker;
end;

create or replace function aisqltkt.kafka_getport(port INT default NULL) 
  returns INT
  modifies sql data
begin
  declare returnval varchar(1000) ccsid 1208;
  call aisqltkt.conf_initialize();
  set returnval = port;
  if (returnval is not null) then return returnval;end if;
  set returnval = aisqltkt.kafka_port;
  if (returnval is not null) then return returnval;end if;
  set returnval = (select kafka_port from aisqltkt.conf where USRPRF = CURRENT_USER);
  return returnval;
end;

-- ### procedure: `kafka_setportforjob`

-- **Description:** sets the Kafka broker's port to be used for this job

-- **Input parameters:**
-- - `PORT` (required): The port number.
create or replace procedure aisqltkt.kafka_setportforjob(port INT default NULL) 
  modifies SQL DATA
begin
  set aisqltkt.kafka_port= port;
end;


-- ### procedure: `kafka_setportforme`

-- **Description:** sets the Kafka broker's port to be used persistently for the current user profile

-- **Input parameters:**
-- - `PORT` (required): The port number.
create or replace procedure aisqltkt.kafka_setportforme(port INT default NULL) 
  MODIFIES SQL DATA
begin
  MERGE INTO aisqltkt.conf tt USING (
    SELECT CURRENT_USER AS usrprf, port AS kafka_port
      FROM sysibm.sysdummy1
  ) live
  ON tt.usrprf = live.usrprf
  WHEN NOT MATCHED THEN INSERT (usrprf, kafka_port) VALUES (live.usrprf,
      live.kafka_port)
  WHEN MATCHED THEN UPDATE SET kafka_port = live.kafka_port;
end;


create or replace function aisqltkt.kafka_gettopic(topic varchar(1000) ccsid 1208 default NULL) 
  returns varchar(1000) ccsid 1208
  modifies sql data
begin
  declare returnval varchar(1000) ccsid 1208;
  set returnval = topic;
  if (returnval is not null) then return returnval;end if;
  call aisqltkt.conf_register_user();
  set returnval = aisqltkt.kafka_topic;
  if (returnval is not null) then return returnval;end if;
  set returnval = (select kafka_topic from aisqltkt.conf where USRPRF = CURRENT_USER);
  return returnval;
end;


-- ### procedure: `kafka_settopicforjob`

-- **Description:** sets the Kafka topic to be used for this job

-- **Input parameters:**
-- - `TOPIC` (required): The topic name.
create or replace procedure aisqltkt.kafka_settopicforjob(topic varchar(1000) ccsid 1208 default NULL) 
  modifies SQL DATA
begin
  set aisqltkt.kafka_topic= topic;
end;

-- ### procedure: `kafka_settopicforme`

-- **Description:** sets the Kafka topic to be persistently used for the current user profile

-- **Input parameters:**
-- - `TOPIC` (required): The topic name.
create or replace procedure aisqltkt.kafka_settopicforme(topic varchar(1000) ccsid 1208 default NULL) 
  MODIFIES SQL DATA
begin
  MERGE INTO aisqltkt.conf tt USING (
    SELECT CURRENT_USER AS usrprf, topic AS kafka_topic
      FROM sysibm.sysdummy1
  ) live
  ON tt.usrprf = live.usrprf
  WHEN NOT MATCHED THEN INSERT (usrprf, kafka_topic) VALUES (live.usrprf,
      live.kafka_topic)
  WHEN MATCHED THEN UPDATE SET kafka_topic = live.kafka_topic;
end;

create or replace function aisqltkt.kafka_getprotocol(protocol varchar(1000) ccsid 1208 default NULL) 
  returns varchar(1000) ccsid 1208
  modifies sql data
begin
  declare returnval varchar(1000) ccsid 1208;
  call aisqltkt.conf_initialize();
  set returnval = protocol;
  if (returnval is not null) then return returnval;end if;
  set returnval = aisqltkt.kafka_protocol;
  if (returnval is not null) then return returnval;end if;
  set returnval = (select kafka_protocol from aisqltkt.conf where USRPRF = CURRENT_USER);
  return returnval;
end;

-- ### procedure: `kafka_setprotocolforjob`

-- **Description:** sets the Kafka protocol to be used for this job

-- **Input parameters:**
-- - `PROTOCOL` (required): `http` or `https`.
create or replace procedure aisqltkt.kafka_setprotocolforjob(protocol varchar(1000) ccsid 1208 default NULL) 
  modifies SQL DATA
begin
  set aisqltkt.kafka_protocol= protocol;
end;

-- ### procedure: `kafka_setprotocolforjob`

-- **Description:** sets the Kafka protocol to be used persistently for this user profile.

-- **Input parameters:**
-- - `PROTOCOL` (required): `http` or `https`.
create or replace procedure aisqltkt.kafka_setprotocolforme(protocol varchar(1000) ccsid 1208 default NULL) 
  MODIFIES SQL DATA
begin
  MERGE INTO aisqltkt.conf tt USING (
    SELECT CURRENT_USER AS usrprf, protocol AS kafka_protocol
      FROM sysibm.sysdummy1
  ) live
  ON tt.usrprf = live.usrprf
  WHEN NOT MATCHED THEN INSERT (usrprf, kafka_protocol) VALUES (live.usrprf,
      live.kafka_protocol)
  WHEN MATCHED THEN UPDATE SET kafka_protocol = live.kafka_protocol;
end;