#!/bin/sh -xeu

# Load AppCDS archive if present (generated at image build time).
CDS_ARCHIVE="${ZB_HOME:-/usr/local/zeebe}/camunda.jsa"
if [ -f "${CDS_ARCHIVE}" ]; then
  JAVA_OPTS="${JAVA_OPTS:-} -XX:SharedArchiveFile=${CDS_ARCHIVE}"
  export JAVA_OPTS
fi

if [ "$ZEEBE_STANDALONE_GATEWAY" = "true" ]; then
    exec /usr/local/zeebe/bin/gateway
elif [ "$ZEEBE_RESTORE" = "true" ]; then
  if [ "${ZEEBE_RESTORE_FROM_BACKUP_ID:-}" ]; then
    exec /usr/local/zeebe/bin/restore --backupId="${ZEEBE_RESTORE_FROM_BACKUP_ID}"
  elif [ "${ZEEBE_RESTORE_FROM_TIMESTAMP:-}" ] && [ "${ZEEBE_RESTORE_TO_TIMESTAMP:-}" ]; then
    exec /usr/local/zeebe/bin/restore --from="${ZEEBE_RESTORE_FROM_TIMESTAMP}" --to="${ZEEBE_RESTORE_TO_TIMESTAMP}"
  elif [ "${ZEEBE_RESTORE_FROM_TIMESTAMP:-}" ]; then
    exec /usr/local/zeebe/bin/restore --from="${ZEEBE_RESTORE_FROM_TIMESTAMP}"
  elif [ "${ZEEBE_RESTORE_TO_TIMESTAMP:-}" ]; then
    exec /usr/local/zeebe/bin/restore --to="${ZEEBE_RESTORE_TO_TIMESTAMP}"
  else
    exec /usr/local/zeebe/bin/restore
  fi
else
  exec /usr/local/zeebe/bin/broker
fi
