-- ============================================================================
-- Grant a test user profile the authority the test harness needs on DBSDK_V1
-- ============================================================================
-- Run this once as the DBSDK_V1 schema owner (or a profile with *SECADM),
-- and again after rebuilding the toolkit: CREATE OR REPLACE resets authority.
--
--   RUNSQLSTM SRCSTMF('test-harness/sql/grant_test_user.sql') COMMIT(*NONE) NAMING(*SQL)
--
-- The profile is DBSDKTEST; change test_user below to use another one.
--
-- What is granted, and why:
-- - SELECT, INSERT, UPDATE, DELETE on DBSDK_V1.CONF: the CONF tests read the
--   table directly, and conf_register_user, the *forme setters and the tests'
--   save/restore of the user's row write it. The toolkit's row access control
--   (src/conf_rcac.sql) still limits every operation to the user's own row.
-- - EXECUTE on every function and procedure in DBSDK_V1, found from the
--   catalog, so the list cannot fall behind the toolkit.
-- - READ and WRITE on every global variable in DBSDK_V1: some tests set job
--   settings (e.g. dbsdk_v1.wx_region) with SET, as the documentation does.
-- ============================================================================

begin
  declare test_user varchar(10) default 'DBSDKTEST';

  execute immediate 'grant select, insert, update, delete on table dbsdk_v1.conf to ' concat test_user;

  for r as
    select specific_name, routine_type
      from qsys2.sysroutines
      where routine_schema = 'DBSDK_V1'
  do
    execute immediate 'grant execute on specific ' concat r.routine_type concat ' dbsdk_v1.' concat r.specific_name concat ' to ' concat test_user;
  end for;

  for v as
    select variable_name
      from qsys2.sysvariables
      where variable_schema = 'DBSDK_V1'
  do
    execute immediate 'grant read, write on variable dbsdk_v1.' concat v.variable_name concat ' to ' concat test_user;
  end for;
end;
