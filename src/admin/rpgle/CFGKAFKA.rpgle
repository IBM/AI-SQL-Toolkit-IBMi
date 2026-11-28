**FREE
//==============================================================================
// DBSDK Configuration Administration - Kafka Configuration Program
//==============================================================================
// Purpose: Kafka configuration screen for editing user settings
//
// Compilation:
//   CRTBNDRPG PGM(AISQLTKT/CFGKAFKA) +
//             SRCSTMF('/home/jwoehr/work/AI/AI-SQL-Toolkit/AI-SDK-Db2-IBMi/src/admin/rpgle/CFGKAFKA.rpgle') +
//             DFTACTGRP(*NO) +
//             ACTGRP(*NEW) +
//             DBGVIEW(*SOURCE) +
//             OPTION(*EVENTF) +
//             USRPRF(*OWNER)
//
// Author: AI-SDK-Db2-IBMi Project
// Date: 2026-03-22
//==============================================================================

Ctl-Opt DftActGrp(*No) ActGrp(*New) Option(*SrcStmt:*NoDebugIO);

// Display file
Dcl-F CFGKAFKAD WorkStn IndDs(Indicators);

// Indicator data structure
Dcl-Ds Indicators;
  Exit Ind Pos(3);
  Cancel Ind Pos(12);
End-Ds;

// Screen fields

// Program variables
Dcl-S OldUsrPrf Varchar(10) Inz(*Blanks);
Dcl-S FullBroker Varchar(1000);
Dcl-S FullTopic Varchar(1000);

//==============================================================================
// Main Processing
//==============================================================================

Dow Not Exit;
  // If no user profile, prompt for it
  If UsrPrf = *Blanks;
    ErrMsg = 'Enter user profile to configure';
  EndIf;
  
  // Display screen
  Exfmt KFCONFIG;
  
  // Check for exit or cancel
  If Exit Or Cancel;
    Leave;
  EndIf;
  
  If UsrPrf = *Blanks;
    Iter;
  EndIf;
  
  // Clear error message if we have a user profile
  If ErrMsg = 'Enter user profile to configure';
    ErrMsg = *Blanks;
  EndIf;
  
  // If user profile changed
  If UsrPrf <> OldUsrPrf;
    // Verify user exists in configuration table
    Exec SQL
      SELECT USRPRF
      INTO :UsrPrf
      FROM AISQLTKT.CONF
      WHERE USRPRF = :UsrPrf;
    
    If SQLCODE <> 0;
      ErrMsg = 'User not found. Add user first.';
      UsrPrf = *Blanks;
      Iter;
    EndIf;
    
    // Check if they entered configuration data along with the new profile
    If KfBroker <> *Blanks Or KfTopic <> *Blanks Or KfProtoc <> *Blanks;
      // They entered data, save it
      SaveConfiguration();
    EndIf;
    
    OldUsrPrf = UsrPrf;
    LoadConfiguration();
  Else;
    // Save configuration
    SaveConfiguration();
    LoadConfiguration();
  EndIf;
EndDo;

*InLR = *On;
Return;

//==============================================================================
// Load Current Configuration
//==============================================================================
Dcl-Proc LoadConfiguration;
  // Clear messages
  ErrMsg = *Blanks;
  StsMsg = *Blanks;
  
  // Load configuration from database
  Exec SQL
    SELECT IFNULL(kafka_protocol, ''),
           IFNULL(kafka_broker, ''),
           IFNULL(kafka_port, 0),
           IFNULL(kafka_topic, '')
    INTO :KfProtoc,
         :FullBroker,
         :KfPort,
         :FullTopic
    FROM AISQLTKT.CONF
    WHERE USRPRF = :UsrPrf;
  
  If SQLCODE = 0;
    // Split broker into two 50-char fields
    If %Len(FullBroker) > 50;
      KfBroker = %Subst(FullBroker:1:50);
      KfBrokr2 = %Subst(FullBroker:51:50);
    ElseIf %Len(FullBroker) > 0;
      KfBroker = FullBroker;
      KfBrokr2 = *Blanks;
    Else;
      KfBroker = *Blanks;
      KfBrokr2 = *Blanks;
    EndIf;
    
    // Split topic into two 50-char fields
    If %Len(FullTopic) > 50;
      KfTopic = %Subst(FullTopic:1:50);
      KfTopic2 = %Subst(FullTopic:51:50);
    ElseIf %Len(FullTopic) > 0;
      KfTopic = FullTopic;
      KfTopic2 = *Blanks;
    Else;
      KfTopic = *Blanks;
      KfTopic2 = *Blanks;
    EndIf;
  Else;
    ErrMsg = 'Error loading configuration: ' + %Char(SQLCODE);
  EndIf;
End-Proc;

//==============================================================================
// Save Configuration
//==============================================================================
Dcl-Proc SaveConfiguration;
  // Combine split fields
  FullBroker = %Trim(KfBroker) + %Trim(KfBrokr2);
  FullTopic = %Trim(KfTopic) + %Trim(KfTopic2);
  
  // Call SQL procedure to update configuration
  Exec SQL
    CALL AISQLTKT.CONF_UPDATE_KAFKA(
      :UsrPrf,
      :KfProtoc,
      :FullBroker,
      :KfPort,
      :FullTopic
    );
  
  If SQLCODE = 0;
    StsMsg = 'Configuration saved successfully';
  Else;
    ErrMsg = 'Error saving configuration: ' + %Char(SQLCODE);
  EndIf;
End-Proc;