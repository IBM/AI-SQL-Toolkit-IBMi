-- ============================================================================
-- DBSDK Configuration Administration - CONF_UPDATE_OLLAMA Procedure
-- ============================================================================
-- Purpose: Update Ollama AI configuration for a specific user in the
--          AISQLTKT.CONF table. Modifies protocol, server address, port,
--          and model settings.
--
-- Compilation:
--   RUNSQLSTM SRCSTMF('/home/jwoehr/work/AI/AI-SQL-Toolkit/AI-SDK-Db2-IBMi/src/admin/sql/conf_update_ollama.sql') +
--             COMMIT(*NONE) +
--             ERRLVL(21)
--
-- Author: AI-SDK-Db2-IBMi Project
-- Date: 2026-03-22
-- ============================================================================

-- ============================================================================
-- Procedure: CONF_UPDATE_OLLAMA
-- Purpose: Update Ollama configuration for a user
-- Parameters:
--   IN p_usrprf VARCHAR(10) - User profile
--   IN p_protocol VARCHAR(16) - Protocol (http/https)
--   IN p_server VARCHAR(1000) - Server address
--   IN p_port INT - Port number
--   IN p_model VARCHAR(1000) - Model name
-- ============================================================================
CREATE OR REPLACE PROCEDURE AISQLTKT.CONF_UPDATE_OLLAMA(
    IN p_usrprf VARCHAR(10),
    IN p_protocol VARCHAR(16),
    IN p_server VARCHAR(1000),
    IN p_port INT,
    IN p_model VARCHAR(1000)
)
LANGUAGE SQL
SPECIFIC CONF_UPD_OL
NOT DETERMINISTIC
MODIFIES SQL DATA
CALLED ON NULL INPUT
SET OPTION USRPRF = *OWNER, DYNUSRPRF = *OWNER, COMMIT = *NONE
BEGIN
    UPDATE AISQLTKT.CONF
    SET ollama_protocol = p_protocol,
        ollama_server = p_server,
        ollama_port = p_port,
        ollama_model = p_model
    WHERE USRPRF = p_usrprf;
END;

-- Made with Bob