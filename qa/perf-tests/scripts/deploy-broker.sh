#!/bin/bash

WORKDIR=${1:?Please provide a work dir}
REMOTE_HOST=${2:?Please provide remote host}
REMOTE_USERNAME=${3:?Please provide remote host}

mkdir -p ${WORKDIR}

ssh ${REMOTE_USERNAME}@${REMOTE_HOST} "rm -Rf ${WORKDIR} ; mkdir ${WORKDIR}"

# copy distribution to remote
scp ../../dist/target/tngp-distribution-1.0.0-SNAPSHOT.tar.gz ${REMOTE_USERNAME}@${REMOTE_HOST}:~/${WORKDIR}/tngp-distribution.tar.gz

# extract and start in background
# PID is saved in file
ssh ${REMOTE_USERNAME}@${REMOTE_HOST} /bin/bash <<-EOF
	cd ${WORKDIR}
	mkdir tngp-distribution/
	tar -zxvf tngp-distribution.tar.gz -C tngp-distribution/ --strip-components=1
	cd tngp-distribution/bin
  chmod +x ./broker
  JAVA_OPTS="-XX:+UnlockDiagnosticVMOptions -XX:GuaranteedSafepointInterval=300000" nohup ./broker &> log.txt &
  echo \$! > broker.pid
EOF

sleep 2
