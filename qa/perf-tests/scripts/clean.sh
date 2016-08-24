#!/bin/bash

WORKDIR=${1:?Please provide a work dir}
REMOTE_HOST=${2:?Please provide remote host}
REMOTE_USERNAME=${3:?Please provide remote host}


ssh ${REMOTE_USERNAME}@${REMOTE_HOST} /bin/bash <<-EOF
    rm -rf ${WORKDIR}
EOF
