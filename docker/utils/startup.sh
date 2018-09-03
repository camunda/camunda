#!/bin/bash -xeu

configFile=/usr/local/zeebe/conf/zeebe.cfg.toml

export ZEEBE_HOST=${ZEEBE_HOST:-$(hostname -i)}
BOOTSTAP=${BOOTSTRAP:-0}

if [[ "$DEPLOY_ON_KUBERNETES" == "true" ]]; then
    ZEEBE_HOST="${HOST}.${DNS}"
fi

sed -i "s/bootstrap =.*/bootstrap = ${BOOTSTRAP}/g" $configFile

exec /usr/local/zeebe/bin/broker
