# syntax=docker/dockerfile:1.4
# This Dockerfile requires BuildKit to be enabled, by setting the environment variable
# DOCKER_BUILDKIT=1
# see https://docs.docker.com/build/buildkit/#getting-started
ARG BASE_DIGEST_AMD64="sha256:b10df4660e02cf944260b13182e4815fc3e577ba510de7f4abccc797e93d9106"
ARG BASE_DIGEST_ARM64="sha256:3cf5c05a6a7e7c387d5ce7bc00842b4788513cebfaf687034e0876153990be0f"

# set to "build" to build zeebe from scratch instead of using a distball
ARG DIST="distball"

### Init image containing tini and the startup script ###
FROM ubuntu:jammy as init
WORKDIR /zeebe
RUN rm /etc/apt/apt.conf.d/docker-clean
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get -qq update && \
    apt-get install -y --no-install-recommends tini=0.19.0-1 && \
    cp /usr/bin/tini .
COPY --link --chown=1000:0 docker/utils/startup.sh .

### Build zeebe from scratch ###
FROM maven:3-eclipse-temurin-17 as build
WORKDIR /zeebe
ENV MAVEN_OPTS -XX:MaxRAMPercentage=80
COPY --link . ./
RUN --mount=type=cache,target=/root/.m2,rw mvn -B -am -pl dist package -T1C -D skipChecks -D skipTests
RUN mv dist/target/camunda-zeebe .

### Extract zeebe from distball ###
FROM ubuntu:jammy as distball
WORKDIR /zeebe
ARG DISTBALL="dist/target/camunda-zeebe-*.tar.gz"
COPY --link ${DISTBALL} zeebe.tar.gz
RUN mkdir camunda-zeebe && tar xfvz zeebe.tar.gz --strip 1 -C camunda-zeebe

### Image containing the zeebe distribution ###
# hadolint ignore=DL3006
FROM ${DIST} as dist

### AMD64 base image ###
# BASE_DIGEST_AMD64 is defined at the top of the Dockerfile
# hadolint ignore=DL3006
FROM eclipse-temurin:17-jre-focal@${BASE_DIGEST_AMD64} as base-amd64
ARG BASE_DIGEST_AMD64
ARG BASE_DIGEST="${BASE_DIGEST_AMD64}"

### ARM64 base image ##
# BASE_DIGEST_ARM64 is defined at the top of the Dockerfile
# hadolint ignore=DL3006
FROM eclipse-temurin:17-jre-focal@${BASE_DIGEST_ARM64} as base-arm64
ARG BASE_DIGEST_ARM64
ARG BASE_DIGEST="${BASE_DIGEST_ARM64}"

### Application Image ###
# TARGETARCH is provided by buildkit
# https://docs.docker.com/engine/reference/builder/#automatic-platform-args-in-the-global-scope
# hadolint ignore=DL3006
FROM base-${TARGETARCH} as app
# leave unset to use the default value at the top of the file
ARG BASE_DIGEST
ARG VERSION=""
ARG DATE=""
ARG REVISION=""

# OCI labels: https://github.com/opencontainers/image-spec/blob/main/annotations.md
LABEL org.opencontainers.image.base.digest="${BASE_DIGEST}"
LABEL org.opencontainers.image.base.name="docker.io/library/eclipse-temurin:17-jre-focal"
LABEL org.opencontainers.image.created="${DATE}"
LABEL org.opencontainers.image.authors="zeebe@camunda.com"
LABEL org.opencontainers.image.url="https://zeebe.io"
LABEL org.opencontainers.image.documentation="https://docs.camunda.io/docs/self-managed/zeebe-deployment/"
LABEL org.opencontainers.image.source="https://github.com/camunda/zeebe"
LABEL org.opencontainers.image.version="${VERSION}"
# According to https://github.com/opencontainers/image-spec/blob/main/annotations.md#pre-defined-annotation-keys
# and given we set the base.name and base.digest, we reference the manifest of the base image here
LABEL org.opencontainers.image.ref.name="eclipse-temurin:17-jre-focal"
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

WORKDIR ${ZB_HOME}
EXPOSE 26500 26501 26502
VOLUME ${ZB_HOME}/data

RUN groupadd -g 1000 zeebe && \
    adduser -u 1000 zeebe --system --ingroup zeebe && \
    chmod g=u /etc/passwd && \
    chown 1000:0 ${ZB_HOME} && \
    chmod 0775 ${ZB_HOME} && \
    mkdir ${ZB_HOME}/data && \
    chmod 0775 ${ZB_HOME}/data

COPY --from=init --chown=1000:0 /zeebe/tini ${ZB_HOME}/bin/
COPY --from=init --chown=1000:0 /zeebe/startup.sh /usr/local/bin/startup.sh
COPY --from=dist --chown=1000:0 /zeebe/camunda-zeebe ${ZB_HOME}

ENTRYPOINT ["tini", "--", "/usr/local/bin/startup.sh"]
