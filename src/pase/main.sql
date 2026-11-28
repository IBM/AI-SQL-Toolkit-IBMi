-- ## PASE main functions
-- 
-- #### **Function:** `pase_call`
-- 
-- **Description:** Calls a PASE program
-- 
-- **Input parameters:**
-- - `FULLCMD` (required): The PASE command to run.
-- 
-- **Return type:** 
-- - Result set with the following columns:
--    - `output`: A line of output from the PASE command's standard output stream.
-- 
-- **Return value:**
-- - The PASE command's standard output stream. The command's standard error stream is ignored.
create or replace function aisqltkt.pase_call(fullcmd varchar(1000) ccsid 1208) 
  RETURNS TABLE(output VARCHAR(32000) ccsid 1208 )
  modifies sql data
  not deterministic
  no external action
  set option usrprf = *user, dynusrprf = *user, commit = *none
begin
  declare cmdoutxml varchar(32500) ccsid 1208 default '';
  declare cmdout varchar(32400) ccsid 1208 default '';
  call qxmlserv.iPlug512k('*na', '*here *cdata', 
  '<script><sh><![CDATA[' concat fullcmd concat ']]></sh></script>', cmdoutxml);
  set cmdout = (Select T1.outp FROM Xmltable('$d' 
        PASSING XMLPARSE(DOCUMENT cmdoutxml) as "d"
        COLUMNS outp varchar(32400) ccsid 1208  Path 'script/sh' ) as T1);
  return select element from TABLE(SYSTOOLS.SPLIT(rtrim(ltrim(cmdout,'
'),'
'),'
')) order by ordinal_position ;
end;