#!/bin/bash -xeu

configFile=/usr/local/zeebe/conf/zeebe.cfg.toml

export ZEEBE_HOST=${ZEEBE_HOST:-$(hostname -i)}

if [ "$ZEEBE_STANDALONE_GATEWAY" = "true" ]; then
    exec /usr/local/zeebe/bin/gateway
else
    exec /usr/local/zeebe/bin/broker
fi
