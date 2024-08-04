#!/bin/sh -xeu


if [ "$ZEEBE_STANDALONE_GATEWAY" = "true" ]; then
    exec /usr/local/zeebe/bin/gateway
elif [ "$ZEEBE_RESTORE" = "true" ]; then
    exec /usr/local/zeebe/bin/restore --backupId=${ZEEBE_RESTORE_FROM_BACKUP_ID}
else
    exec /usr/local/zeebe/bin/broker
fi
