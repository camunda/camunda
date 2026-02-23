# syntax=docker/dockerfile:1.4
# This Dockerfile requires BuildKit to be enabled, by setting the environment variable
# DOCKER_BUILDKIT=1
# see https://docs.docker.com/build/buildkit/#getting-started

ARG BASE_IMAGE="reg.mini.dev/1212/openjre-base:21-dev"
ARG BASE_DIGEST="sha256:1950a722f67e3eb68bcb22e8f830440882d4701bf451e872a596594185661824"
ARG JATTACH_VERSION="v2.2"
ARG JATTACH_CHECKSUM_AMD64="acd9e17f15749306be843df392063893e97bfecc5260eef73ee98f06e5cfe02f"
ARG JATTACH_CHECKSUM_ARM64="288ae5ed87ee7fe0e608c06db5a23a096a6217c9878ede53c4e33710bdcaab51"

# If you don't have access to Minimus hardened base images, you can use public
# base images like this instead on your own risk.
# Simply pass `--build-arg BASE=public` in order to build with the Temurin JDK.
ARG BASE_IMAGE_PUBLIC="eclipse-temurin:21.0.10_7-jre-noble"
ARG BASE_DIGEST_PUBLIC="sha256:d79823d08f42c77af16fd656d4ddeaeaac75804238a488a400675d42bf47c88e"
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

# hadolint ignore=DL4006,DL3018
RUN --mount=type=cache,target=/root/.jattach,rw \
    apk add -q --no-cache curl 2>/dev/null && \
    if [ "${TARGETARCH}" = "amd64" ]; then \
      BINARY="linux-x64"; \
      CHECKSUM="${JATTACH_CHECKSUM_AMD64}"; \
    else  \
      BINARY="linux-arm64"; \
      CHECKSUM="${JATTACH_CHECKSUM_ARM64}"; \
    fi && \
    curl -sL "https://github.com/jattach/jattach/releases/download/${JATTACH_VERSION}/jattach-${BINARY}.tgz" -o jattach.tgz && \
    echo "${CHECKSUM} jattach.tgz" | sha256sum -c && \
    tar -xzf "jattach.tgz" && \
    chmod +x jattach && \
    mv jattach /jattach

### Extract camunda from distball ###
# hadolint ignore=DL3006,DL3007
FROM ubuntu:noble AS distball

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
ENV PATH="${CAMUNDA_HOME}/bin:${PATH}"
# Disable RocksDB runtime check for musl, which launches `ldd` as a shell process
# We know there's no need to check for musl on this image
ENV ROCKSDB_MUSL_LIBC=false

WORKDIR ${CAMUNDA_HOME}
EXPOSE 8080 26500 26501 26502
VOLUME /tmp
VOLUME ${CAMUNDA_HOME}/data
VOLUME ${CAMUNDA_HOME}/logs
VOLUME ${CAMUNDA_HOME}/documents
VOLUME /driver-lib

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

COPY --from=jattach --chown=1001:0 /jattach /usr/local/bin/jattach
COPY --from=dist --chown=1001:0 /camunda/camunda-zeebe ${CAMUNDA_HOME}

RUN ln -s /driver-lib ${CAMUNDA_HOME}/driver-lib

USER 1001:1001

ENTRYPOINT ["/usr/local/camunda/bin/camunda"]
