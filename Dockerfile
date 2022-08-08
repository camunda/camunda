ARG APP_ENV=prod
# Override this based on the architecture; this is currently pointing to amd64
ARG BASE_SHA="fce37e5146419a158c2199c6089fa39b92445fb2e66dc0331f8591891239ea3b"

# Building builder image
FROM ubuntu:focal as builder
ARG DISTBALL

ENV TMP_ARCHIVE=/tmp/zeebe.tar.gz \
    TMP_DIR=/tmp/zeebe

COPY ${DISTBALL} ${TMP_ARCHIVE}

RUN mkdir -p ${TMP_DIR} && \
    tar xfvz ${TMP_ARCHIVE} --strip 1 -C ${TMP_DIR} && \
    # already create volume dir to later have correct rights
    mkdir ${TMP_DIR}/data && \
    apt-get -qq update && \
    apt-get install -y --no-install-recommends tini=0.18.0-1 && \
    cp /usr/bin/tini ${TMP_DIR}/bin/tini

COPY docker/utils/startup.sh ${TMP_DIR}/bin/startup.sh
RUN chmod +x -R ${TMP_DIR}/bin/ && \
    chmod 0775 ${TMP_DIR} ${TMP_DIR}/data

# Building prod image
FROM eclipse-temurin:17-jre-focal@sha256:${BASE_SHA} as prod

# leave unset to use the default value at the top of the file
ARG BASE_SHA

LABEL org.opencontainers.image.base.digest="${BASE_SHA}"
LABEL org.opencontainers.image.base.name="docker.io/library/eclipse-temurin:17-jre-focal"

# Building dev image
FROM eclipse-temurin:17-jdk-focal as dev
SHELL ["/bin/bash", "-o", "pipefail", "-c"]
RUN echo "running DEV pre-install commands" && \
    curl -sSL https://github.com/jvm-profiling-tools/async-profiler/releases/download/v1.7.1/async-profiler-1.7.1-linux-x64.tar.gz | tar xzv

# Building application image
# hadolint ignore=DL3006
FROM ${APP_ENV} as app

ARG VERSION=""
ARG DATE=""
ARG REVISION=""

# OCI labels: https://github.com/opencontainers/image-spec/blob/main/annotations.md
LABEL org.opencontainers.image.created="${DATE}"
LABEL org.opencontainers.image.authors="zeebe@camunda.com"
LABEL org.opencontainers.image.url="https://zeebe.io"
LABEL org.opencontainers.image.documentation="https://docs.camunda.io/docs/self-managed/zeebe-deployment/"
LABEL org.opencontainers.image.source="https://github.com/camunda/zeebe"
LABEL org.opencontainers.image.version="${VERSION}"
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
    ZEEBE_STANDALONE_GATEWAY=false
ENV PATH "${ZB_HOME}/bin:${PATH}"

WORKDIR ${ZB_HOME}
EXPOSE 26500 26501 26502
VOLUME ${ZB_HOME}/data

RUN groupadd -g 1000 zeebe && \
    adduser -u 1000 zeebe --system --ingroup zeebe && \
    chmod g=u /etc/passwd && \
    chown 1000:0 ${ZB_HOME} && \
    chmod 0775 ${ZB_HOME}

COPY --from=builder --chown=1000:0 /tmp/zeebe/bin/startup.sh /usr/local/bin/startup.sh
COPY --from=builder --chown=1000:0 /tmp/zeebe ${ZB_HOME}

ENTRYPOINT ["tini", "--", "/usr/local/bin/startup.sh"]
