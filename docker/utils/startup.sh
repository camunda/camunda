#!/bin/bash -xeu

configFile=/usr/local/zeebe/conf/zeebe.cfg.toml

INITIAL_CONTACT_POINT=${INITIAL_CONTACT_POINT:-}
ZEEBE_HOST=${ZEEBE_HOST:-$(hostname -i)}
BOOTSTAP=${BOOTSTRAP:-0}

if [[ "$DEPLOY_ON_KUBERNETES" == "true" ]]; then
    ZEEBE_HOST="${HOST}.${DNS}"
fi

sed -i "s/bootstrap =.*/bootstrap = ${BOOTSTRAP}/g" $configFile
sed -i "s/.*host =.*/host = \"${ZEEBE_HOST}\"/g" $configFile

if [ -n "${INITIAL_CONTACT_POINT}" ]; then
    sed -i "s/# initialContactPoints = \[\].*/initialContactPoints = \[\n\t\"${INITIAL_CONTACT_POINT}\"\n\]/g" $configFile
fi

exec /usr/local/zeebe/bin/broker
