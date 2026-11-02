# hadolint global ignore=DL3006
ARG BASE_IMAGE="alpine:3.23.6"
ARG BASE_DIGEST="sha256:85fe1e81d6758c208f3e1eed4338a1997e19d4be002d4dd32d3100c9a8c010a0"

# Prepare tasklist Distribution
FROM ${BASE_IMAGE}@${BASE_DIGEST} AS prepare

ARG DISTBALL="dist/target/camunda-zeebe-*.tar.gz"
WORKDIR /tmp/tasklist

# download tasklist
COPY ${DISTBALL} tasklist.tar.gz
RUN tar xzvf tasklist.tar.gz --strip 1 && \
    rm tasklist.tar.gz

### Base image ###
# hadolint ignore=DL3006
FROM ${BASE_IMAGE}@${BASE_DIGEST} AS base

# Install Tini
RUN apk update && apk add --no-cache tini

### Application Image ###
# hadolint ignore=DL3006

FROM base AS app
# leave unset to use the default value at the top of the file
ARG BASE_IMAGE
ARG BASE_DIGEST
ARG VERSION=""
ARG DATE=""
ARG REVISION=""

# OCI labels: https://github.com/opencontainers/image-spec/blob/main/annotations.md
LABEL org.opencontainers.image.base.name="docker.io/library/${BASE_IMAGE}"
LABEL org.opencontainers.image.base.digest="${BASE_DIGEST}"
LABEL org.opencontainers.image.created="${DATE}"
LABEL org.opencontainers.image.authors="hto@camunda.com"
LABEL org.opencontainers.image.url="https://camunda.com/platform/tasklist/"
LABEL org.opencontainers.image.documentation="https://docs.camunda.io/docs/self-managed/tasklist-deployment/install-and-start/"
LABEL org.opencontainers.image.source="https://github.com/camunda/camunda"
LABEL org.opencontainers.image.version="${VERSION}"
LABEL org.opencontainers.image.revision="${REVISION}"
LABEL org.opencontainers.image.vendor="Camunda Services GmbH"
LABEL org.opencontainers.image.licenses="(Apache-2.0 AND LicenseRef-Camunda-License-1.0)"
LABEL org.opencontainers.image.title="Camunda Tasklist"
LABEL org.opencontainers.image.description="Tasklist is a ready-to-use application to rapidly implement business processes alongside user tasks in Zeebe"

# OpenShift labels: https://docs.openshift.com/container-platform/4.10/openshift_images/create-images.html#defining-image-metadata
LABEL io.openshift.tags="bpmn,tasklist,camunda"
LABEL io.openshift.wants="zeebe,elasticsearch"
LABEL io.openshift.non-scalable="false"
LABEL io.openshift.min-memory="1Gi"
LABEL io.openshift.min-cpu="1"
LABEL io.k8s.description="Tasklist is a ready-to-use application to rapidly implement business processes alongside user tasks in Zeebe"

EXPOSE 8080

RUN apk update && apk upgrade && \
    apk add --no-cache bash openjdk21-jre tzdata gcompat libgcc libc6-compat

# CVE-2026-114820: Oracle's Security Alert removes the eMudhra roots emSign G1, G2 and ECC G3 and
# Sectigo's AAA Certificate Services from the JDK cacerts. Alpine's ca-certificates still ships
# emSign G1, and update-ca-certificates rebuilds both the OS bundle and the JVM cacerts from
# /etc/ca-certificates.conf. Deselect G1 there, so it stays out when a derived image runs
# update-ca-certificates to add its own CA. The build fails if G1, G3 or AAA is in either store.
# emSign C1 and C3 are not part of Oracle's removal and stay.
SHELL ["/bin/bash", "-eo", "pipefail", "-c"]
RUN g3=86A1ECBA089C4A8D3BBE2734C612BA341D813E043CF9E8A862CD5C57A36BBE6B; \
    g1=40F6AF0346A99AA1CD1D555A4E9CCE62C7F9634603EE406615833DC8C8D00367; \
    aaa=D7A7A0FB5D7E2731D771E9484EBCDEF71D5F0C3E0A2948782BC83EE0EA699EF4; \
    sed -i 's|^mozilla/emSign_Root_CA_-_G1\.crt$|!&|' /etc/ca-certificates.conf; \
    update-ca-certificates; \
    fingerprints() { keytool "$@" | awk '$1=="SHA256:"{f=$2; gsub(":","",f); print f}'; }; \
    jvm=$(fingerprints -list -v -cacerts -storepass changeit); \
    os=$(fingerprints -printcert -file /etc/ssl/certs/ca-certificates.crt); \
    [ "$(wc -l <<< "$jvm")" -gt 100 ]; \
    [ "$(wc -l <<< "$os")" -gt 100 ]; \
    ! grep -qxE "$g3|$g1|$aaa" <<< "$jvm"$'\n'"$os"
SHELL ["/bin/sh", "-c"]

ENV TASKLIST_HOME=/usr/local/tasklist

WORKDIR ${TASKLIST_HOME}
VOLUME /tmp
VOLUME ${TASKLIST_HOME}/logs

RUN addgroup --gid 1001 camunda && \
    adduser -D -h ${TASKLIST_HOME} -G camunda -u 1001 camunda && \
    # These directories are to be mounted by users, eagerly creating them and setting ownership
    # helps to avoid potential permission issues due to default volume ownership.
    mkdir ${TASKLIST_HOME}/logs && \
    chown -R 1001:0 ${TASKLIST_HOME} && \
    chmod -R 0775 ${TASKLIST_HOME}

COPY --from=prepare --chown=1001:0 --chmod=0775 /tmp/tasklist ${TASKLIST_HOME}

# rename tasklist-migrate script to migrate (as expected by SaaS)
RUN mv ${TASKLIST_HOME}/bin/tasklist-migrate ${TASKLIST_HOME}/bin/migrate

USER 1001:1001

ENTRYPOINT ["/sbin/tini", "--", "/usr/local/tasklist/bin/tasklist"]
