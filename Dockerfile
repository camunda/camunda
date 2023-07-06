# syntax=docker/dockerfile:1.4
# This Dockerfile requires BuildKit to be enabled, by setting the environment variable
# DOCKER_BUILDKIT=1
# see https://docs.docker.com/build/buildkit/#getting-started
# We use BellSoft's Liberica OpenJDK as base JDK image for two reasons: it provides an Alpine base
# image with glibc already installed and configured (which is noticeably more performant than musl),
# and it is slimmer than their JRE offering (which is completely uncompressed)
ARG BASE_IMAGE="bellsoft/liberica-openjdk-alpine:17.0.7-7"
ARG BASE_DIGEST_AMD64="sha256:1fb565a396ac8c6d4fd0d7796d8e54e78cfddeb36af9773ff3df02b64d64a739"
ARG BASE_DIGEST_ARM64="sha256:5b56222283e24aa19a09594001f1c2a2820c09518120762dfff4f77ec3f64b4c"

# set to "build" to build zeebe from scratch instead of using a distball
ARG DIST="distball"

### AMD64 base image ###
# BASE_DIGEST_AMD64 is defined at the top of the Dockerfile
# hadolint ignore=DL3006
FROM ${BASE_IMAGE}@${BASE_DIGEST_AMD64} as base-amd64
ARG BASE_DIGEST_AMD64
ARG BASE_DIGEST="${BASE_DIGEST_AMD64}"

### ARM64 base image ##
# BASE_DIGEST_ARM64 is defined at the top of the Dockerfile
# hadolint ignore=DL3006
FROM ${BASE_IMAGE}@${BASE_DIGEST_ARM64} as base-arm64
ARG BASE_DIGEST_ARM64
ARG BASE_DIGEST="${BASE_DIGEST_ARM64}"

### Architecture agnostic base image ##
# This is the actual base image for our application. It's split from the app stage to help with
# layering, caching, reuse, and debugging. Use this stage to install any additional native
# dependencies, for example. Since APK is quite slow, we'll centralize everything in a single call
# here.
# hadolint ignore=DL3006
FROM base-${TARGETARCH} as base

# Prepare a base stage once with all the dependencies required; we do this because APK is quite slow
# to run and install, so concentrate all dependencies into a single stage
# hadolint ignore=DL3018
RUN --mount=type=cache,target=/var/cache/apk,id=apk \
   apk update && apk add binutils tini libstdc++ libgcc bash && apk upgrade

### Extract zeebe from distball ###
# hadolint ignore=DL3006
FROM alpine:3.18.2 as distball
WORKDIR /zeebe
ARG DISTBALL="dist/target/camunda-zeebe-*.tar.gz"
COPY --link ${DISTBALL} zeebe.tar.gz
# Remove zbctl from the distribution to reduce CVE related maintenance effort w.r.t to containers
RUN mkdir camunda-zeebe && \
    tar xfvz zeebe.tar.gz --strip 1 -C camunda-zeebe && \
    find . -type f -name 'zbctl*' -delete

### Build zeebe from scratch ###
# We use the latest version here to avoid having to track the Java version multiple times
# hadolint ignore=DL3006,DL3007
FROM base as build
WORKDIR /zeebe
ENV MAVEN_OPTS -XX:MaxRAMPercentage=80
COPY --link . ./
RUN --mount=type=cache,target=/root/.m2,rw ./mvnw -B -am -pl dist package -T1C -D skipChecks -D skipTests
RUN mv dist/target/camunda-zeebe . && find . -type f -name 'zbctl*' -delete

### Image containing the zeebe distribution ###
# This stage is required because we cannot use arguments in the COPY --from statement below, but we
# can in the FROM statement
# hadolint ignore=DL3006
FROM ${DIST} as dist

### Application Image ###
# https://docs.docker.com/engine/reference/builder/#automatic-platform-args-in-the-global-scope
# hadolint ignore=DL3006
FROM base as app

# leave unset to use the default value at the top of the file
ARG BASE_IMAGE
ARG BASE_DIGEST
ARG VERSION=""
ARG DATE=""
ARG REVISION=""

# OCI labels: https://github.com/opencontainers/image-spec/blob/main/annotations.md
LABEL org.opencontainers.image.base.digest="${BASE_DIGEST}"
LABEL org.opencontainers.image.base.name="docker.io/library/${BASE_IMAGE}"
LABEL org.opencontainers.image.created="${DATE}"
LABEL org.opencontainers.image.authors="zeebe@camunda.com"
LABEL org.opencontainers.image.url="https://zeebe.io"
LABEL org.opencontainers.image.documentation="https://docs.camunda.io/docs/self-managed/zeebe-deployment/"
LABEL org.opencontainers.image.source="https://github.com/camunda/zeebe"
LABEL org.opencontainers.image.version="${VERSION}"
# According to https://github.com/opencontainers/image-spec/blob/main/annotations.md#pre-defined-annotation-keys
# and given we set the base.name and base.digest, we reference the manifest of the base image here
LABEL org.opencontainers.image.ref.name="${BASE_IMAGE}"
LABEL org.opencontainers.image.revision="${REVISION}"
LABEL org.opencontainers.image.vendor="Camunda Services GmbH"
LABEL org.opencontainers.image.licenses="(Apache-2.0 AND LicenseRef-Zeebe-Community-1.1)"
LABEL org.opencontainers.image.title="Zeebe"
LABEL org.opencontainers.image.description="Workflow engine for microservice orchestration"

# OpenShift labels: https://docs.openshift.com/container-platform/4.10/openshift_images/create-images.html#defining-image-metadata
LABEL io.openshift.tags="bpmn,orchestration,workflow"
LABEL io.k8s.description="Workflow engine for microservice orchestration"
LABEL io.openshift.non-scalable="false"
LABEL io.openshift.min-memory="512Mi"
LABEL io.openshift.min-cpu="1"

ENV ZB_HOME=/usr/local/zeebe \
    ZEEBE_BROKER_GATEWAY_NETWORK_HOST=0.0.0.0 \
    ZEEBE_STANDALONE_GATEWAY=false \
    ZEEBE_RESTORE=false
ENV PATH "${ZB_HOME}/bin:${PATH}"
# Disable RocksDB runtime check for musl, which launches `ldd` as a shell process
ENV ROCKSDB_MUSL_LIBC=true

WORKDIR ${ZB_HOME}
EXPOSE 26500 26501 26502
VOLUME /tmp
VOLUME ${ZB_HOME}/data
VOLUME ${ZB_HOME}/logs

WORKDIR /zeebe
RUN addgroup -g 1000 zeebe && \
    adduser -u 1000 zeebe --system --ingroup zeebe && \
    chmod g=u /etc/passwd && \
    # These directories are to be mounted by users, eagerly creating them and setting ownership
    # helps to avoid potential permission issues due to default volume ownership.
    mkdir ${ZB_HOME}/data && \
    mkdir ${ZB_HOME}/logs && \
    chown -R 1000:0 ${ZB_HOME} && \
    chmod -R 0775 ${ZB_HOME}

COPY --link --chown=1000:0 docker/utils/startup.sh /usr/local/bin/startup.sh
COPY --from=dist --chown=1000:0 /zeebe/camunda-zeebe ${ZB_HOME}

ENTRYPOINT ["tini", "--", "/usr/local/bin/startup.sh"]
