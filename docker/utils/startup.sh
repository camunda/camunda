#!/bin/bash -xeu

# legacy support
# This environment variable was used to set the gatewway cluster host in standalone and embedded mode.
# Now, there are two dedicated environment variables for the two different deployment scenarios.
export ZEEBE_HOST=${ZEEBE_HOST:-$(hostname -i)}
# Legacy support

if [ "$ZEEBE_STANDALONE_GATEWAY" = "true" ]; then
    export ZEEBEGATEWAY_CLUSTER_HOST=${ZEEBEGATEWAY_CLUSTER_HOST:-${ZEEBE_HOST}}

    exec /usr/local/zeebe/bin/gateway
else
    export ZEEBEBROKER_NETWORK_HOST=${ZEEBEBROKER_NETWORK_HOST:-${ZEEBE_HOST}}
    export ZEEBEBROKER_GATEWAY_CLUSTER_HOST=${ZEEBEBROKER_GATEWAY_CLUSTER_HOST:-${ZEEBEBROKER_NETWORK_HOST}}

    exec /usr/local/zeebe/bin/broker
fi
