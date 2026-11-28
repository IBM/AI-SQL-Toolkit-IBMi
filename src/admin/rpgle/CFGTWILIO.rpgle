**FREE
//==============================================================================
// DBSDK Configuration Administration - Twilio Configuration Program
//==============================================================================
// Purpose: Twilio configuration screen for editing user settings
//
// Compilation:
//   CRTBNDRPG PGM(AISQLTKT/CFGTWILIO) +
//             SRCSTMF('/home/jwoehr/work/AI/AI-SQL-Toolkit/AI-SDK-Db2-IBMi/src/admin/rpgle/CFGTWILIO.rpgle') +
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
Dcl-F CFGTWILIOD WorkStn IndDs(Indicators);

// Indicator data structure
Dcl-Ds Indicators;
  Exit Ind Pos(3);
  Cancel Ind Pos(12);
End-Ds;

// Screen fields

// Program variables
Dcl-S OldUsrPrf Varchar(10) Inz(*Blanks);
Dcl-S FullNumber Varchar(1000);
Dcl-S FullSid Varchar(1000);
Dcl-S FullAuthToken Varchar(1000);

//==============================================================================
// Main Processing
//==============================================================================

Dow Not Exit;
  // If no user profile, prompt for it
  If UsrPrf = *Blanks;
    ErrMsg = 'Enter user profile to configure';
  EndIf;
  
  // Display screen
  Exfmt TWCONFIG;
  
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
    If TwNumber <> *Blanks Or TwSid <> *Blanks Or TwAuthTn <> *Blanks;
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
    SELECT IFNULL(twilio_number, ''),
           IFNULL(twilio_sid, ''),
           IFNULL(twilio_authtoken, '')
    INTO :FullNumber,
         :FullSid,
         :FullAuthToken
    FROM AISQLTKT.CONF
    WHERE USRPRF = :UsrPrf;
  
  If SQLCODE = 0;
    // Split number into two 50-char fields
    If %Len(FullNumber) > 50;
      TwNumber = %Subst(FullNumber:1:50);
      TwNumbr2 = %Subst(FullNumber:51:50);
    ElseIf %Len(FullNumber) > 0;
      TwNumber = FullNumber;
      TwNumbr2 = *Blanks;
    Else;
      TwNumber = *Blanks;
      TwNumbr2 = *Blanks;
    EndIf;
    
    // Split SID into two 50-char fields
    If %Len(FullSid) > 50;
      TwSid = %Subst(FullSid:1:50);
      TwSid2 = %Subst(FullSid:51:50);
    ElseIf %Len(FullSid) > 0;
      TwSid = FullSid;
      TwSid2 = *Blanks;
    Else;
      TwSid = *Blanks;
      TwSid2 = *Blanks;
    EndIf;
    
    // Split auth token into two 50-char fields
    If %Len(FullAuthToken) > 50;
      TwAuthTn = %Subst(FullAuthToken:1:50);
      TwAuthK2 = %Subst(FullAuthToken:51:50);
    ElseIf %Len(FullAuthToken) > 0;
      TwAuthTn = FullAuthToken;
      TwAuthK2 = *Blanks;
    Else;
      TwAuthTn = *Blanks;
      TwAuthK2 = *Blanks;
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
  FullNumber = %Trim(TwNumber) + %Trim(TwNumbr2);
  FullSid = %Trim(TwSid) + %Trim(TwSid2);
  FullAuthToken = %Trim(TwAuthTn) + %Trim(TwAuthK2);
  
  // Call SQL procedure to update configuration
  Exec SQL
    CALL AISQLTKT.CONF_UPDATE_TWILIO(
      :UsrPrf,
      :FullNumber,
      :FullSid,
      :FullAuthToken
    );
  
  If SQLCODE = 0;
    StsMsg = 'Configuration saved successfully';
  Else;
    ErrMsg = 'Error saving configuration: ' + %Char(SQLCODE);
  EndIf;
End-Proc;