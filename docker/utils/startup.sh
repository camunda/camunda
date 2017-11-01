#!/bin/bash -xeu

cfgHome=/usr/local/zeebe/conf/zeebe.cfg.toml
cfgLog=/usr/local/zeebe/conf/log4j2.xml

INITIAL_CONTACT_POINT=${INITIAL_CONTACT_POINT:-}

if [[ "$DEPLOY_ON_KUBERNETES" == "true" ]]; then

    DNSNAME="${HOST}.${DNS}"

    sed -i "s/@DNSNAME@/${DNSNAME}/g" $cfgHome

    if [[ -n $INITIAL_CONTACT_POINT && ! $HOST =~ .*-0$ ]]; then
        sed -i "s/@INITIAL_CONTACT_POINT@/initialContactPoints = \[\n\t\"${INITIAL_CONTACT_POINT}\"\n\]/g" $cfgHome
    else
        sed -i "s/@INITIAL_CONTACT_POINT@//g" $cfgHome
    fi

else

    sed -i "s/@DNSNAME@/$(hostname -i)/g" $cfgHome

    if [ -n "${INITIAL_CONTACT_POINT}" ]; then
        sed -i "s/@INITIAL_CONTACT_POINT@/initialContactPoints = \[\n\t\"${INITIAL_CONTACT_POINT}\"\n\]/g" $cfgHome
    else
        sed -i "s/@INITIAL_CONTACT_POINT@//g" $cfgHome
    fi

fi

exec /usr/local/zeebe/bin/broker
