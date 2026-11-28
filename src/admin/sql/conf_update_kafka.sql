-- ============================================================================
-- DBSDK Configuration Administration - CONF_UPDATE_KAFKA Procedure
-- ============================================================================
-- Purpose: Update Kafka messaging configuration for a specific user in the
--          AISQLTKT.CONF table. Modifies protocol, broker address, port,
--          and topic settings.
--
-- Compilation:
--   RUNSQLSTM SRCSTMF('/home/jwoehr/work/AI/AI-SQL-Toolkit/AI-SDK-Db2-IBMi/src/admin/sql/conf_update_kafka.sql') +
--             COMMIT(*NONE) +
--             ERRLVL(21)
--
-- Author: AI-SDK-Db2-IBMi Project
-- Date: 2026-03-22
-- ============================================================================

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

-- Made with Bob