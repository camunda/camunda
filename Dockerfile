# syntax=docker/dockerfile:1.4
# This Dockerfile requires BuildKit to be enabled, by setting the environment variable
# DOCKER_BUILDKIT=1
# see https://docs.docker.com/build/buildkit/#getting-started
ARG BASE_IMAGE="alpine:3.18.2"
ARG BASE_DIGEST_AMD64="sha256:25fad2a32ad1f6f510e528448ae1ec69a28ef81916a004d3629874104f8a7f70"
ARG BASE_DIGEST_ARM64="sha256:e3bd82196e98898cae9fe7fbfd6e2436530485974dc4fb3b7ddb69134eda2407"
ARG JDK_IMAGE="azul/zulu-openjdk-alpine:17.0.7-17.42.19"
ARG JDK_DIGEST_AMD64="sha256:4158692ebc1753d32c7e14b5bb8b96efec8d645c00fbc9341738d85320f5b2de"
ARG JDK_DIGEST_ARM64="sha256:525168b306464ce0f9e64b85829a1afcad9fe11588bafa61ed0850642a5d6140"

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

### AMD64 JDK image ###
# JDK_DIGEST_AMD64 is defined at the top of the Dockerfile
# hadolint ignore=DL3006
FROM ${JDK_IMAGE}@${JDK_DIGEST_AMD64} as jdk-amd64

### ARM64 JDK image ##
# JDK_DIGEST_ARM64 is defined at the top of the Dockerfile
# hadolint ignore=DL3006
FROM ${JDK_IMAGE}@${JDK_DIGEST_ARM64} as jdk-arm64

### Build custom JRE using the base JDK image
# hadolint ignore=DL3006
FROM jdk-${TARGETARCH} as jre-build

# Build a custom JRE which will strip down and compress modules to end up with a smaller Java \
# distribution than the official Zulu JRE. This JRE will also include useful debugging tools like
# jcmd, jmod, jps, etc., which take little to no space. Anecdotally, compressing modules add around
# 10ms to the start up time, which is negligible considering our application takes ~10s to start up.
# See https://adoptium.net/blog/2021/10/jlink-to-produce-own-runtime/
# hadolint ignore=DL3018
RUN --mount=type=cache,target=/var/cache/apk,id=apk-jre,sharing=shared \
   apk update && apk add binutils && \
   jlink \
     --add-modules ALL-MODULE-PATH \
     --strip-debug \
     --no-man-pages \
     --no-header-files \
     --compress=2 \
     --output /jre && \
   rm -rf /jre/lib/src.zip

### Java base ###
# hadolint ignore=DL3006
FROM base-${TARGETARCH} as java
WORKDIR /

# Inherit from previous build stage
ARG JAVA_HOME=/opt/java/openjdk

# Default to UTF-8 file.encoding
ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en' LC_ALL='en_US.UTF-8'

# Setup JAVA_HOME and binaries in the path
ENV JAVA_HOME ${JAVA_HOME}
ENV PATH $JAVA_HOME/bin:$PATH

# Install missing dependencies and update existing ones to fix any pending security issues
# hadolint ignore=DL3018
RUN --mount=type=cache,target=/var/cache/apk,id=apk-java,sharing=shared \
    ln -s /var/cache/apk /etc/apk/cache && \
    apk update && apk upgrade && \
    apk add java-common java-cacerts musl musl-locales musl-locales-lang tzdata zlib

# Copy JRE from previous build stage
COPY --from=jre-build /jre ${JAVA_HOME}

### Extract zeebe from distball ###
# hadolint ignore=DL3006
FROM base-${TARGETARCH} as distball
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
FROM bellsoft/liberica-openjdk-alpine:latest as build
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
FROM bellsoft/liberica-openjre-alpine-musl:17.0.7-7 as app

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

# Install tini and RocksDB dependencies
# Additionally, install dependencies required to make sure the image works out of the box with the
# official Helm chart and SaaS controller
# hadolint ignore=DL3018
RUN --mount=type=cache,target=/var/cache/apk,id=apk-app,sharing=shared \
    apk update && apk add tini libstdc++ libgcc bash

COPY --link --chown=1000:0 docker/utils/startup.sh /usr/local/bin/startup.sh
COPY --from=dist --chown=1000:0 /zeebe/camunda-zeebe ${ZB_HOME}

ENTRYPOINT ["tini", "--", "/usr/local/bin/startup.sh"]
