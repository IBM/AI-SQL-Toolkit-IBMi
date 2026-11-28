-- ============================================================================
-- DBSDK Configuration Administration - CONF_UPDATE_TWILIO Procedure
-- ============================================================================
-- Purpose: Update Twilio SMS configuration for a specific user in the
--          AISQLTKT.CONF table. Modifies phone number, account SID,
--          and authentication token settings.
--
-- Compilation:
--   RUNSQLSTM SRCSTMF('/home/jwoehr/work/AI/AI-SQL-Toolkit/AI-SDK-Db2-IBMi/src/admin/sql/conf_update_twilio.sql') +
--             COMMIT(*NONE) +
--             ERRLVL(21)
--
-- Author: AI-SDK-Db2-IBMi Project
-- Date: 2026-03-22
-- ============================================================================

-- ============================================================================
-- Procedure: CONF_UPDATE_TWILIO
-- Purpose: Update Twilio configuration for a user
-- Parameters:
--   IN p_usrprf VARCHAR(10) - User profile
--   IN p_number VARCHAR(1000) - Phone number
--   IN p_sid VARCHAR(1000) - Account SID
--   IN p_authtoken VARCHAR(1000) - Auth token
-- ============================================================================
CREATE OR REPLACE PROCEDURE AISQLTKT.CONF_UPDATE_TWILIO(
    IN p_usrprf VARCHAR(10),
    IN p_number VARCHAR(1000),
    IN p_sid VARCHAR(1000),
    IN p_authtoken VARCHAR(1000)
)
LANGUAGE SQL
SPECIFIC CONF_UPD_TW
NOT DETERMINISTIC
MODIFIES SQL DATA
CALLED ON NULL INPUT
SET OPTION USRPRF = *OWNER, DYNUSRPRF = *OWNER, COMMIT = *NONE
BEGIN
    UPDATE AISQLTKT.CONF
    SET twilio_number = p_number,
        twilio_sid = p_sid,
        twilio_authtoken = p_authtoken
    WHERE USRPRF = p_usrprf;
END;

-- Made with Bob