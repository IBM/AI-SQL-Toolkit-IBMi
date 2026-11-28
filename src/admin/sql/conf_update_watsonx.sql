-- ============================================================================
-- DBSDK Configuration Administration - CONF_UPDATE_WATSONX Procedure
-- ============================================================================
-- Purpose: Update WatsonX AI configuration for a specific user in the
--          AISQLTKT.CONF table. Modifies region, API version, API key,
--          and project ID settings.
--
-- Compilation:
--   RUNSQLSTM SRCSTMF('/home/jwoehr/work/AI/AI-SQL-Toolkit/AI-SDK-Db2-IBMi/src/admin/sql/conf_update_watsonx.sql') +
--             COMMIT(*NONE) +
--             ERRLVL(21)
--
-- Author: AI-SDK-Db2-IBMi Project
-- Date: 2026-03-22
-- ============================================================================

-- ============================================================================
-- Procedure: CONF_UPDATE_WATSONX
-- Purpose: Update WatsonX configuration for a user
-- Parameters:
--   IN p_usrprf VARCHAR(10) - User profile
--   IN p_region VARCHAR(16) - WatsonX region
--   IN p_apiversion VARCHAR(10) - API version
--   IN p_apikey VARCHAR(100) - API key
--   IN p_projectid VARCHAR(100) - Project ID
-- ============================================================================
CREATE OR REPLACE PROCEDURE AISQLTKT.CONF_UPDATE_WATSONX(
    IN p_usrprf VARCHAR(10),
    IN p_region VARCHAR(16),
    IN p_apiversion VARCHAR(10),
    IN p_apikey VARCHAR(100),
    IN p_projectid VARCHAR(100)
)
LANGUAGE SQL
SPECIFIC CONF_UPD_WX
NOT DETERMINISTIC
MODIFIES SQL DATA
CALLED ON NULL INPUT
SET OPTION USRPRF = *OWNER, DYNUSRPRF = *OWNER, COMMIT = *NONE
BEGIN
    UPDATE AISQLTKT.CONF
    SET watsonx_region = p_region,
        watsonx_apiVersion = p_apiversion,
        watsonx_apikey = p_apikey,
        watsonx_projectid = p_projectid
    WHERE USRPRF = p_usrprf;
END;

-- Made with Bob