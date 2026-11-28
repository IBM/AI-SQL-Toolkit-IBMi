-- ## Slack utility functions


create or replace function aisqltkt.slack_getwebhook(webhook varchar(1000) ccsid 1208 default NULL) 
  returns varchar(1000) ccsid 1208
  modifies sql data
begin
  declare returnval varchar(1000) ccsid 1208;
  call aisqltkt.conf_initialize();
  set returnval = webhook;
  if (returnval is not null) then return returnval;end if;
  set returnval = aisqltkt.slack_webhook;
  if (returnval is not null) then return returnval;end if;
  set returnval = (select slack_webhook from aisqltkt.conf where USRPRF = CURRENT_USER);
  return returnval;
end;

-- ### procedure: `slack_setwebhookforjob`

-- **Description:** sets the Slack webhook to be used for this job

-- **Input parameters:**
-- - `webhook` (required): the Slack webhook URL.
create or replace procedure aisqltkt.slack_setwebhookforjob(webhook varchar(1000) ccsid 1208 default NULL) 
  modifies SQL DATA
begin
  set aisqltkt.slack_webhook= webhook;
end;

-- ### procedure: `slack_setwebhookforme`

-- **Description:** sets the Slack webhook to be used for the current user profile (persists across jobs).

-- **Input parameters:**
-- - `webhook` (required): the Slack webhook URL.
create or replace procedure aisqltkt.slack_setwebhookforme(webhook varchar(1000) ccsid 1208 default NULL) 
  MODIFIES SQL DATA
begin
  MERGE INTO aisqltkt.conf tt USING (
    SELECT CURRENT_USER AS usrprf, webhook AS slack_webhook
      FROM sysibm.sysdummy1
  ) live
  ON tt.usrprf = live.usrprf
  WHEN NOT MATCHED THEN INSERT (usrprf, slack_webhook) VALUES (live.usrprf,
      live.slack_webhook)
  WHEN MATCHED THEN UPDATE SET slack_webhook = live.slack_webhook;
end;