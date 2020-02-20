#!/bin/bash -xeu

export ZEEBE_HOST=${ZEEBE_HOST:-$(hostname -i)}
export ZEEBE_GATEWAY_CLUSTER_HOST=${ZEEBE_GATEWAY_CLUSTER_HOST:-${ZEEBE_HOST}}

if [ "$ZEEBE_STANDALONE_GATEWAY" = "true" ]; then
    exec /usr/local/zeebe/bin/gateway
else
    exec /usr/local/zeebe/bin/broker
fi
