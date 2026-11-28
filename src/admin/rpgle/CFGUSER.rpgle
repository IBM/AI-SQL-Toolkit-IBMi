**FREE
//==============================================================================
// DBSDK Configuration Administration - User Management Program
//==============================================================================
// Purpose: User management program with add, change, delete, and display
//          Simple list display (no subfile)
//
// Compilation:
//   CRTSQLRPGI PGM(AISQLTKT/CFGUSER) +
//             SRCSTMF('/home/jwoehr/work/AI/AI-SQL-Toolkit/AI-SDK-Db2-IBMi/src/admin/rpgle/CFGUSER.rpgle') +
//             COMMIT(*NONE) +
//             DBGVIEW(*SOURCE) +
//             COMPILEOPT('DFTACTGRP(*NO) ACTGRP(*NEW) TGTCCSID(*JOB)') +
//             USRPRF(*OWNER) +
//             CVTCCSID(*JOB)
//
// Author: AI-SDK-Db2-IBMi Project
// Date: 2026-03-22
//==============================================================================

Ctl-Opt DftActGrp(*No) ActGrp(*New) Option(*SrcStmt:*NoDebugIO);

// Display file
Dcl-F CFGUSERD WorkStn IndDs(Indicators);

// Indicator data structure
Dcl-Ds Indicators;
  Exit Ind Pos(3);
  Cancel Ind Pos(12);
End-Ds;

//==============================================================================
// Main Processing
//==============================================================================

// Clear screen fields initially
Clear USERMAIN;

Dow Not Exit;
  
  // Display screen and get user input
  Exfmt USERMAIN;
  
  If Exit;
    Leave;
  EndIf;
  
  // Clear messages from previous iteration
  Clear ErrMsg;
  Clear StsMsg;
  
  // Process action
  Select;
    When Action = '1'; // Add user
      AddUser();
      Action = *Blanks; // Clear action to prevent accidental re-execution
    When Action = '2'; // Change user
      ChangeUser();
      Action = *Blanks;
    When Action = '4'; // Delete user
      DeleteUser();
      Action = *Blanks;
    When Action = '5'; // Display user
      DisplayUser();
      Action = *Blanks;
    When Action = '9'; // List users
      ListUsers();
      Action = *Blanks;
  EndSl;
  
EndDo;

*InLR = *On;
Return;

//==============================================================================
// Add User
//==============================================================================
Dcl-Proc AddUser;
  
  If UsrPrfI = *Blanks;
    ErrMsg = 'User profile required';
    Return;
  EndIf;
  
  Exec SQL
    CALL AISQLTKT.CONF_REGISTER_USER(:UsrPrfI);
  
  If SQLCODE = 0;
    StsMsg = 'User added successfully';
    Clear ErrMsg;
  Else;
    ErrMsg = 'Error adding user: ' + %Char(SQLCODE);
    Clear StsMsg;
  EndIf;
  
End-Proc;

//==============================================================================
// Change User (placeholder - no additional fields to change)
//==============================================================================
Dcl-Proc ChangeUser;
  
  StsMsg = 'Change not implemented - users have no additional fields';
  Clear ErrMsg;
  
End-Proc;

//==============================================================================
// Delete User
//==============================================================================
Dcl-Proc DeleteUser;
  
  If UsrPrfI = *Blanks;
    ErrMsg = 'User profile required';
    Return;
  EndIf;
  
  Exec SQL
    CALL AISQLTKT.CONF_REMOVE_USER(:UsrPrfI);
  
  If SQLCODE = 0;
    StsMsg = 'User deleted successfully';
    Clear ErrMsg;
  Else;
    ErrMsg = 'Error deleting user: ' + %Char(SQLCODE);
    Clear StsMsg;
  EndIf;
  
End-Proc;

//==============================================================================
// Display User
//==============================================================================
Dcl-Proc DisplayUser;
  
  Dcl-S FoundUser Char(10);
  
  If UsrPrfI = *Blanks;
    ErrMsg = 'User profile required';
    Return;
  EndIf;
  
  Exec SQL
    SELECT USRPRF INTO :FoundUser
    FROM AISQLTKT.CONF
    WHERE USRPRF = :UsrPrfI;
  
  If SQLCODE = 0;
    StsMsg = 'User found: ' + %Trim(FoundUser);
    Clear ErrMsg;
  Else;
    ErrMsg = 'User not found';
    Clear StsMsg;
  EndIf;
  
End-Proc;

//==============================================================================
// List Users
//==============================================================================
Dcl-Proc ListUsers;
  
  Dcl-S i Int(10);
  Dcl-S TempUsrPrf Char(10);
  
  // Clear user list fields
  User01 = *Blanks;
  User02 = *Blanks;
  User03 = *Blanks;
  User04 = *Blanks;
  User05 = *Blanks;
  User06 = *Blanks;
  User07 = *Blanks;
  User08 = *Blanks;
  User09 = *Blanks;
  User10 = *Blanks;
  
  i = 0;
  
  Exec SQL
    DECLARE C2 CURSOR FOR
      SELECT USRPRF FROM AISQLTKT.CONF ORDER BY USRPRF;
  
  Exec SQL OPEN C2;
  
  Exec SQL FETCH C2 INTO :TempUsrPrf;
  
  Dow SQLCODE = 0 And i < 10;
    i += 1;
    Select;
      When i = 1;
        User01 = TempUsrPrf;
      When i = 2;
        User02 = TempUsrPrf;
      When i = 3;
        User03 = TempUsrPrf;
      When i = 4;
        User04 = TempUsrPrf;
      When i = 5;
        User05 = TempUsrPrf;
      When i = 6;
        User06 = TempUsrPrf;
      When i = 7;
        User07 = TempUsrPrf;
      When i = 8;
        User08 = TempUsrPrf;
      When i = 9;
        User09 = TempUsrPrf;
      When i = 10;
        User10 = TempUsrPrf;
    EndSl;
    Exec SQL FETCH C2 INTO :TempUsrPrf;
  EndDo;
  
  Exec SQL CLOSE C2;
  
  If i = 0;
    StsMsg = 'No users found';
  Else;
    StsMsg = %Char(i) + ' user(s) listed';
  EndIf;
  
  Clear ErrMsg;
  
End-Proc;