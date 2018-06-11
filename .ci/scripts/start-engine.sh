#!/bin/sh -eux

VERSION=${CAMBPM_VERSION:-7.10.0}
SNAPSHOT=${SNAPSHOT:-true}
DISTRO=tomcat

POSTGRESQL_DRIVER_VERSION=42.2.1
INSTALL_HOME=./backend/target/performance-engine-${VERSION}
CAMUNDA_HOME=${INSTALL_HOME}/camunda

# Determine nexus URL parameters
echo "Downloading Camunda ${VERSION} Community Edition for ${DISTRO}"
REPO="camunda-bpm"
ARTIFACT_GROUP="org.camunda.bpm.tomcat"
ARTIFACT="camunda-bpm-${DISTRO}"
ARTIFACT_VERSION="${VERSION}"

# Determine if SNAPSHOT repo and version should be used
if [ ${SNAPSHOT} = "true" ]; then
	REPO="${REPO}-snapshots"
	ARTIFACT_VERSION="${VERSION}-SNAPSHOT"
fi

# setup
rm -rf ${INSTALL_HOME}
mkdir -p ${CAMUNDA_HOME}

# Download distro from nexus
wget -O ${INSTALL_HOME}/camunda.tar.gz "https://app.camunda.com/nexus/service/local/artifact/maven/redirect?r=${REPO}&g=${ARTIFACT_GROUP}&a=${ARTIFACT}&v=${ARTIFACT_VERSION}&p=tar.gz"
tar xzf ${INSTALL_HOME}/camunda.tar.gz -C ${CAMUNDA_HOME} server --strip 2

cp qa/import-performance-tests/src/test/resources/tomcat/* ${CAMUNDA_HOME}/conf/
wget -O ${CAMUNDA_HOME}/lib/postgresql.jar "http://central.maven.org/maven2/org/postgresql/postgresql/${POSTGRESQL_DRIVER_VERSION}/postgresql-${POSTGRESQL_DRIVER_VERSION}.jar"
${CAMUNDA_HOME}/bin/startup.sh