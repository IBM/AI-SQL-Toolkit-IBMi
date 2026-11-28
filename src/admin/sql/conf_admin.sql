-- ============================================================================
-- DBSDK Configuration Administration - SQL Procedures
-- ============================================================================
-- Purpose: Administrative SQL procedures for managing user configurations
--          in the AISQLTKT.CONF table. These procedures provide a clean
--          interface for RPGLE programs to interact with the configuration
--          database.
--
-- Compilation:
--   RUNSQLSTM SRCSTMF('/home/jwoehr/work/AI/AI-SQL-Toolkit/AI-SDK-Db2-IBMi/src/admin/sql/conf_admin.sql') +
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

-- ============================================================================
-- Procedure: CONF_LIST_USERS
-- Purpose: List all users in the configuration table
-- Returns: Result set with user profiles
-- ============================================================================
CREATE OR REPLACE PROCEDURE AISQLTKT.CONF_LIST_USERS()
LANGUAGE SQL
SPECIFIC CONF_LIST_USERS
NOT DETERMINISTIC
READS SQL DATA
CALLED ON NULL INPUT
DYNAMIC RESULT SETS 1
SET OPTION USRPRF = *OWNER, DYNUSRPRF = *OWNER, COMMIT = *NONE
BEGIN
    DECLARE LIST_USER_CSR CURSOR WITH RETURN FOR
        SELECT USRPRF
        FROM AISQLTKT.CONF
        ORDER BY USRPRF;
    
    OPEN LIST_USER_CSR;
END;

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

-- ============================================================================
-- Procedure: CONF_UPDATE_KAFKA
-- Purpose: Update Kafka configuration for a user
-- Parameters:
--   IN p_usrprf VARCHAR(10) - User profile
--   IN p_protocol VARCHAR(16) - Protocol
--   IN p_broker VARCHAR(1000) - Broker address
--   IN p_port INT - Port number
--   IN p_topic VARCHAR(1000) - Topic name
-- ============================================================================
CREATE OR REPLACE PROCEDURE AISQLTKT.CONF_UPDATE_KAFKA(
    IN p_usrprf VARCHAR(10),
    IN p_protocol VARCHAR(16),
    IN p_broker VARCHAR(1000),
    IN p_port INT,
    IN p_topic VARCHAR(1000)
)
LANGUAGE SQL
SPECIFIC CONF_UPD_KF
NOT DETERMINISTIC
MODIFIES SQL DATA
CALLED ON NULL INPUT
SET OPTION USRPRF = *OWNER, DYNUSRPRF = *OWNER, COMMIT = *NONE
BEGIN
    UPDATE AISQLTKT.CONF
    SET kafka_protocol = p_protocol,
        kafka_broker = p_broker,
        kafka_port = p_port,
        kafka_topic = p_topic
    WHERE USRPRF = p_usrprf;
END;

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

-- ============================================================================
-- End of SQL Procedures
-- ============================================================================

-- Made with Bob
