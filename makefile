SCHEMA=dbsdk_v1
OBJS =  $(shell ./getmaketargets.sh)

# Maven for the Java projects (mock server and test harness): the IBM i open source package
# (yum install maven). Override elsewhere, e.g. "make test-build MVN=mvn".
MVN ?= /QOpenSys/pkgs/bin/mvn
MVN_FLAGS ?= -B -q

# On IBM i, "java" is a PASE launcher outside any JDK, so Maven cannot work out JAVA_HOME from it.
# Default to the Java 8 JDK (what "java" runs by default) unless one is set.
ifeq ($(shell uname),OS400)
JAVA_HOME ?= /QOpenSys/QIBM/ProdData/JavaVM/jdk80/64bit
export JAVA_HOME
endif

.PHONY: all clean test test-build

all: buildts.txt

/qsys.lib/$(SCHEMA).lib:
	-system -s "RUNSQL SQL('CREATE SCHEMA $(SCHEMA)') COMMIT(*NONE)" && echo created schema $(SCHEMA)

buildts.txt: /qsys.lib/$(SCHEMA).lib  $(OBJS)
	date > buildts.txt

clean:
	rm -r .build

.build/%.done: %
	./runsql.sh $(SCHEMA) $<

# One SQL script with the whole toolkit, in build order: the source of each item in $(OBJS)
# (.build/<file>.done is the stamp for <file>), each followed by a newline in case it lacks one.
.build/master_install.sql: $(OBJS)
	mkdir -p $(@D)
	: > $@
	for f in $(patsubst .build/%.done,%,$(OBJS)); do cat "$$f" >> $@; echo >> $@; done

# Build the mock server and the test harness jars. "clean" so that a deployment that preserved old
# file times (e.g. copied with tar) can never leave stale classes behind.
test-build:
	cd mock && $(MVN) $(MVN_FLAGS) clean package
	cd test-harness && $(MVN) $(MVN_FLAGS) clean package

# Options for the test harness. By default the tests for open toolkit bugs (tag known-issue) are
# skipped, so a passing run means no regressions; "make test HARNESS_ARGS=" runs them too.
HARNESS_ARGS ?= --skip known-issue
export HARNESS_ARGS

# Build the toolkit and the test tools, start the mock server, and run the test suite against
# the toolkit through mapepire-server (localhost:8076 by default). See test-harness/README.md.
test: all test-build
	sh test-harness/run-tests.sh
