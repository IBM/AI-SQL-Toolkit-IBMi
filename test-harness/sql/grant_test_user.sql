-- ============================================================================
-- Grant a test user profile the authority the test harness needs on AISQLTKT
-- ============================================================================
-- Run this once as the AISQLTKT schema owner (or a profile with *SECADM),
-- and again after rebuilding the toolkit: CREATE OR REPLACE resets authority.
--
--   RUNSQLSTM SRCSTMF('test-harness/sql/grant_test_user.sql') COMMIT(*NONE) NAMING(*SQL)
--
-- The profile is DBSDKTEST; change test_user below to use another one.
--
-- What is granted, and why:
-- - SELECT, INSERT, UPDATE, DELETE on AISQLTKT.CONF: the CONF tests read the
--   table directly, and conf_register_user, the *forme setters and the tests'
--   save/restore of the user's row write it. The toolkit's row access control
--   (src/conf_rcac.sql) still limits every operation to the user's own row.
-- - EXECUTE on every function and procedure in AISQLTKT, found from the
--   catalog, so the list cannot fall behind the toolkit.
-- - READ and WRITE on every global variable in AISQLTKT: some tests set job
--   settings (e.g. aisqltkt.wx_region) with SET, as the documentation does.
-- ============================================================================

begin
  declare test_user varchar(10) default 'DBSDKTEST';

  execute immediate 'grant select, insert, update, delete on table aisqltkt.conf to ' concat test_user;

  for r as
    select specific_name, routine_type
      from qsys2.sysroutines
      where routine_schema = 'AISQLTKT'
  do
    execute immediate 'grant execute on specific ' concat r.routine_type concat ' aisqltkt.' concat r.specific_name concat ' to ' concat test_user;
  end for;

  for v as
    select variable_name
      from qsys2.sysvariables
      where variable_schema = 'AISQLTKT'
  do
    execute immediate 'grant read, write on variable aisqltkt.' concat v.variable_name concat ' to ' concat test_user;
  end for;
end;
