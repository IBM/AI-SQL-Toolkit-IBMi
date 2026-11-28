-- ============================================================================
-- DBSDK Configuration Administration - CONF_GET_USER Procedure
-- ============================================================================
-- Purpose: Retrieve complete configuration for a specific user from the
--          AISQLTKT.CONF table. Returns all configuration fields for the
--          specified user profile.
--
-- Compilation:
--   RUNSQLSTM SRCSTMF('/home/jwoehr/work/AI/AI-SQL-Toolkit/AI-SDK-Db2-IBMi/src/admin/sql/conf_get_user.sql') +
--             COMMIT(*NONE) +
--             ERRLVL(21)
--
-- Author: AI-SDK-Db2-IBMi Project
-- Date: 2026-03-22
-- ============================================================================

-- ============================================================================
-- Procedure: CONF_GET_USER
-- Purpose: Retrieve complete configuration for a specific user
-- Parameters:
--   IN p_usrprf VARCHAR(10) - User profile to retrieve
-- Returns: Result set with all configuration fields
-- ============================================================================
CREATE OR REPLACE PROCEDURE AISQLTKT.CONF_GET_USER(
    IN p_usrprf VARCHAR(10)
)
LANGUAGE SQL
SPECIFIC CONF_GET_USER
NOT DETERMINISTIC
READS SQL DATA
CALLED ON NULL INPUT
DYNAMIC RESULT SETS 1
SET OPTION USRPRF = *OWNER, DYNUSRPRF = *OWNER, COMMIT = *NONE
BEGIN
    DECLARE USER_CFG_CSR CURSOR WITH RETURN FOR
        SELECT
            USRPRF,
            watsonx_region,
            watsonx_apiVersion,
            watsonx_apikey,
            watsonx_projectid,
            ollama_protocol,
            ollama_server,
            ollama_port,
            ollama_model,
            openai_compatible_protocol,
            openai_compatible_server,
            openai_compatible_port,
            openai_compatible_model,
            openai_compatible_apikey,
            openai_compatible_basepath,
            kafka_protocol,
            kafka_broker,
            kafka_port,
            kafka_topic,
            slack_webhook,
            twilio_number,
            twilio_sid,
            twilio_authtoken
        FROM AISQLTKT.CONF
        WHERE USRPRF = p_usrprf;
    
    OPEN USER_CFG_CSR;
END;

-- Made with Bob