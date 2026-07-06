sync-password.sh changes the PG password via ALTER USER. PG does not kill existing connections on ALTER USER — but any new connection attempt with the old password fails. That's what staging shows: pods restarted and their cached password mismatched until you ran sync-password.
Liquibase lock — when a pod crashes mid-migration, it dies holding the lock. The new pod waits forever because the dead pod's session still holds it.
Exact order:
# 1. Fix PG password (matches K8s secret → PG user)
./sync-password.sh dev
./sync-password.sh staging
./sync-password.sh production

# 2. Release Liquibase locks (from pods that crashed before sync-password)
for db in customer inventory media order payment promotion rating recommendation; do
  kubectl exec -n postgres-dev postgresql-0 -- psql -U postgres -d $db -c \
    "UPDATE DATABASECHANGELOGLOCK SET LOCKED=FALSE, LOCKGRANTED=NULL, LOCKEDBY=NULL WHERE ID=1;"
done
# Repeat for staging:
for db in customer inventory media order payment promotion rating recommendation; do
  kubectl exec -n postgres-staging postgresql-0 -- psql -U postgres -d $db -c \
    "UPDATE DATABASECHANGELOGLOCK SET LOCKED=FALSE, LOCKGRANTED=NULL, LOCKEDBY=NULL WHERE ID=1;"
done
Since the apps use POSTGRESQL_USERNAME/POSTGRESQL_PASSWORD from yas-credentials-secret for their own DB connections, and Keycloak uses its own secret for its connection — after sync-password.sh aligns the PG user password, everything should recover on restart.