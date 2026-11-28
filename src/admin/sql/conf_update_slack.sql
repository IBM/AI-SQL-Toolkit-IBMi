-- ============================================================================
-- DBSDK Configuration Administration - CONF_UPDATE_SLACK Procedure
-- ============================================================================
-- Purpose: Update Slack messaging configuration for a specific user in the
--          AISQLTKT.CONF table. Modifies webhook URL settings for Slack
--          integration.
--
-- Compilation:
--   RUNSQLSTM SRCSTMF('/home/jwoehr/work/AI/AI-SQL-Toolkit/AI-SDK-Db2-IBMi/src/admin/sql/conf_update_slack.sql') +
--             COMMIT(*NONE) +
--             ERRLVL(21)
--
-- Author: AI-SDK-Db2-IBMi Project
-- Date: 2026-03-22
-- ============================================================================

-- ============================================================================
-- Procedure: CONF_UPDATE_SLACK
-- Purpose: Update Slack configuration for a user
-- Parameters:
--   IN p_usrprf VARCHAR(10) - User profile
--   IN p_webhook VARCHAR(1000) - Webhook URL
-- ============================================================================
CREATE OR REPLACE PROCEDURE AISQLTKT.CONF_UPDATE_SLACK(
    IN p_usrprf VARCHAR(10),
    IN p_webhook VARCHAR(1000)
)
LANGUAGE SQL
SPECIFIC CONF_UPD_SL
NOT DETERMINISTIC
MODIFIES SQL DATA
CALLED ON NULL INPUT
SET OPTION USRPRF = *OWNER, DYNUSRPRF = *OWNER, COMMIT = *NONE
BEGIN
    UPDATE AISQLTKT.CONF
    SET slack_webhook = p_webhook
    WHERE USRPRF = p_usrprf;
END;

-- Made with Bob