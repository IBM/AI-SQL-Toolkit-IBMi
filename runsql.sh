#!/QOpenSys/usr/bin/sh

SQL_FILE=$2
SQL_SCHEMA=$1
TSDIRBASE=".build"
DONE_FILE="$TSDIRBASE/$SQL_FILE.done"
echo "Running SQL File $SQL_FILE with schema $SCHEMA..."
echo "donefile is $DONE_FILE"
mkdir -p $(dirname $DONE_FILE)
system -kpieb "RUNSQLSTM SRCSTMF('$SQL_FILE') COMMIT(*NONE) NAMING(*SQL) DFTRDBCOL($SQL_CHEMA)" && touch $DONE_FILE