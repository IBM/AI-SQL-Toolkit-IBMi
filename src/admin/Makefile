#===============================================================================
# DBSDK Configuration Administration - Makefile
#===============================================================================
# Purpose: Automated build for compiling all components
#          Compiles SQL procedures, display files, and RPGLE programs
#
# Usage: make [target] [VERBOSE=1]
#   make all     - Build all components (default)
#   make sql     - Build SQL procedures only
#   make dspf    - Build display files only
#   make rpgle   - Build RPGLE programs only
#   make clean   - Remove all compiled objects
#   make help    - Show this help message
#
# Options:
#   VERBOSE=1    - Show compilation output (default: suppressed)
#                  Example: make VERBOSE=1
#
# Prerequisites:
#   - AISQLTKT library must exist
#   - User must have authority to create objects in AISQLTKT
#   - IBM i commands must be available (system, cl)
#
# Author: AI-SDK-Db2-IBMi Project
# Date: 2026-04-06
#===============================================================================

# Use bash as shell for better compatibility
SHELL := /bin/bash

# Verbose mode control
VERBOSE ?= 0
ifeq ($(VERBOSE),1)
    REDIRECT :=
else
    REDIRECT := > /dev/null 2>&1
endif

# Color codes for output
RED     := \033[0;31m
GREEN   := \033[0;32m
YELLOW  := \033[1;33m
BLUE    := \033[0;34m
NC      := \033[0m

# Base directory
BASE_DIR := $(shell pwd)

# Target library
TARGET_LIB := AISQLTKT
SOURCE_LIB := JWOEHR

# SQL procedure files
SQL_PROCEDURES := \
	conf_get_user \
	conf_list_users \
	conf_update_watsonx \
	conf_update_ollama \
	conf_update_openai \
	conf_update_kafka \
	conf_update_slack \
	conf_update_twilio

# Display files
DISPLAY_FILES := \
	CFGMENUD \
	CFGUSERD \
	CFGWXD \
	CFGOLLAMD \
	CFGOPENAID \
	CFGKAFKAD \
	CFGSLACKD \
	CFGTWILIOD

# RPGLE programs
RPGLE_PROGRAMS := \
	CFGUSER \
	CFGWX \
	CFGOLLAMA \
	CFGOPENAI \
	CFGKAFKA \
	CFGSLACK \
	CFGTWILIO \
	CFGMENU

# Phony targets
.PHONY: all sql dspf rpgle clean help header summary

# Default target
all: header sql dspf rpgle summary

#===============================================================================
# Header and Summary
#===============================================================================

header:
	@printf "$(BLUE)===============================================================================$(NC)\n"
	@printf "$(BLUE)DBSDK Configuration Administration - Build Script$(NC)\n"
	@printf "$(BLUE)===============================================================================$(NC)\n"
	@printf "Starting build process...\n"
	@printf "\n"

summary:
	@printf "\n"
	@printf "$(BLUE)===============================================================================$(NC)\n"
	@printf "$(BLUE)Build Summary$(NC)\n"
	@printf "$(BLUE)===============================================================================$(NC)\n"
	@printf "$(GREEN)✓ Build completed successfully!$(NC)\n"
	@printf "\n"
	@printf "All components have been compiled to library $(TARGET_LIB)\n"
	@printf "\n"
	@printf "To start the application, run:\n"
	@printf "  CALL PGM($(TARGET_LIB)/CFGMENU)\n"
	@printf "\n"
	@$(MAKE) --no-print-directory list-components

list-components:
	@printf "Components created:\n"
	@printf "  SQL Procedures:\n"
	@for proc in $(SQL_PROCEDURES); do \
		printf "    - %s\n" "$$(echo $$proc | tr '[:lower:]' '[:upper:]')"; \
	done
	@printf "\n"
	@printf "  Display Files:\n"
	@for dspf in $(DISPLAY_FILES); do \
		printf "    - %s\n" "$$dspf"; \
	done
	@printf "\n"
	@printf "  Programs:\n"
	@for pgm in $(RPGLE_PROGRAMS); do \
		printf "    - %s\n" "$$pgm"; \
	done
	@printf "\n"

#===============================================================================
# SQL Procedures
#===============================================================================

sql:
	@printf "$(BLUE)===============================================================================$(NC)\n"
	@printf "$(BLUE)Step 1: Creating SQL Procedures$(NC)\n"
	@printf "$(BLUE)===============================================================================$(NC)\n"
	@$(MAKE) --no-print-directory $(addprefix sql/,$(SQL_PROCEDURES))
	@printf "\n"

sql/%:
	@printf "$(YELLOW)>>> Compiling $*.sql...$(NC)\n"
	@if system "QSYS/RUNSQLSTM SRCSTMF('$(BASE_DIR)/sql/$*.sql') COMMIT(*NONE) ERRLVL(21)" $(REDIRECT); then \
		printf "$(GREEN)✓ $* compiled successfully$(NC)\n"; \
	else \
		printf "$(RED)✗ Failed to compile $*$(NC)\n"; \
		exit 1; \
	fi

#===============================================================================
# Display Files
#===============================================================================

dspf:
	@printf "$(BLUE)===============================================================================$(NC)\n"
	@printf "$(BLUE)Step 2: Compiling Display Files$(NC)\n"
	@printf "$(BLUE)===============================================================================$(NC)\n"
	@$(MAKE) --no-print-directory $(addprefix dspf/,$(DISPLAY_FILES))
	@printf "\n"

dspf/%:
	@printf "$(YELLOW)>>> Compiling $*...$(NC)\n"
	@if cl "QSYS/CPYFRMSTMF FROMSTMF('$(BASE_DIR)/dspf/$*.dspf') TOMBR('/QSYS.LIB/$(SOURCE_LIB).LIB/QDDSSRC.FILE/$*.MBR') MBROPT(*REPLACE)" $(REDIRECT); then \
		if cl "QSYS/CRTDSPF FILE($(TARGET_LIB)/$*) SRCFILE($(SOURCE_LIB)/QDDSSRC) SRCMBR($*) GENLVL(30)" $(REDIRECT); then \
			printf "$(GREEN)✓ $* compiled successfully$(NC)\n"; \
		else \
			printf "$(RED)✗ Failed to compile $*$(NC)\n"; \
			exit 1; \
		fi; \
	else \
		printf "$(RED)✗ Failed to copy $* to source member$(NC)\n"; \
		exit 1; \
	fi

#===============================================================================
# RPGLE Programs
#===============================================================================

rpgle:
	@printf "$(BLUE)===============================================================================$(NC)\n"
	@printf "$(BLUE)Step 3: Compiling RPGLE Programs$(NC)\n"
	@printf "$(BLUE)===============================================================================$(NC)\n"
	@$(MAKE) --no-print-directory $(addprefix rpgle/,$(RPGLE_PROGRAMS))
	@printf "\n"

rpgle/%:
	@printf "$(YELLOW)>>> Compiling $*...$(NC)\n"
	@bash -c 'cl "QSYS/ADDLIBLE $(TARGET_LIB)" $(REDIRECT); cl "QSYS/CRTSQLRPGI OBJ($(TARGET_LIB)/$*) SRCSTMF('"'"'$(BASE_DIR)/rpgle/$*.rpgle'"'"') COMMIT(*NONE) DBGVIEW(*SOURCE) COMPILEOPT('"'"'DFTACTGRP(*NO) ACTGRP(*NEW) TGTCCSID(*JOB)'"'"') USRPRF(*OWNER) RDB(*LOCAL) DFTRDBCOL($(TARGET_LIB)) CVTCCSID(*JOB) SQLPATH(*LIBL)" $(REDIRECT)' && \
		printf "$(GREEN)✓ $* compiled successfully$(NC)\n" || \
		(printf "$(RED)✗ Failed to compile $*$(NC)\n"; exit 1)

#===============================================================================
# Clean Target
#===============================================================================

clean:
	@printf "$(YELLOW)Cleaning compiled objects from $(TARGET_LIB)...$(NC)\n"
	@bash -c "for proc in $(SQL_PROCEDURES); do \
		proc_upper=\$$(echo \$$proc | tr '[:lower:]' '[:upper:]'); \
		system \"QSYS/RUNSQL SQL('DROP PROCEDURE $(TARGET_LIB)/\$$proc_upper') COMMIT(*NONE)\" $(REDIRECT) || true; \
	done"
	@bash -c "for dspf in $(DISPLAY_FILES); do \
		cl \"QSYS/DLTF FILE($(TARGET_LIB)/\$$dspf)\" $(REDIRECT) || true; \
	done"
	@bash -c "for pgm in $(RPGLE_PROGRAMS); do \
		cl \"QSYS/DLTPGM PGM($(TARGET_LIB)/\$$pgm)\" $(REDIRECT) || true; \
	done"
	@printf "$(GREEN)✓ Clean completed$(NC)\n"

#===============================================================================
# Help Target
#===============================================================================

help:
	@printf "DBSDK Configuration Administration - Makefile\n"
	@printf "\n"
	@printf "Usage: make [target] [VERBOSE=1]\n"
	@printf "\n"
	@printf "Targets:\n"
	@printf "  all     - Build all components (default)\n"
	@printf "  sql     - Build SQL procedures only\n"
	@printf "  dspf    - Build display files only\n"
	@printf "  rpgle   - Build RPGLE programs only\n"
	@printf "  clean   - Remove all compiled objects\n"
	@printf "  help    - Show this help message\n"
	@printf "\n"
	@printf "Options:\n"
	@printf "  VERBOSE=1    - Show compilation output (default: suppressed)\n"
	@printf "               Example: make VERBOSE=1\n"
	@printf "               Example: make rpgle VERBOSE=1\n"
	@printf "\n"
	@printf "Prerequisites:\n"
	@printf "  - $(TARGET_LIB) library must exist\n"
	@printf "  - User must have authority to create objects in $(TARGET_LIB)\n"
	@printf "  - IBM i commands must be available (system, cl)\n"
	@printf "\n"

# Made with Bob