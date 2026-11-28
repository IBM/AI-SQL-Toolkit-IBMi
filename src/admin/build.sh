#!/bin/bash
#===============================================================================
# DBSDK Configuration Administration - Build Script
#===============================================================================
# Purpose: Automated build script for compiling all components
#          Compiles SQL procedures, display files, and RPGLE programs
#
# Usage: ./build.sh
#
# Prerequisites:
#   - AISQLTKT library must exist
#   - User must have authority to create objects in AISQLTKT
#   - IBM i commands must be available (cl command)
#
# Author: AI-SDK-Db2-IBMi Project
# Date: 2026-03-22
#===============================================================================

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Base directory
BASE_DIR="/home/jwoehr/work/AI/AI-SQL-Toolkit/AI-SDK-Db2-IBMi/src/admin"

# Error counter
ERRORS=0

#===============================================================================
# Helper Functions
#===============================================================================

print_header() {
    echo -e "${BLUE}===============================================================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}===============================================================================${NC}"
}

print_step() {
    echo -e "${YELLOW}>>> $1${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
    ((ERRORS++))
}

#===============================================================================
# Main Build Process
#===============================================================================

print_header "DBSDK Configuration Administration - Build Script"
echo "Starting build process..."
echo ""

#===============================================================================
# Step 1: Create SQL Procedures
#===============================================================================

print_header "Step 1: Creating SQL Procedures"

# Array of SQL procedure files
SQL_PROCEDURES=(
    "conf_get_user"
    "conf_list_users"
    "conf_update_watsonx"
    "conf_update_ollama"
    "conf_update_openai"
    "conf_update_kafka"
    "conf_update_slack"
    "conf_update_twilio"
)

for sql_file in "${SQL_PROCEDURES[@]}"; do
    print_step "Compiling ${sql_file}.sql..."
    
    system "RUNSQLSTM SRCSTMF('${BASE_DIR}/sql/${sql_file}.sql') COMMIT(*NONE) ERRLVL(21)" 2>&1 > /dev/null
    
    if [ $? -eq 0 ]; then
        print_success "${sql_file} compiled successfully"
    else
        print_error "Failed to compile ${sql_file}"
    fi
done

echo ""

#===============================================================================
# Step 2: Compile Display Files
#===============================================================================

print_header "Step 2: Compiling Display Files"

# Array of display files
DISPLAY_FILES=(
    "CFGMENUD"
    "CFGUSERD"
    "CFGWXD"
    "CFGOLLAMD"
    "CFGOPENAID"
    "CFGKAFKAD"
    "CFGSLACKD"
    "CFGTWILIOD"
)

for dspf in "${DISPLAY_FILES[@]}"; do
    print_step "Compiling ${dspf}..."
    
    # Copy from IFS to source member
    cl "CPYFRMSTMF FROMSTMF('${BASE_DIR}/dspf/${dspf}.dspf') TOMBR('/QSYS.LIB/JWOEHR.LIB/QDDSSRC.FILE/${dspf}.MBR') MBROPT(*REPLACE)" 2>&1 > /dev/null
    
    if [ $? -ne 0 ]; then
        print_error "Failed to copy ${dspf} to source member"
        continue
    fi
    
    # Compile display file with GENLVL(30) to allow warnings
    cl "CRTDSPF FILE(AISQLTKT/${dspf}) SRCFILE(JWOEHR/QDDSSRC) SRCMBR(${dspf}) GENLVL(30)" 2>&1 > /dev/null
    
    if [ $? -eq 0 ]; then
        print_success "${dspf} compiled successfully"
    else
        print_error "Failed to compile ${dspf}"
    fi
done

echo ""

#===============================================================================
# Step 3: Compile RPGLE Programs
#===============================================================================

print_header "Step 3: Compiling RPGLE Programs"

cl "ADDLIBLE AISQLTKT" 2>&1 > /dev/null

# Array of RPGLE programs
RPGLE_PROGRAMS=(
    "CFGUSER"
    "CFGWX"
    "CFGOLLAMA"
    "CFGOPENAI"
    "CFGKAFKA"
    "CFGSLACK"
    "CFGTWILIO"
    "CFGMENU"
)

for pgm in "${RPGLE_PROGRAMS[@]}"; do
    print_step "Compiling ${pgm}..."
    
    # Use CRTSQLRPGI for programs with embedded SQL
    # CVTCCSID(*JOB) required for UTF-8 (1208) source files
    # SQLPATH(*LIBL) uses library list to find SQL procedures
    # INCDIR specifies library for external file descriptions
    # DFTRDBCOL(AISQLTKT) sets default collection for unqualified SQL names
    # CURLIB(AISQLTKT) sets current library for this compile job
    cl "CRTSQLRPGI OBJ(AISQLTKT/${pgm}) SRCSTMF('${BASE_DIR}/rpgle/${pgm}.rpgle') COMMIT(*NONE) DBGVIEW(*SOURCE) COMPILEOPT('DFTACTGRP(*NO) ACTGRP(*NEW) TGTCCSID(*JOB)') USRPRF(*OWNER) RDB(*LOCAL) DFTRDBCOL(AISQLTKT) CVTCCSID(*JOB) SQLPATH(*LIBL)" 2>&1 > /dev/null
    
    if [ $? -eq 0 ]; then
        print_success "${pgm} compiled successfully"
    else
        print_error "Failed to compile ${pgm}"
    fi
done

echo ""

#===============================================================================
# Build Summary
#===============================================================================

print_header "Build Summary"

if [ $ERRORS -eq 0 ]; then
    echo -e "${GREEN}✓ Build completed successfully!${NC}"
    echo ""
    echo "All components have been compiled to library AISQLTKT"
    echo ""
    echo "To start the application, run:"
    echo "  CALL PGM(AISQLTKT/CFGMENU)"
    echo ""
else
    echo -e "${RED}✗ Build completed with ${ERRORS} error(s)${NC}"
    echo ""
    echo "Please review the errors above and correct them."
    echo "Check the job log for detailed error messages:"
    echo "  DSPJOBLOG"
    echo ""
fi

#===============================================================================
# Component List
#===============================================================================

echo "Components created:"
echo "  SQL Procedures:"
echo "    - CONF_GET_USER"
echo "    - CONF_LIST_USERS"
echo "    - CONF_UPDATE_WATSONX"
echo "    - CONF_UPDATE_OLLAMA"
echo "    - CONF_UPDATE_OPENAI"
echo "    - CONF_UPDATE_KAFKA"
echo "    - CONF_UPDATE_SLACK"
echo "    - CONF_UPDATE_TWILIO"
echo ""
echo "  Display Files:"
for dspf in "${DISPLAY_FILES[@]}"; do
    echo "    - ${dspf}"
done
echo ""
echo "  Programs:"
for pgm in "${RPGLE_PROGRAMS[@]}"; do
    echo "    - ${pgm}"
done
echo ""

exit $ERRORS

# Made with Bob
