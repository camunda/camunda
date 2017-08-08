#!/bin/bash

WORKDIR=${1:?Please provide a work dir}
REMOTE_HOST=${2:?Please provide remote host}
REMOTE_USERNAME=${3:?Please provide remote host}

mkdir -p ${WORKDIR}

ssh ${REMOTE_USERNAME}@${REMOTE_HOST} "rm -Rf ${WORKDIR} ; mkdir ${WORKDIR}"

# copy distribution to remote
scp ../../dist/target/zeebe-distribution-0.2.0-SNAPSHOT.tar.gz ${REMOTE_USERNAME}@${REMOTE_HOST}:~/${WORKDIR}/zeebe-distribution.tar.gz

# extract and start in background
# PID is saved in file
ssh ${REMOTE_USERNAME}@${REMOTE_HOST} /bin/bash <<-EOF
	cd ${WORKDIR}
	mkdir zeebe-distribution/
	tar -zxvf zeebe-distribution.tar.gz -C zeebe-distribution/ --strip-components=1
	cd zeebe-distribution/bin
    chmod +x ./broker
    # use external ip for client interface
    sed -i "s/0.0.0.0/${REMOTE_HOST}/g" ../conf/zeebe.cfg.toml
    JAVA_OPTS="-XX:+UnlockDiagnosticVMOptions -XX:GuaranteedSafepointInterval=300000" nohup ./broker &> log.txt &
    echo \$! > broker.pid
EOF

sleep 2
