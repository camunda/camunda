# hadolint global ignore=DL3006
ARG BASE_IMAGE="alpine:3.23.2"
ARG BASE_DIGEST="sha256:865b95f46d98cf867a156fe4a135ad3fe50d2056aa3f25ed31662dff6da4eb62"

# Prepare Operate Distribution
FROM ${BASE_IMAGE}@${BASE_DIGEST} AS prepare
ARG DISTBALL="dist/target/camunda-zeebe-*.tar.gz"
WORKDIR /tmp/operate

# download operate
COPY ${DISTBALL} operate.tar.gz
RUN tar xzvf operate.tar.gz --strip 1 && \
    rm operate.tar.gz
COPY docker-notice.txt notice.txt
RUN sed -i '/^exec /i cat /usr/local/operate/notice.txt' bin/operate

### Base image ###
# hadolint ignore=DL3006
FROM ${BASE_IMAGE}@${BASE_DIGEST} AS base

# Install Tini
RUN apk update && apk add --no-cache tini

### Application Image ###
# TARGETARCH is provided by buildkit
# https://docs.docker.com/engine/reference/builder/#automatic-platform-args-in-the-global-scope
# hadolint ignore=DL3006

FROM base AS app
# leave unset to use the default value at the top of the file
ARG BASE_IMAGE
ARG BASE_DIGEST
ARG VERSION=""
ARG DATE=""
ARG REVISION=""

# OCI labels: https://github.com/opencontainers/image-spec/blob/main/annotations.md
LABEL org.opencontainers.image.base.name="docker.io/library/${BASE_IMAGE}"
LABEL org.opencontainers.image.base.digest="${BASE_DIGEST}"
LABEL org.opencontainers.image.created="${DATE}"
LABEL org.opencontainers.image.authors="operate@camunda.com"
LABEL org.opencontainers.image.url="https://camunda.com/platform/operate/"
LABEL org.opencontainers.image.documentation="https://docs.camunda.io/docs/self-managed/operate-deployment/install-and-start/"
LABEL org.opencontainers.image.source="https://github.com/camunda/camunda-operate"
LABEL org.opencontainers.image.version="${VERSION}"
LABEL org.opencontainers.image.revision="${REVISION}"
LABEL org.opencontainers.image.vendor="Camunda Services GmbH"
LABEL org.opencontainers.image.licenses="(Apache-2.0 AND LicenseRef-Camunda-License-1.0)"
LABEL org.opencontainers.image.title="Camunda Operate"
LABEL org.opencontainers.image.description="Tool for process observability and troubleshooting processes running in Camunda Platform 8"

# OpenShift labels: https://docs.openshift.com/container-platform/4.10/openshift_images/create-images.html#defining-image-metadata
LABEL io.openshift.tags="bpmn,operate,camunda"
LABEL io.openshift.wants="zeebe,elasticsearch"
LABEL io.openshift.non-scalable="false"
LABEL io.openshift.min-memory="512Mi"
LABEL io.openshift.min-cpu="1"
LABEL io.k8s.description="Tool for process observability and troubleshooting processes running in Camunda Platform 8"

EXPOSE 8080

RUN apk update && apk upgrade
RUN apk add --no-cache bash openjdk21-jre tzdata gcompat libgcc libc6-compat

ENV OPE_HOME=/usr/local/operate

WORKDIR ${OPE_HOME}
VOLUME /tmp
VOLUME ${OPE_HOME}/logs

RUN addgroup --gid 1001 camunda && \
    adduser -D -h ${OPE_HOME} -G camunda -u 1001 camunda && \
    # These directories are to be mounted by users, eagerly creating them and setting ownership
    # helps to avoid potential permission issues due to default volume ownership.
    mkdir ${OPE_HOME}/logs && \
    chown -R 1001:0 ${OPE_HOME} && \
    chmod -R 0775 ${OPE_HOME}

COPY --from=prepare --chown=1001:0 --chmod=0775 /tmp/operate ${OPE_HOME}

USER 1001:1001

ENTRYPOINT ["/sbin/tini", "--", "/usr/local/operate/bin/operate"]
