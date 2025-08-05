# syntax=docker/dockerfile:1.4
# This Dockerfile requires BuildKit to be enabled, by setting the environment variable
# DOCKER_BUILDKIT=1
# see https://docs.docker.com/build/buildkit/#getting-started
# Both ubuntu and eclipse-temurin are pinned via digest and not by a strict version tag, as Renovate
# has trouble with custom versioning schemes
ARG BASE_IMAGE="ubuntu:noble"
ARG BASE_DIGEST="sha256:a08e551cb33850e4740772b38217fc1796a66da2506d312abe51acda354ff061"
ARG JDK_IMAGE="eclipse-temurin:21-jdk-noble"
ARG JDK_DIGEST="sha256:c04e695e59a97337e87d55ebbe9f527aacec1504b78ffac2730475057a8ea465"

# set to "build" to build camunda from scratch instead of using a distball
ARG DIST="distball"

### Base image ###
# All package installation, updates, etc., anything with APT should be done here in a single step
# hadolint ignore=DL3006
FROM ${BASE_IMAGE}@${BASE_DIGEST} AS base
WORKDIR /

# Use custom APT timeout and retry values for more resilient builds
COPY .github/actions/build-platform-docker/99apt-timeout-and-retries /etc/apt/apt.conf.d/

# Upgrade all outdated packages and install missing ones (e.g. locales, tini)
# This breaks reproducibility of builds, but is acceptable to gain access to security patches faster
# than the base image releases
# FYI, installing packages via APT also updates the dpkg files, which are few MBs, but removing or
# caching them could break stuff (like not knowing the package is present) or container scanners
# hadolint ignore=DL3008
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    --mount=type=cache,target=/var/log/apt,sharing=locked \
    apt-get -qq update && \
    apt-get install -yqq --no-install-recommends tini ca-certificates systemd perl && \
    apt-get upgrade -yqq --no-install-recommends systemd perl

### Build custom JRE using the base JDK image
# hadolint ignore=DL3006
FROM ${JDK_IMAGE}@${JDK_DIGEST} AS jre-build

# Build a custom JRE which will strip down and compress modules to end up with a smaller Java \
# distribution than the official JRE. This will also include useful debugging tools like
# jcmd, jmod, jps, etc., which take little to no space. Anecdotally, compressing modules add around
# 10ms to the start up time, which is negligible considering our application takes ~10s to start up.
# See https://adoptium.net/blog/2021/10/jlink-to-produce-own-runtime/
# hadolint ignore=DL3018
# required to compile a JRE on ARM64
# see https://github.com/openzipkin/docker-java/issues/34
ENV JAVA_TOOL_OPTIONS "-Djdk.lang.Process.launchMechanism=vfork"
RUN jlink \
     --add-modules ALL-MODULE-PATH \
     --strip-debug \
     --no-man-pages \
     --no-header-files \
     --compress=2 \
     --output /jre && \
   rm -rf /jre/lib/src.zip

### Java base image
FROM base AS java
WORKDIR /

# Inherit from previous build stage
ARG JAVA_HOME=/opt/java/openjdk

# Default to UTF-8 file encoding
ENV LANG='C.UTF-8' LC_ALL='C.UTF-8'

# Setup JAVA_HOME and binaries in the path
ENV JAVA_HOME ${JAVA_HOME}
ENV PATH $JAVA_HOME/bin:$PATH

# Copy JRE from previous build stage
COPY --from=jre-build /jre ${JAVA_HOME}

# https://github.com/docker-library/openjdk/issues/212#issuecomment-420979840
# https://openjdk.java.net/jeps/341
# TL;DR generate some class data sharing for faster load time
RUN java -Xshare:dump;

### Build camunda from scratch ###
FROM java AS build
WORKDIR /camunda
ENV MAVEN_OPTS -XX:MaxRAMPercentage=80
COPY --link . ./
RUN --mount=type=cache,target=/root/.m2,rw \
    ./mvnw -B -am -pl dist package -T1C -D skipChecks -D skipTests && \
    mv dist/target/camunda-zeebe .

### Extract camunda from distball ###
# hadolint ignore=DL3006
FROM base AS distball
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
FROM java AS app
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
# Disable RocksDB runtime check for musl, which launches `ldd` as a shell process
# We know there's no need to check for musl on this image
ENV ROCKSDB_MUSL_LIBC=false

WORKDIR ${CAMUNDA_HOME}
EXPOSE 8080 26500 26501 26502
VOLUME /tmp
VOLUME ${CAMUNDA_HOME}/data
VOLUME ${CAMUNDA_HOME}/logs

RUN groupadd --gid 1001 camunda && \
    useradd --system --gid 1001 --uid 1001 --home ${CAMUNDA_HOME} camunda && \
    chmod g=u /etc/passwd && \
    # These directories are to be mounted by users, eagerly creating them and setting ownership
    # helps to avoid potential permission issues due to default volume ownership.
    mkdir ${CAMUNDA_HOME}/data && \
    mkdir ${CAMUNDA_HOME}/logs && \
    chown -R 1001:0 ${CAMUNDA_HOME} && \
    chmod -R 0775 ${CAMUNDA_HOME}

COPY --from=dist --chown=1001:0 /camunda/camunda-zeebe ${CAMUNDA_HOME}

USER 1001:1001

ENTRYPOINT ["tini", "--", "/usr/local/camunda/bin/camunda"]
