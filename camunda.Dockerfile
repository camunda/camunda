# syntax=docker/dockerfile:1.4
# This Dockerfile requires BuildKit to be enabled, by setting the environment variable
# DOCKER_BUILDKIT=1
# see https://docs.docker.com/build/buildkit/#getting-started

ARG BASE_IMAGE="reg.mini.dev/1212/openjre-base-compat:21-dev"
ARG BASE_DIGEST="sha256:185eeb5a0420bb96c371bf40b53a573a15855283cd70365d1ea22c18430386ed"

# If you don't have access to Minimus hardened base images, you can use public
# base images like this instead on your own risk.
# Simply pass `--build-arg BASE=public` in order to build with the Temurin JDK.
ARG BASE_IMAGE_PUBLIC="eclipse-temurin:21.0.12_8-jre-noble"
ARG BASE_DIGEST_PUBLIC="sha256:7739f0ffce786528961eea6bf46d9610ee968ac6127c9b2e93494757bdecce9f"
ARG BASE="hardened"

# set to "build" to build camunda from scratch instead of using a distball
ARG DIST="distball"

### Base Application Image ###
# hadolint ignore=DL3006
FROM ${BASE_IMAGE}@${BASE_DIGEST} AS base-hardened

### Base Public Application Image ###
# hadolint ignore=DL3006
FROM ${BASE_IMAGE_PUBLIC}@${BASE_DIGEST_PUBLIC} AS base-public

### Build camunda from scratch ###
# hadolint ignore=DL3006
FROM base-${BASE} AS build

# hadolint ignore=DL3002
USER root
WORKDIR /camunda
ENV MAVEN_OPTS -XX:MaxRAMPercentage=80
ENV LANG='C.UTF-8' LC_ALL='C.UTF-8'
COPY --link . ./
RUN --mount=type=cache,target=/root/.m2,rw \
    ./mvnw -B -am -pl dist package -T1C -D skipChecks -D skipTests && \
    mv dist/target/camunda-zeebe .

# hadolint ignore=DL3006,DL3007
FROM ${BASE_IMAGE}@${BASE_DIGEST} AS distball

# hadolint ignore=DL3002
USER root
WORKDIR /camunda
ARG DISTBALL="dist/target/camunda-zeebe-*.tar.gz"
COPY --link ${DISTBALL} camunda.tar.gz

RUN mkdir camunda-zeebe && \
    tar xfvz camunda.tar.gz --strip 1 -C camunda-zeebe

### Image containing the camunda distribution ###
# hadolint ignore=DL3006
FROM ${DIST} AS dist

### Application Image ###
# https://docs.docker.com/engine/reference/builder/#automatic-platform-args-in-the-global-scope
# hadolint ignore=DL3006
FROM base-${BASE} AS app
# leave unset to use the default value at the top of the file
ARG BASE_IMAGE
ARG BASE_DIGEST
ARG VERSION=""
ARG DATE=""
ARG REVISION=""

# OCI labels: https://github.com/opencontainers/image-spec/blob/main/annotations.md
LABEL org.opencontainers.image.base.digest="${BASE_DIGEST}"
LABEL org.opencontainers.image.base.name="${BASE_IMAGE}"
LABEL org.opencontainers.image.created="${DATE}"
LABEL org.opencontainers.image.authors="community@camunda.com"
LABEL org.opencontainers.image.url="https://camunda.com/platform/"
LABEL org.opencontainers.image.documentation="https://docs.camunda.io/docs/self-managed/about-self-managed/"
LABEL org.opencontainers.image.source="https://github.com/camunda/camunda"
LABEL org.opencontainers.image.version="${VERSION}"
# According to https://github.com/opencontainers/image-spec/blob/main/annotations.md#pre-defined-annotation-keys
# and given we set the base.name and base.digest, we reference the manifest of the base image here
LABEL org.opencontainers.image.ref.name="${BASE_IMAGE}"
LABEL org.opencontainers.image.revision="${REVISION}"
LABEL org.opencontainers.image.vendor="Camunda Services GmbH"
LABEL org.opencontainers.image.licenses="(Apache-2.0 AND LicenseRef-Camunda-License-1.0)"
LABEL org.opencontainers.image.title="Camunda Platform"
LABEL org.opencontainers.image.description="Camunda platform: the universal process orchestrator"

# OpenShift labels: https://docs.openshift.com/container-platform/4.10/openshift_images/create-images.html#defining-image-metadata
LABEL io.openshift.tags="bpmn,orchestration,workflow,operate,tasklist"
LABEL io.k8s.description="Camunda platform: the universal process orchestrator"
LABEL io.openshift.non-scalable="false"
LABEL io.openshift.min-memory="512Mi"
LABEL io.openshift.min-cpu="1"
LABEL io.openshift.wants="elasticsearch"

ENV CAMUNDA_HOME=/usr/local/camunda
ENV PATH "${CAMUNDA_HOME}/bin:${PATH}"
ENV LANG='C.UTF-8' LC_ALL='C.UTF-8'
# Disable RocksDB runtime check for musl, which launches `ldd` as a shell process
# We know there's no need to check for musl on this image
ENV ROCKSDB_MUSL_LIBC=false

