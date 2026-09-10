# syntax=docker/dockerfile:1.4
# This Dockerfile requires BuildKit to be enabled, by setting the environment variable
# DOCKER_BUILDKIT=1
# see https://docs.docker.com/build/buildkit/#getting-started

# Echo equivalent of Minimus openjre-base:25-dev. Echo's floating tags (:25)
# are the maintained ones and are rebuilt continuously; the dated tags are
# immutable snapshots that go stale. Pin the digest of a floating tag and bump
# it deliberately, rather than pinning a dated tag.
ARG BASE_IMAGE="reg.echohq.com/eclipse-temurin-jre:25"
ARG BASE_DIGEST="sha256:6d8ba575867b286c5c06065605ac408c659a45f71af238ece341e5407abfd36a"
ARG JDK_IMAGE="reg.echohq.com/eclipse-temurin:25.0.4-20260819"
ARG JDK_DIGEST="sha256:a8cc4cb7276f43bd438e921646b9f4c656f87caa7a82928f02a99fe3d33ee2c3"
ARG CURL_IMAGE="reg.echohq.com/curl:8.22.0-20260907"
ARG CURL_DIGEST="sha256:1ea8abbf714a807c32b9ee808bab2fba58d4f016883c3c819ab9d5f462d79fdb"
ARG JATTACH_VERSION="v2.2"
ARG JATTACH_CHECKSUM_AMD64="acd9e17f15749306be843df392063893e97bfecc5260eef73ee98f06e5cfe02f"
ARG JATTACH_CHECKSUM_ARM64="288ae5ed87ee7fe0e608c06db5a23a096a6217c9878ede53c4e33710bdcaab51"

# If you don't have access to Minimus hardened base images, you can use public
# base images like this instead on your own risk.
# Simply pass `--build-arg BASE=public` in order to build with the Temurin JDK.
ARG BASE_IMAGE_PUBLIC="eclipse-temurin:25.0.4_7-jre-noble"
ARG BASE_DIGEST_PUBLIC="sha256:1e80201efc21b839ebc9b448b1f9b16aa5f036dd2885148282fe5dae38684fe1"
# The jattach and distball stages run on every build, including fork PRs that
# cannot authenticate to Echo, so they need public counterparts selected by the
# same BASE switch. curlimages/curl matches Echo's curl image closely enough
# that one RUN body works on both: curl is preinstalled and the default user is
# unprivileged.
ARG JDK_IMAGE_PUBLIC="eclipse-temurin:25-jdk-noble"
ARG JDK_DIGEST_PUBLIC="sha256:264fafc3390db78c93dc51da0109a0d66ad1fb59f7a893f12b7e3df1f15e52da"
ARG CURL_IMAGE_PUBLIC="curlimages/curl:8.18.0"
ARG CURL_DIGEST_PUBLIC="sha256:d94d07ba9e7d6de898b6d96c1a072f6f8266c687af78a74f380087a0addf5d17"
ARG BASE="hardened"

# set to "build" to build camunda from scratch instead of using a distball
ARG DIST="distball"

### Base Application Image ###
# No packages are added on top. Echo's Temurin JRE image already supplies the
# JRE, tzdata and ca-certificates, plus a shell and coreutils - which covers
# everything the appassembler entrypoint calls (sh, env, dirname, expr, ls,
# uname, which; see dist/src/main/scripts/unixBinTemplate).
#
# The four extras Minimus's openjre-base bundled are deliberately NOT carried
# over, because none of them apply to this image:
#   busybox        - only needed for start/health checks on the non-"dev"
#                    openjre flavor (Identity); this base already has a shell.
#   wget           - only used by the Identity healthcheck.
#   netcat-openbsd - only used by wait-for-it.sh in Optimize.
#   tzdata         - required, and already present in this base.
# No camunda service defines a container healthcheck, and the shipped scripts
# shell out to nothing beyond the seven commands listed above.
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
COPY --link . ./
RUN --mount=type=cache,target=/root/.m2,rw \
    ./mvnw -B -am -pl dist package -T1C -D skipChecks -D skipTests && \
    mv dist/target/camunda-zeebe .

### jattach download stage ###
# hadolint ignore=DL3006,DL3007
# hadolint ignore=DL3006
FROM ${CURL_IMAGE}@${CURL_DIGEST} AS jattach-hardened
# hadolint ignore=DL3006
FROM ${CURL_IMAGE_PUBLIC}@${CURL_DIGEST_PUBLIC} AS jattach-public
# hadolint ignore=DL3006
FROM jattach-${BASE} AS jattach
ARG TARGETARCH
ARG JATTACH_VERSION
ARG JATTACH_CHECKSUM_AMD64
ARG JATTACH_CHECKSUM_ARM64

# --retry-all-errors is what makes the retry apply to a dropped or refused TLS
# connection to github.com. On its own --retry covers a timeout and the HTTP
# 408, 429, 500, 502, 503 and 504 responses, none of which an SSL connect error
# (exit 35) is, so without it the download fails on the first attempt.
# Echo's curl image defaults to the unprivileged `curl_user`; this stage writes
# to / so it needs root. Only the jattach binary reaches the final image.
# hadolint ignore=DL3002
USER root
# hadolint ignore=DL4006
RUN if [ "${TARGETARCH}" = "amd64" ]; then \
      BINARY="linux-x64"; \
      CHECKSUM="${JATTACH_CHECKSUM_AMD64}"; \
    else  \
      BINARY="linux-arm64"; \
      CHECKSUM="${JATTACH_CHECKSUM_ARM64}"; \
    fi && \
    curl -fsSL --retry 5 --retry-delay 5 --retry-all-errors \
      "https://github.com/jattach/jattach/releases/download/${JATTACH_VERSION}/jattach-${BINARY}.tgz" \
      -o jattach.tgz && \
    echo "${CHECKSUM} jattach.tgz" | sha256sum -c && \
    tar -xzf "jattach.tgz" && \
    chmod +x jattach && \
    mv jattach /jattach

