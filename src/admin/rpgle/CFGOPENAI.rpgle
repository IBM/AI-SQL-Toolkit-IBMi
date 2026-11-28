**FREE
//==============================================================================
// DBSDK Configuration Administration - OpenAI Compatible Configuration Program
//==============================================================================
// Purpose: OpenAI Compatible configuration screen for editing user settings
//
// Compilation:
//   CRTBNDRPG PGM(AISQLTKT/CFGOPENAI) +
//             SRCSTMF('/home/jwoehr/work/AI/AI-SQL-Toolkit/AI-SDK-Db2-IBMi/src/admin/rpgle/CFGOPENAI.rpgle') +
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
Dcl-F CFGOPENAID WorkStn IndDs(Indicators);

// Indicator data structure
Dcl-Ds Indicators;
  Exit Ind Pos(3);
  Cancel Ind Pos(12);
End-Ds;

// Screen fields

// Program variables
Dcl-S OldUsrPrf Varchar(10) Inz(*Blanks);
Dcl-S FullApiKey Varchar(8000);
Dcl-S FullBasePath Varchar(1000);

//==============================================================================
// Main Processing
//==============================================================================

Dow Not Exit;
  // If no user profile, prompt for it
  If UsrPrf = *Blanks;
    ErrMsg = 'Enter user profile to configure';
  EndIf;
  
  // Display screen
  Exfmt OACONFG;
  
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
    If OaServer <> *Blanks Or OaModel <> *Blanks Or OaApiKey <> *Blanks;
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
    SELECT IFNULL(openai_compatible_protocol, ''),
           IFNULL(openai_compatible_server, ''),
           IFNULL(openai_compatible_port, 0),
           IFNULL(openai_compatible_model, ''),
           IFNULL(openai_compatible_apikey, ''),
           IFNULL(openai_compatible_basepath, '')
    INTO :OaProtoc,
         :OaServer,
         :OaPort,
         :OaModel,
         :FullApiKey,
         :FullBasePath
    FROM AISQLTKT.CONF
    WHERE USRPRF = :UsrPrf;
  
  If SQLCODE = 0;
    // Split API key into two 50-char fields
    If %Len(FullApiKey) > 50;
      OaApiKey = %Subst(FullApiKey:1:50);
      OaApiKy2 = %Subst(FullApiKey:51:50);
    ElseIf %Len(FullApiKey) > 0;
      OaApiKey = FullApiKey;
      OaApiKy2 = *Blanks;
    Else;
      OaApiKey = *Blanks;
      OaApiKy2 = *Blanks;
    EndIf;
    
    // Base path fits in one field
    OaBasePt = FullBasePath;
  Else;
    ErrMsg = 'Error loading configuration: ' + %Char(SQLCODE);
  EndIf;
End-Proc;

//==============================================================================
// Save Configuration
//==============================================================================
Dcl-Proc SaveConfiguration;
  // Combine split API key fields
  FullApiKey = %Trim(OaApiKey) + %Trim(OaApiKy2);
  FullBasePath = OaBasePt;
  
  // Call SQL procedure to update configuration
  Exec SQL
    CALL AISQLTKT.CONF_UPDATE_OPENAI(
      :UsrPrf,
      :OaProtoc,
      :OaServer,
      :OaPort,
      :OaModel,
      :FullApiKey,
      :FullBasePath
    );
  
  If SQLCODE = 0;
    StsMsg = 'Configuration saved successfully';
  Else;
    ErrMsg = 'Error saving configuration: ' + %Char(SQLCODE);
  EndIf;
End-Proc;