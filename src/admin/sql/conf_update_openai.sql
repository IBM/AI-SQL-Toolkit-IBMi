-- ============================================================================
-- DBSDK Configuration Administration - CONF_UPDATE_OPENAI Procedure
-- ============================================================================
-- Purpose: Update OpenAI Compatible API configuration for a specific user in
--          the AISQLTKT.CONF table. Modifies protocol, server address, port,
--          model, API key, and base path settings.
--
-- Compilation:
--   RUNSQLSTM SRCSTMF('/home/jwoehr/work/AI/AI-SQL-Toolkit/AI-SDK-Db2-IBMi/src/admin/sql/conf_update_openai.sql') +
--             COMMIT(*NONE) +
--             ERRLVL(21)
--
-- Author: AI-SDK-Db2-IBMi Project
-- Date: 2026-03-22
-- ============================================================================

-- ============================================================================
-- Procedure: CONF_UPDATE_OPENAI
-- Purpose: Update OpenAI Compatible configuration for a user
-- Parameters:
--   IN p_usrprf VARCHAR(10) - User profile
--   IN p_protocol VARCHAR(16) - Protocol
--   IN p_server VARCHAR(1000) - Server address
--   IN p_port INT - Port number
--   IN p_model VARCHAR(1000) - Model name
--   IN p_apikey VARCHAR(8000) - API key
--   IN p_basepath VARCHAR(1000) - Base path
-- ============================================================================
CREATE OR REPLACE PROCEDURE AISQLTKT.CONF_UPDATE_OPENAI(
    IN p_usrprf VARCHAR(10),
    IN p_protocol VARCHAR(16),
    IN p_server VARCHAR(1000),
    IN p_port INT,
    IN p_model VARCHAR(1000),
    IN p_apikey VARCHAR(8000),
    IN p_basepath VARCHAR(1000)
)
LANGUAGE SQL
SPECIFIC CONF_UPD_OA
NOT DETERMINISTIC
MODIFIES SQL DATA
CALLED ON NULL INPUT
SET OPTION USRPRF = *OWNER, DYNUSRPRF = *OWNER, COMMIT = *NONE
BEGIN
    UPDATE AISQLTKT.CONF
    SET openai_compatible_protocol = p_protocol,
        openai_compatible_server = p_server,
        openai_compatible_port = p_port,
        openai_compatible_model = p_model,
        openai_compatible_apikey = p_apikey,
        openai_compatible_basepath = p_basepath
    WHERE USRPRF = p_usrprf;
END;

-- Made with Bob