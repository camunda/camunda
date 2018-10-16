#!/bin/bash -xeu

configFile=/usr/local/zeebe/conf/zeebe.cfg.toml

export ZEEBE_HOST=${ZEEBE_HOST:-$(hostname -i)}

if [[ "$DEPLOY_ON_KUBERNETES" == "true" ]]; then
    ZEEBE_HOST="${HOST}.${DNS}"
fi

exec /usr/local/zeebe/bin/broker