### Extract camunda from distball ###
# Use eclipse-temurin JDK (not JRE) so `jar` is available for repacking JARs,
# avoiding a runtime dependency on external package servers for (un)zip.
# hadolint ignore=DL3006,DL3007
# hadolint ignore=DL3006
FROM ${JDK_IMAGE}@${JDK_DIGEST} AS distball-hardened
# hadolint ignore=DL3006
FROM ${JDK_IMAGE_PUBLIC}@${JDK_DIGEST_PUBLIC} AS distball-public
# hadolint ignore=DL3006
FROM distball-${BASE} AS distball

# hadolint ignore=DL3002
USER root
WORKDIR /camunda
SHELL ["/bin/bash", "-o", "pipefail", "-c"]

ARG DISTBALL="dist/target/camunda-zeebe-*.tar.gz"
COPY --link ${DISTBALL} camunda.tar.gz

RUN mkdir camunda-zeebe && \
    tar xfvz camunda.tar.gz --strip 1 -C camunda-zeebe

ARG TARGETARCH
# Extract the target-arch RocksDB native lib into a system library dir and strip ALL
# native libs from the JNI jar (~60 MB). At runtime RocksDB.loadLibrary() resolves the
# lib via System.loadLibrary from /usr/java/packages/lib (default java.library.path entry
# on Linux) before ever falling back to unpacking it from the jar.
# hadolint ignore=DL3003
RUN \
    # Map Docker TARGETARCH to the RocksDB native lib filename suffix
    if [ "$TARGETARCH" = "amd64" ]; then \
      ROCKSDB_ARCH="linux64"; \
    elif [ "$TARGETARCH" = "arm64" ]; then \
      ROCKSDB_ARCH="linux-aarch64"; \
    else \
      echo "Unsupported architecture: $TARGETARCH" >&2 && exit 1; \
    fi && \
    # Locate the jar (version-agnostic)
    ROCKSDB_JAR=$(find camunda-zeebe/lib -name 'rocksdbjni-*.jar' | head -1) && \
    [ -n "$ROCKSDB_JAR" ] && [ -f "$ROCKSDB_JAR" ] || { echo "rocksdbjni jar not found under camunda-zeebe/lib" >&2; exit 1; } && \
    SO="librocksdbjni-${ROCKSDB_ARCH}.so" && \
    UNPACK=$(mktemp -d) && \
    ( cd "$UNPACK" && jar xf "$OLDPWD/$ROCKSDB_JAR" ) && \
    [ -f "$UNPACK/$SO" ] || { echo "Native lib $SO not found in jar" >&2; exit 1; } && \
    # Copy the one native lib we need into a dir installed into the final image
    mkdir -p /camunda/rocksdb-lib && \
    cp "$UNPACK/$SO" "/camunda/rocksdb-lib/$SO" && \
    # Drop all native libs and repack; the .so is now served from the system lib dir
    find "$UNPACK" \( -name '*.so' -o -name '*.jnilib' -o -name '*.dll' \) -delete && \
    rm "$ROCKSDB_JAR" && \
    ( cd "$UNPACK" && jar cMf "$OLDPWD/$ROCKSDB_JAR" . ) && \
    rm -rf "$UNPACK"


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
ENV PATH="${CAMUNDA_HOME}/bin:${PATH}"
# Disable RocksDB runtime check for musl, which launches `ldd` as a shell process
# We know there's no need to check for musl on this image
ENV ROCKSDB_MUSL_LIBC=false
# Minimus's openjre-base set this; Debian leaves it unset. OpenSSL's built-in
# default is the same path, but non-JVM clients that read the variable
# explicitly would otherwise see a behaviour change. The Helm chart overrides
# it when global.tls.caBundle is configured.
ENV SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt

WORKDIR ${CAMUNDA_HOME}
EXPOSE 8080 26500 26501 26502

# Switch to root to allow setting up our own user
USER root
RUN groupadd -g 1001 camunda && \
    useradd -g camunda -u 1001 -d ${CAMUNDA_HOME} -s /usr/sbin/nologin camunda && \
    chmod g=u /etc/passwd && \
    # These directories are to be mounted by users, eagerly creating them and setting ownership
    # helps to avoid potential permission issues due to default volume ownership.
    mkdir ${CAMUNDA_HOME}/data && \
    mkdir ${CAMUNDA_HOME}/logs && \
    mkdir ${CAMUNDA_HOME}/documents && \
    chown -R 1001:0 ${CAMUNDA_HOME} && \
    chmod -R 0775 ${CAMUNDA_HOME}

VOLUME /tmp
VOLUME ${CAMUNDA_HOME}/data
VOLUME ${CAMUNDA_HOME}/logs
VOLUME ${CAMUNDA_HOME}/documents
VOLUME /driver-lib

COPY --from=jattach --chown=1001:0 /jattach /usr/local/bin/jattach
COPY --link --chown=1001:0 zeebe/docker/utils/jvm.options ${CAMUNDA_HOME}/config/jvm.options
COPY --from=dist --chown=1001:0 /camunda/camunda-zeebe ${CAMUNDA_HOME}
# Install the RocksDB native lib into the default Linux java.library.path entry so
# RocksDB.loadLibrary() resolves it via System.loadLibrary without unpacking the jar.
COPY --from=dist /camunda/rocksdb-lib/ /usr/java/packages/lib/

RUN ln -s /driver-lib ${CAMUNDA_HOME}/driver-lib

USER 1001:1001

ENTRYPOINT ["/usr/local/camunda/bin/camunda"]
