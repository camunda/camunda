
# This Dockerfile requires BuildKit to be enabled, by setting the environment variable
# DOCKER_BUILDKIT=1
# see https://docs.docker.com/build/buildkit/#getting-started

# Both ubuntu and eclipse-temurin are pinned via digest and not by a strict version tag, as Renovate
# has trouble with custom versioning schemes
#ARG BASE_IMAGE="camundaservicesgmbhdhi/dhi-eclipse-temurin:21-debian12"
#ARG BASE_DIGEST="sha256:?"
ARG JDK_IMAGE=""

# set to "build" to build camunda from scratch instead of using a distball
ARG DIST="distball"


### Build camunda from scratch ###
FROM camundaservicesgmbhdhi/dhi-eclipse-temurin:21-jdk-debian12-dev AS build
WORKDIR /camunda
ENV MAVEN_OPTS="-XX:MaxRAMPercentage=80"
COPY --link . ./
RUN --mount=type=cache,target=/root/.m2,rw \
    ./mvnw -B -am -pl dist package -T1C -D skipChecks -D skipTests && \
    mv dist/target/camunda-zeebe .


### Extract camunda from distball ###
# hadolint ignore=DL3006
FROM ubuntu:noble@sha256:a08e551cb33850e4740772b38217fc1796a66da2506d312abe51acda354ff061 AS distball
WORKDIR /camunda
ARG DISTBALL="dist/target/camunda-zeebe-*.tar.gz"
COPY --link ${DISTBALL} camunda.tar.gz

RUN mkdir camunda-zeebe && \
    tar xfvz camunda.tar.gz --strip 1 -C camunda-zeebe


FROM camundaservicesgmbhdhi/dhi-eclipse-temurin:21-jdk-debian12-dev AS setup
RUN groupadd --gid 1001 camunda
ENV CAMUNDA_HOME=/usr/local/camunda
RUN useradd --system --gid 1001 --uid 1001 --home ${CAMUNDA_HOME} camunda
RUN chmod g=u /etc/passwd
RUN mkdir -p ${CAMUNDA_HOME}/data
RUN mkdir ${CAMUNDA_HOME}/logs
RUN chown -R 1001:0 ${CAMUNDA_HOME}
RUN chmod -R 0775 ${CAMUNDA_HOME}


### Image containing the camunda distribution ###
# hadolint ignore=DL3006
FROM ${DIST} AS dist


### Application Image ###
# https://docs.docker.com/engine/reference/builder/#automatic-platform-args-in-the-global-scope
# hadolint ignore=DL3006
#FROM ${BASE_IMAGE}@${BASE_DIGEST} AS app
FROM camundaservicesgmbhdhi/dhi-eclipse-temurin:21-debian12 AS app
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
ENV PATH="${CAMUNDA_HOME}/bin:${PATH}"
# Disable RocksDB runtime check for musl, which launches `ldd` as a shell process
# We know there's no need to check for musl on this image
ENV ROCKSDB_MUSL_LIBC=false

USER 1001:1001

WORKDIR ${CAMUNDA_HOME}

EXPOSE 8080 26500 26501 26502

VOLUME /tmp
VOLUME ${CAMUNDA_HOME}/data
VOLUME ${CAMUNDA_HOME}/logs

COPY --from=setup /etc/passwd /etc/passwd
COPY --from=setup /etc/group /etc/group
COPY --from=setup ${CAMUNDA_HOME} ${CAMUNDA_HOME}
COPY --from=dist --chown=1001:0 /camunda/camunda-zeebe ${CAMUNDA_HOME}


ENTRYPOINT ["tini", "--", "/usr/local/camunda/bin/camunda"]
