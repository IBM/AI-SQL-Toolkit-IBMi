#!/bin/sh
#
# Starts the dbsdk mock server, runs the test harness against the installed dbsdk_v1 toolkit, then
# stops the mock. Exits with the harness's status: 0 all tests passed, 1 a test failed, 2 a usage
# or environment error. Used by "make test"; can also be run as "sh test-harness/run-tests.sh"
# from the repository root (use "sh" explicitly: on IBM i PASE, #!/bin/sh resolves to QShell).
#
# Settings (environment variables):
#   MOCK_PORT        port for the mock server (default 8080)
#   MOCK_BIND        address the mock listens on (default 127.0.0.1; the toolkit must reach it)
#   MAPEPIRE_HOST    mapepire-server host (default localhost)
#   MAPEPIRE_PORT    mapepire-server port (default 8076)
#   MAPEPIRE_USER, MAPEPIRE_PASSWORD
#                    credentials; prompted for when unset and a console is available
#   DBSDK_TEST_ENV   a file of VAR=value lines to load first (default .dbsdk-test.env in the
#                    repository root, if it exists; git ignores it). Lets unattended runs get
#                    credentials without putting the password on a command line. It must not be
#                    readable by group or others.
#   HARNESS_ARGS     extra harness options, e.g. "--skip known-issue --report-dir reports"
#   JAVA             java command (default: java)

ROOT=$(cd "$(dirname "$0")/.." && pwd)

# Load settings (typically credentials) from a private file, if there is one
ENV_FILE=${DBSDK_TEST_ENV:-$ROOT/.dbsdk-test.env}
if [ -f "$ENV_FILE" ]; then
    case "$(ls -l "$ENV_FILE" | cut -c5-10)" in
        ------) ;;
        *)
            echo "$ENV_FILE is readable by other users; run 'chmod 600 $ENV_FILE' first." >&2
            exit 2
            ;;
    esac
    echo "Loading settings from $ENV_FILE"
    set -a
    # shellcheck disable=SC1090
    . "$ENV_FILE"
    set +a
fi
MOCK_JAR="$ROOT/mock/target/dbsdk-mock-server-1.0.0-jar-with-dependencies.jar"
HARNESS_JAR="$ROOT/test-harness/target/dbsdk-test-harness-1.0.0-jar-with-dependencies.jar"
MOCK_PORT=${MOCK_PORT:-8080}
MOCK_BIND=${MOCK_BIND:-127.0.0.1}
MAPEPIRE_HOST=${MAPEPIRE_HOST:-localhost}
MAPEPIRE_PORT=${MAPEPIRE_PORT:-8076}
JAVA=${JAVA:-java}
MOCK_LOG="$ROOT/test-harness/target/mock-server.log"
# Where this script reaches the mock: its bind address, or loopback if it listens on all addresses
case "$MOCK_BIND" in
    0.0.0.0 | ::) MOCK_LOCAL=127.0.0.1 ;;
    *) MOCK_LOCAL=$MOCK_BIND ;;
esac

for jar in "$MOCK_JAR" "$HARNESS_JAR"; do
    if [ ! -f "$jar" ]; then
        echo "Missing $jar; run 'make test' (or build mock/ and test-harness/ with mvn package) first." >&2
        exit 2
    fi
done

# The connection to a local mapepire-server never leaves this machine, and mapepire's default
# certificate is self-signed, so certificate verification is only enforced for remote servers.
INSECURE=
case "$MAPEPIRE_HOST" in
    localhost | 127.0.0.1) INSECURE=--insecure ;;
esac

echo "Starting mock server on $MOCK_BIND:$MOCK_PORT (log: $MOCK_LOG)"
mkdir -p "$(dirname "$MOCK_LOG")"
"$JAVA" -jar "$MOCK_JAR" --port "$MOCK_PORT" --bind "$MOCK_BIND" > "$MOCK_LOG" 2>&1 &
MOCK_PID=$!

# Stop the mock by asking it to shut down: on IBM i the JVM runs in its own job, and signalling
# the java launcher's process does not reliably end it. The signal is kept as a fallback.
stop_mock() {
    if command -v curl > /dev/null 2>&1; then
        curl -s -X POST --max-time 10 "http://$MOCK_LOCAL:$MOCK_PORT/mock/shutdown" > /dev/null 2>&1
    fi
    kill "$MOCK_PID" 2> /dev/null
    wait "$MOCK_PID" 2> /dev/null
}
trap stop_mock EXIT
trap 'exit 130' INT TERM

# Wait until the mock reports it is listening, or gives up (e.g. the port is in use)
waited=0
until grep -q "dbsdk-mock-server .* started" "$MOCK_LOG" 2>/dev/null; do
    if ! kill -0 "$MOCK_PID" 2>/dev/null; then
        echo "The mock server exited during startup:" >&2
        cat "$MOCK_LOG" >&2
        exit 2
    fi
    if [ "$waited" -ge 60 ]; then
        echo "The mock server did not start within 60 seconds:" >&2
        cat "$MOCK_LOG" >&2
        exit 2
    fi
    sleep 1
    waited=$((waited + 1))
done

# HARNESS_ARGS is intentionally unquoted so it can hold several options
# shellcheck disable=SC2086
"$JAVA" -jar "$HARNESS_JAR" --host "$MAPEPIRE_HOST" --port "$MAPEPIRE_PORT" --mock-host localhost --mock-port "$MOCK_PORT" \
    --mock-url "http://$MOCK_LOCAL:$MOCK_PORT" $INSECURE $HARNESS_ARGS
exit $?
