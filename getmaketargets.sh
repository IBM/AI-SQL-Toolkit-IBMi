#!/QOpenSys/usr/bin/sh
# Prints the build targets in dependency order. Each module's utils.sql defines the helper functions
# its other files call, and Db2 resolves routine references when a routine is created, so every
# utils.sql is built before the rest.
echo .build/src/conf.sql.done
echo .build/src/conf_rcac.sql.done
ls -b src/*/utils.sql | awk '{print ".build/" $1".done";}'
ls -b src/*/*.sql | grep -v '/utils\.sql$' | awk '{print ".build/" $1".done";}'
