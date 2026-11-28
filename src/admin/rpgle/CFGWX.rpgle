**FREE
//==============================================================================
// DBSDK Configuration Administration - WatsonX Configuration Program
//==============================================================================
// Purpose: WatsonX configuration screen for editing user settings
//
// Compilation:
//   CRTBNDRPG PGM(AISQLTKT/CFGWX) +
//             SRCSTMF('/home/jwoehr/work/AI/AI-SQL-Toolkit/AI-SDK-Db2-IBMi/src/admin/rpgle/CFGWX.rpgle') +
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
Dcl-F CFGWXD WorkStn IndDs(Indicators);

// Indicator data structure
Dcl-Ds Indicators;
  Exit Ind Pos(3);
  Cancel Ind Pos(12);
End-Ds;

// Screen fields

// Program variables
Dcl-S OldUsrPrf Varchar(10) Inz(*Blanks);

//==============================================================================
// Main Processing
//==============================================================================

Dow Not Exit;
  // If no user profile, prompt for it
  If UsrPrf = *Blanks;
    ErrMsg = 'Enter user profile to configure';
  EndIf;
  
  // Display screen
  Exfmt WXCONFIG;
  
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
    If WxRegion <> *Blanks Or WxApiVer <> *Blanks Or WxApiKey <> *Blanks Or WxProjId <> *Blanks;
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
    SELECT IFNULL(watsonx_region, ''),
           IFNULL(watsonx_apiVersion, ''),
           IFNULL(watsonx_apikey, ''),
           IFNULL(watsonx_projectid, '')
    INTO :WxRegion,
         :WxApiVer,
         :WxApiKey,
         :WxProjId
    FROM AISQLTKT.CONF
    WHERE USRPRF = :UsrPrf;
  
  If SQLCODE <> 0;
    ErrMsg = 'Error loading configuration: ' + %Char(SQLCODE);
  EndIf;
End-Proc;

//==============================================================================
// Save Configuration
//==============================================================================
Dcl-Proc SaveConfiguration;
  // Call SQL procedure to update configuration
  Exec SQL
    CALL AISQLTKT.CONF_UPDATE_WATSONX(
      :UsrPrf,
      :WxRegion,
      :WxApiVer,
      :WxApiKey,
      :WxProjId
    );
  
  If SQLCODE = 0;
    StsMsg = 'Configuration saved successfully';
  Else;
    ErrMsg = 'Error saving configuration: ' + %Char(SQLCODE);
  EndIf;
End-Proc;