WORKDIR ${CAMUNDA_HOME}
EXPOSE 8080 26500 26501 26502
VOLUME /tmp
VOLUME ${CAMUNDA_HOME}/data
VOLUME ${CAMUNDA_HOME}/logs

# Switch to root to allow setting up our own user
USER root
RUN addgroup --gid 1001 camunda && \
    adduser -S -G camunda -u 1001 -h ${CAMUNDA_HOME} camunda && \
    chmod g=u /etc/passwd && \
    # These directories are to be mounted by users, eagerly creating them and setting ownership
    # helps to avoid potential permission issues due to default volume ownership.
    mkdir ${CAMUNDA_HOME}/data && \
    mkdir ${CAMUNDA_HOME}/logs && \
    chown -R 1001:0 ${CAMUNDA_HOME} && \
    chmod -R 0775 ${CAMUNDA_HOME}

# CVE-2026-114820: Oracle's Security Alert removes the eMudhra roots emSign G1, G2 and ECC G3 and
# Sectigo's AAA Certificate Services from the JDK cacerts. Most bases still ship some of them in
# ca-certificates. Deselect them in /etc/ca-certificates.conf so update-ca-certificates keeps them
# out of the OS bundle (and out of the JVM cacerts where that is rebuilt from it) even when a derived
# image runs update-ca-certificates to add its own CA. Temurin keeps a separate JVM cacerts, so also
# delete the roots from it by fingerprint. The build fails if G1, G3 or AAA is left in either store.
# emSign C1 and C3 are not part of Oracle's removal and stay. POSIX sh without pipes: plain Alpine
# has no bash, and a failing "! pipeline" would not stop "set -e". A base without
# update-ca-certificates and keytool is left as it is.
RUN set -eu; \
    g3=86A1ECBA089C4A8D3BBE2734C612BA341D813E043CF9E8A862CD5C57A36BBE6B; \
    g1=40F6AF0346A99AA1CD1D555A4E9CCE62C7F9634603EE406615833DC8C8D00367; \
    aaa=D7A7A0FB5D7E2731D771E9484EBCDEF71D5F0C3E0A2948782BC83EE0EA699EF4; \
    if command -v update-ca-certificates >/dev/null 2>&1; then \
      sed -i -E 's#^mozilla/(emSign_Root_CA_-_G[12]|emSign_ECC_Root_CA_-_G3|AAA_Certificate_Services)\.crt$#!&#' /etc/ca-certificates.conf; \
      update-ca-certificates; \
    fi; \
    for pem in emSign_Root_CA_-_G1 emSign_Root_CA_-_G2 emSign_ECC_Root_CA_-_G3 AAA_Certificate_Services; do \
      [ ! -e "/etc/ssl/certs/$pem.pem" ]; \
    done; \
    if command -v keytool >/dev/null 2>&1; then \
      tmp=$(mktemp -d); \
      fingerprints() { awk '/^Alias name: /{a=substr($0,13)} $1=="SHA256:"{f=$2; gsub(":","",f); print f"\t"a}' "$1"; }; \
      keytool -list -v -cacerts -storepass changeit > "$tmp/jvm.txt"; \
      fingerprints "$tmp/jvm.txt" > "$tmp/jvm.fp"; \
      for fp in "$g3" "$g1" "$aaa"; do \
        alias=$(awk -F'\t' -v fp="$fp" '$1==fp{print $2}' "$tmp/jvm.fp"); \
        if [ -n "$alias" ]; then keytool -delete -cacerts -storepass changeit -alias "$alias"; fi; \
      done; \
      keytool -list -v -cacerts -storepass changeit > "$tmp/jvm.txt"; \
      fingerprints "$tmp/jvm.txt" > "$tmp/jvm.fp"; \
      [ "$(wc -l < "$tmp/jvm.fp")" -gt 100 ]; \
      if awk -F'\t' -v a="$g3" -v b="$g1" -v c="$aaa" '$1==a||$1==b||$1==c{f=1} END{exit (f ? 0 : 1)}' "$tmp/jvm.fp"; then \
        echo "forbidden root left in JVM cacerts" >&2; exit 1; \
      fi; \
      if [ -f /etc/ssl/certs/ca-certificates.crt ]; then \
        keytool -printcert -file /etc/ssl/certs/ca-certificates.crt > "$tmp/os.txt"; \
        awk '$1=="SHA256:"{f=$2; gsub(":","",f); print f}' "$tmp/os.txt" > "$tmp/os.fp"; \
        [ "$(wc -l < "$tmp/os.fp")" -gt 100 ]; \
        if awk -v a="$g3" -v b="$g1" -v c="$aaa" '$1==a||$1==b||$1==c{f=1} END{exit (f ? 0 : 1)}' "$tmp/os.fp"; then \
          echo "forbidden root left in OS bundle" >&2; exit 1; \
        fi; \
      fi; \
      rm -rf "$tmp"; \
    fi

COPY --from=dist --chown=1001:0 /camunda/camunda-zeebe ${CAMUNDA_HOME}

USER 1001:1001

ENTRYPOINT ["tini", "--", "/usr/local/camunda/bin/camunda"]
