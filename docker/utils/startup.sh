#!/bin/bash -xeu

cfgHome=/usr/local/zeebe/conf/zeebe.cfg.toml
cfgLog=/usr/local/zeebe/conf/log4j2.xml

if [[ "$DEPLOY_ON_KUBERNETES" == "true" ]]; then

    DNSNAME="${HOST}.${DNS}"

    sed -i "s/@DNSNAME@/${DNSNAME}/g" $cfgHome

    if [[ -n $INITIAL_CONTACT_POINT && ! $HOST =~ .*-0$ ]]; then
        sed -i "s/@INITIAL_CONTACT_POINT@/initialContactPoints = \[\n\t\"${INITIAL_CONTACT_POINT}\"\n\]/g" $cfgHome
    else
        sed -i "s/@INITIAL_CONTACT_POINT@//g" $cfgHome
    fi

else

    sed -i "s/@DNSNAME@/$(hostname --ip-address)/g" $cfgHome
    sed -i "s/@INITIAL_CONTACT_POINT@//g" $cfgHome

fi

if [[ "$DEBUG" == "true" ]]; then
    sed -i 's/<Logger name="io.zeebe" level="info"\/>/<Logger name="io.zeebe" level="debug"\/>/g' $cfgLog
fi

exec /usr/local/zeebe/bin/broker
