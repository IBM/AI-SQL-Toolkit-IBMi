#!/QOpenSys/usr/bin/sh
# Prints the build targets in dependency order. Each module's utils.sql defines the helper functions
# its other files call, and Db2 resolves routine references when a routine is created, so every
# utils.sql is built before the rest.
echo .build/src/conf.sql.done
echo .build/src/conf_rcac.sql.done
ls -b src/*/utils.sql | awk '{print ".build/" $1".done";}'
ls -b src/*/*.sql | grep -v '/utils\.sql$' | awk '{print ".build/" $1".done";}'
# The CONF administration procedures used by the src/admin programs. conf_admin.sql holds copies of
# the same procedures as the individual files, so it is left out (src/admin/Makefile does the same).
ls -b src/admin/sql/conf_*.sql | grep -v '/conf_admin\.sql$' | awk '{print ".build/" $1".done";}'
