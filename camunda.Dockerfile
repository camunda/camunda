# syntax=docker/dockerfile:1.4
# This Dockerfile requires BuildKit to be enabled, by setting the environment variable
# DOCKER_BUILDKIT=1
# see https://docs.docker.com/build/buildkit/#getting-started

ARG BASE_IMAGE="reg.mini.dev/1212/openjre-base:25-dev"
ARG BASE_DIGEST="sha256:4103bf065c47f43d0d80aebf9258fec411585ed6fd6ed397115092727d6f0171"
ARG JATTACH_VERSION="v2.2"
ARG JATTACH_CHECKSUM_AMD64="acd9e17f15749306be843df392063893e97bfecc5260eef73ee98f06e5cfe02f"
ARG JATTACH_CHECKSUM_ARM64="288ae5ed87ee7fe0e608c06db5a23a096a6217c9878ede53c4e33710bdcaab51"

# If you don't have access to Minimus hardened base images, you can use public
# base images like this instead on your own risk.
# Simply pass `--build-arg BASE=public` in order to build with the Temurin JDK.
ARG BASE_IMAGE_PUBLIC="eclipse-temurin:25.0.4_7-jre-noble"
ARG BASE_DIGEST_PUBLIC="sha256:b573af9e331196fbc42e246da4df24df9b6c556c73e7efddfde0511f1c9508c5"
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
COPY --link . ./
RUN --mount=type=cache,target=/root/.m2,rw \
    ./mvnw -B -am -pl dist package -T1C -D skipChecks -D skipTests && \
    mv dist/target/camunda-zeebe .

### jattach download stage ###
# hadolint ignore=DL3006,DL3007
FROM alpine AS jattach
ARG TARGETARCH
ARG JATTACH_VERSION
ARG JATTACH_CHECKSUM_AMD64
ARG JATTACH_CHECKSUM_ARM64

# --retry-all-errors is what makes the retry apply to a dropped or refused TLS
# connection to github.com. On its own --retry covers a timeout and the HTTP
# 408, 429, 500, 502, 503 and 504 responses, none of which an SSL connect error
# (exit 35) is, so without it the download fails on the first attempt.
# hadolint ignore=DL4006,DL3018
RUN apk add -q --no-cache curl && \
    if [ "${TARGETARCH}" = "amd64" ]; then \
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
FROM eclipse-temurin:25-jdk-noble AS distball

# hadolint ignore=DL3002
USER root
WORKDIR /camunda
SHELL ["/bin/bash", "-o", "pipefail", "-c"]

ARG DISTBALL="dist/target/camunda-zeebe-*.tar.gz"
COPY --link ${DISTBALL} camunda.tar

# tar detects gzip itself, so DISTBALL can be a .tar or a .tar.gz
RUN mkdir camunda-zeebe && \
    tar xfv camunda.tar --strip 1 -C camunda-zeebe

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

WORKDIR ${CAMUNDA_HOME}
EXPOSE 8080 26500 26501 26502

# Switch to root to allow setting up our own user
USER root
RUN addgroup --gid 1001 camunda && \
    adduser -S -G camunda -u 1001 -h ${CAMUNDA_HOME} camunda && \
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

# CVE-2026-114820: Oracle's Security Alert removes the eMudhra roots emSign G1, G2 and ECC G3 and
# Sectigo's AAA Certificate Services from the JDK cacerts, but the bases still ship emSign G1 (and
# on Temurin ECC G3) in ca-certificates. Deselect them in /etc/ca-certificates.conf, so
# update-ca-certificates drops them from the OS bundle and keeps them out when a derived image runs
# it to add its own CA. Temurin's JVM cacerts is separate, so G1 and G3 are also deleted from it by
# fingerprint; on Minimus it is rebuilt from the OS bundle. The build fails if G1, G3 or AAA is left
# in either store or a store looks truncated. emSign C1 and C3 are not part of Oracle's removal.
RUN set -eu; \
    g3=86A1ECBA089C4A8D3BBE2734C612BA341D813E043CF9E8A862CD5C57A36BBE6B; \
    g1=40F6AF0346A99AA1CD1D555A4E9CCE62C7F9634603EE406615833DC8C8D00367; \
    aaa=D7A7A0FB5D7E2731D771E9484EBCDEF71D5F0C3E0A2948782BC83EE0EA699EF4; \
    tmp=$(mktemp -d); \
    chk() { \
      awk -v a="$g3" -v b="$g1" -v c="$aaa" '$1=="SHA256:"{f=$2; gsub(":","",f); n++; if (f==a||f==b||f==c) x=1} END{exit (n>100 && !x ? 0 : 1)}' "$1" \
        || { echo "forbidden root left in $2, or it looks truncated" >&2; exit 1; }; \
    }; \
    sed -i -E 's#^mozilla/(emSign_Root_CA_-_G1|emSign_ECC_Root_CA_-_G3)\.crt$#!&#' /etc/ca-certificates.conf; \
    update-ca-certificates; \
    keytool -list -v -cacerts -storepass changeit > "$tmp/jvm.txt"; \
    for fp in "$g3" "$g1"; do \
      alias=$(awk -v fp="$fp" '/^Alias name: /{a=substr($0,13)} $1=="SHA256:"{f=$2; gsub(":","",f); if (f==fp) print a}' "$tmp/jvm.txt"); \
      if [ -n "$alias" ]; then keytool -delete -cacerts -storepass changeit -alias "$alias"; fi; \
    done; \
    keytool -list -v -cacerts -storepass changeit > "$tmp/jvm.txt"; \
    chk "$tmp/jvm.txt" "JVM cacerts"; \
    keytool -printcert -file /etc/ssl/certs/ca-certificates.crt > "$tmp/os.txt"; \
    chk "$tmp/os.txt" "OS bundle"; \
    rm -rf "$tmp"

COPY --from=jattach --chown=1001:0 /jattach /usr/local/bin/jattach
COPY --link --chown=1001:0 zeebe/docker/utils/jvm.options ${CAMUNDA_HOME}/config/jvm.options
COPY --from=dist --chown=1001:0 /camunda/camunda-zeebe ${CAMUNDA_HOME}
# Install the RocksDB native lib into the default Linux java.library.path entry so
# RocksDB.loadLibrary() resolves it via System.loadLibrary without unpacking the jar.
COPY --from=dist /camunda/rocksdb-lib/ /usr/java/packages/lib/

RUN ln -s /driver-lib ${CAMUNDA_HOME}/driver-lib

USER 1001:1001

ENTRYPOINT ["/usr/local/camunda/bin/camunda"]
