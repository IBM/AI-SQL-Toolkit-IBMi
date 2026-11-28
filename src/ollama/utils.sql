-- ## Ollama utility functions


create or replace function aisqltkt.ollama_getserver(hostname varchar(1000) ccsid 1208 default NULL) 
  returns varchar(1000) ccsid 1208
  modifies sql data
begin
  declare returnval varchar(1000) ccsid 1208;
  call aisqltkt.conf_initialize();
  set returnval = hostname;
  if (returnval is not null) then return returnval;end if;
  set returnval = aisqltkt.ollama_server;
  if (returnval is not null) then return returnval;end if;
  set returnval = (select ollama_server from aisqltkt.conf where USRPRF = CURRENT_USER);
  return returnval;
end;


-- ### procedure: `ollama_setserverforjob`

-- **Description:** sets the ollama server to be used for this job

-- **Input parameters:**
-- - `HOSTNAME` (required): The IP address or hostname of the ollama server.
create or replace procedure aisqltkt.ollama_setserverforjob(hostname varchar(1000) ccsid 1208 default NULL) 
  modifies SQL DATA
begin
  set aisqltkt.ollama_server= hostname;
end;



-- ### procedure: `ollama_setserverforme`

-- **Description:** sets the ollama server to be used for this user profile (persists across jobs)

-- **Input parameters:**
-- - `HOSTNAME` (required): The IP address or hostname of the ollama server.
create or replace procedure aisqltkt.ollama_setserverforme(hostname varchar(1000) ccsid 1208 default NULL)
  MODIFIES SQL DATA
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  MERGE INTO aisqltkt.conf tt USING (
    SELECT CURRENT_USER AS usrprf, hostname AS ollama_server
      FROM sysibm.sysdummy1
  ) live
  ON tt.usrprf = live.usrprf
  WHEN NOT MATCHED THEN INSERT (usrprf, ollama_server) VALUES (live.usrprf,
      live.ollama_server)
  WHEN MATCHED THEN UPDATE SET ollama_server = live.ollama_server;
end;


create or replace function aisqltkt.ollama_getport(port INT default NULL) 
  returns INT
  modifies sql data
begin
  declare returnval varchar(1000) ccsid 1208;
  call aisqltkt.conf_initialize();
  set returnval = port;
  if (returnval is not null) then return returnval;end if;
  set returnval = aisqltkt.ollama_port;
  if (returnval is not null) then return returnval;end if;
  set returnval = (select ollama_port from aisqltkt.conf where USRPRF = CURRENT_USER);
  return returnval;
end;



-- ### procedure: `ollama_setportforjob`

-- **Description:** sets the ollama server port to be used for this job

-- **Input parameters:**
-- - `PORT` (required): The ollama server port.
create or replace procedure aisqltkt.ollama_setportforjob(port INT default NULL) 
  modifies SQL DATA
begin
  set aisqltkt.ollama_port= port;
end;


-- ### procedure: `ollama_setportforme`

-- **Description:** sets the ollama server port to be used for this user profile (persists across jobs).

-- **Input parameters:**
-- - `PORT` (required): The ollama server port.
create or replace procedure aisqltkt.ollama_setportforme(port INT default NULL)
  MODIFIES SQL DATA
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  MERGE INTO aisqltkt.conf tt USING (
    SELECT CURRENT_USER AS usrprf, port AS ollama_port
      FROM sysibm.sysdummy1
  ) live
  ON tt.usrprf = live.usrprf
  WHEN NOT MATCHED THEN INSERT (usrprf, ollama_port) VALUES (live.usrprf,
      live.ollama_port)
  WHEN MATCHED THEN UPDATE SET ollama_port = live.ollama_port;
end;


create or replace function aisqltkt.ollama_getmodel(model varchar(1000) ccsid 1208 default NULL) 
  returns varchar(1000) ccsid 1208
  modifies sql data
begin
  declare returnval varchar(1000) ccsid 1208;
  call aisqltkt.conf_initialize();
  set returnval = model;
  if (returnval is not null) then return returnval;end if;
  set returnval = aisqltkt.ollama_model;
  if (returnval is not null) then return returnval;end if;
  set returnval = (select ollama_model from aisqltkt.conf where USRPRF = CURRENT_USER);
  return returnval;
end;


-- ### procedure: `ollama_setmodelforjob`

-- **Description:** sets the large language model (LLM) to be used for this job

-- **Input parameters:**
-- - `MODEL` (required): The ollama identifier of the model to use.
create or replace procedure aisqltkt.ollama_setmodelforjob(model varchar(1000) ccsid 1208 default NULL) 
  modifies SQL DATA
begin
  set aisqltkt.ollama_model= model;
end;



-- ### procedure: `ollama_setmodelforme`

-- **Description:** sets the large language model (LLM) to be used for this user profile (persists across jobs)

-- **Input parameters:**
-- - `MODEL` (required): The ollama identifier of the model to use.
create or replace procedure aisqltkt.ollama_setmodelforme(model varchar(1000) ccsid 1208 default NULL)
  MODIFIES SQL DATA
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  MERGE INTO aisqltkt.conf tt USING (
    SELECT CURRENT_USER AS usrprf, model AS ollama_model
      FROM sysibm.sysdummy1
  ) live
  ON tt.usrprf = live.usrprf
  WHEN NOT MATCHED THEN INSERT (usrprf, ollama_model) VALUES (live.usrprf,
      live.ollama_model)
  WHEN MATCHED THEN UPDATE SET ollama_model = live.ollama_model;
end;

create or replace function aisqltkt.ollama_getprotocol(protocol varchar(1000) ccsid 1208 default NULL) 
  returns varchar(1000) ccsid 1208
  modifies sql data
begin
  declare returnval varchar(1000) ccsid 1208;
  call aisqltkt.conf_initialize();
  set returnval = protocol;
  if (returnval is not null) then return returnval;end if;
  set returnval = aisqltkt.ollama_protocol;
  if (returnval is not null) then return returnval;end if;
  set returnval = (select ollama_protocol from aisqltkt.conf where USRPRF = CURRENT_USER);
  return returnval;
end;



-- ### procedure: `ollama_setprotocolforjob`

-- **Description:** sets the protocol to be used for this job

-- **Input parameters:**
-- - `PROTOCOL` (required): `http`/`https`
create or replace procedure aisqltkt.ollama_setprotocolforjob(protocol varchar(1000) ccsid 1208 default NULL) 
  modifies SQL DATA
begin
  set aisqltkt.ollama_protocol= protocol;
end;


-- ### procedure: `ollama_setprotocolforjob`
-- 
-- **Description:** sets the protocol to be used for this user profile (persists across jobs)
-- 
-- **Input parameters:**
-- - `PROTOCOL` (required): `http`/`https`
create or replace procedure aisqltkt.ollama_setprotocolforme(protocol varchar(1000) ccsid 1208 default NULL)
  MODIFIES SQL DATA
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  MERGE INTO aisqltkt.conf tt USING (
    SELECT CURRENT_USER AS usrprf, protocol AS ollama_protocol
      FROM sysibm.sysdummy1
  ) live
  ON tt.usrprf = live.usrprf
  WHEN NOT MATCHED THEN INSERT (usrprf, ollama_protocol) VALUES (live.usrprf,
      live.ollama_protocol)
  WHEN MATCHED THEN UPDATE SET ollama_protocol = live.ollama_protocol;
end;