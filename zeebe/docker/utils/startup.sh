#!/bin/sh -xeu

if [ "$ZEEBE_STANDALONE_GATEWAY" = "true" ]; then
    exec /usr/local/zeebe/bin/gateway
elif [ "$ZEEBE_RESTORE" = "true" ]; then
  if [ "$ZEEBE_RESTORE_FROM_BACKUP_ID" ]; then
    exec /usr/local/zeebe/bin/restore --backupId="${ZEEBE_RESTORE_FROM_BACKUP_ID}"
  elif [ -z "$ZEEBE_RESTORE_TO_TIMESTAMP" ]; then
    exec /usr/local/zeebe/bin/restore --from="${ZEEBE_RESTORE_FROM_TIMESTAMP}"
  else
    exec /usr/local/zeebe/bin/restore --from="${ZEEBE_RESTORE_FROM_TIMESTAMP}" --to="${ZEEBE_RESTORE_TO_TIMESTAMP}"
  fi
else
  exec /usr/local/zeebe/bin/broker
fi
