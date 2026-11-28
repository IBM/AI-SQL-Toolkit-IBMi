**FREE
//==============================================================================
// DBSDK Configuration Administration - Ollama Configuration Program
//==============================================================================
// Purpose: Ollama configuration screen for editing user settings
//
// Compilation:
//   CRTBNDRPG PGM(AISQLTKT/CFGOLLAMA) +
//             SRCSTMF('/home/jwoehr/work/AI/AI-SQL-Toolkit/AI-SDK-Db2-IBMi/src/admin/rpgle/CFGOLLAMA.rpgle') +
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
Dcl-F CFGOLLAMD WorkStn IndDs(Indicators);

// Indicator data structure
Dcl-Ds Indicators;
  Exit Ind Pos(3);
  Cancel Ind Pos(12);
End-Ds;

// Screen fields

// Program variables
Dcl-S OldUsrPrf Varchar(10) Inz(*Blanks);
Dcl-S FullServer Varchar(1000);
Dcl-S FullModel Varchar(1000);

//==============================================================================
// Main Processing
//==============================================================================

Dow Not Exit;
  // If no user profile, prompt for it
  If UsrPrf = *Blanks;
    ErrMsg = 'Enter user profile to configure';
  EndIf;
  
  // Display screen
  Exfmt OLCONFIG;
  
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
    If OlServer <> *Blanks Or OlModel <> *Blanks Or OlProtoc <> *Blanks;
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
    SELECT IFNULL(ollama_protocol, ''),
           IFNULL(ollama_server, ''),
           IFNULL(ollama_port, 0),
           IFNULL(ollama_model, '')
    INTO :OlProtoc,
         :FullServer,
         :OlPort,
         :FullModel
    FROM AISQLTKT.CONF
    WHERE USRPRF = :UsrPrf;
  
  If SQLCODE = 0;
    // Split server into two 50-char fields
    If %Len(FullServer) > 50;
      OlServer = %Subst(FullServer:1:50);
      OlSrvr2 = %Subst(FullServer:51);
    ElseIf %Len(FullServer) > 0;
      OlServer = FullServer;
      OlSrvr2 = *Blanks;
    Else;
      OlServer = *Blanks;
      OlSrvr2 = *Blanks;
    EndIf;
    
    // Split model into two 50-char fields
    If %Len(FullModel) > 50;
      OlModel = %Subst(FullModel:1:50);
      OlModl2 = %Subst(FullModel:51);
    ElseIf %Len(FullModel) > 0;
      OlModel = FullModel;
      OlModl2 = *Blanks;
    Else;
      OlModel = *Blanks;
      OlModl2 = *Blanks;
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
  FullServer = %Trim(OlServer) + %Trim(OlSrvr2);
  FullModel = %Trim(OlModel) + %Trim(OlModl2);
  
  // Call SQL procedure to update configuration
  Exec SQL
    CALL AISQLTKT.CONF_UPDATE_OLLAMA(
      :UsrPrf,
      :OlProtoc,
      :FullServer,
      :OlPort,
      :FullModel
    );
  
  If SQLCODE = 0;
    StsMsg = 'Configuration saved successfully';
  Else;
    ErrMsg = 'Error saving configuration: ' + %Char(SQLCODE);
  EndIf;
End-Proc;