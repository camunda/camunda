#!/bin/bash -xeu

HOST=$(hostname -i)

if [ "$EUID" = "0" ]; then
  # This is here for migration purpose to run the zeebe process with the zeebe user going forward,
  # while in older releases it was run with root.
  chown -R 1000:0 /usr/local/zeebe/
  chmod -R 0775 /usr/local/zeebe/
  su -s /bin/bash zeebe -
fi

if [ "$ZEEBE_STANDALONE_GATEWAY" = "true" ]; then
    export ZEEBE_GATEWAY_NETWORK_HOST=${ZEEBE_GATEWAY_NETWORK_HOST:-${HOST}}
    export ZEEBE_GATEWAY_CLUSTER_HOST=${ZEEBE_GATEWAY_CLUSTER_HOST:-${ZEEBE_GATEWAY_NETWORK_HOST}}

    exec /usr/local/zeebe/bin/gateway
elif [ "$ZEEBE_RESTORE" = "true" ]; then
    exec /usr/local/zeebe/bin/restore --backupId=${ZEEBE_RESTORE_FROM_BACKUP_ID}
else
    export ZEEBE_BROKER_NETWORK_HOST=${ZEEBE_BROKER_NETWORK_HOST:-${HOST}}
    export ZEEBE_BROKER_GATEWAY_CLUSTER_HOST=${ZEEBE_BROKER_GATEWAY_CLUSTER_HOST:-${ZEEBE_BROKER_NETWORK_HOST}}

    exec /usr/local/zeebe/bin/broker
fi
