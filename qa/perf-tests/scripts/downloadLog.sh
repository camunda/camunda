#!/bin/bash

WORKDIR=${1:?Please provide a work dir}
REMOTE_HOST=${2:?Please provide remote host}
REMOTE_USERNAME=${3:?Please provide remote host}

# copy broker log to data dir
scp ${REMOTE_USERNAME}@${REMOTE_HOST}:~/${WORKDIR}/tngp-distribution/bin/log.txt ./data/broker.log

sleep 2
