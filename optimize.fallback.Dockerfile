# hadolint global ignore=DL3006
ARG BASE_IMAGE="alpine:3.21.2"
ARG BASE_DIGEST="sha256:56fa17d2a7e7f168a043a2712e63aed1f8543aeafdcee47c58dcffe38ed51099"

# Prepare Optimize Distribution
FROM ${BASE_IMAGE}@${BASE_DIGEST} AS prepare
ARG DISTBALL="dist/target/camunda-zeebe-*.tar.gz"
WORKDIR /tmp/optimize

# download optimize
COPY ${DISTBALL} optimize.tar.gz
RUN tar xzvf optimize.tar.gz --strip 1 && \
    rm optimize.tar.gz
COPY docker-notice.txt notice.txt
RUN sed -i '/^exec /i cat /usr/local/optimize/notice.txt' bin/optimize

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
LABEL org.opencontainers.image.authors="optimize@camunda.com"
LABEL org.opencontainers.image.url="https://docs.camunda.io/docs/components/optimize/what-is-optimize/"
LABEL org.opencontainers.image.documentation="https://docs.camunda.io/docs/self-managed/optimize-deployment/install-and-start/"
LABEL org.opencontainers.image.source="https://github.com/camunda/camunda"
LABEL org.opencontainers.image.version="${VERSION}"
LABEL org.opencontainers.image.revision="${REVISION}"
LABEL org.opencontainers.image.vendor="Camunda Services GmbH"
LABEL org.opencontainers.image.licenses="Proprietary"
LABEL org.opencontainers.image.title="Camunda Optimize"
LABEL org.opencontainers.image.description="Provides business activity monitoring for workflows and uses BPMN-based analysis to uncover process bottlenecks"

# OpenShift labels: https://docs.openshift.com/container-platform/4.10/openshift_images/create-images.html#defining-image-metadata
LABEL io.openshift.tags="bpmn,optimization,camunda"
LABEL io.openshift.wants="zeebe,elasticsearch,identity,keycloak,opensearch"
LABEL io.k8s.description="Provides business activity monitoring for workflows and uses BPMN-based analysis to uncover process bottlenecks"
LABEL io.openshift.non-scalable="false"
LABEL io.openshift.min-memory="2Gi"
LABEL io.openshift.min-cpu="1"

ENV WAIT_FOR=
ENV WAIT_FOR_TIMEOUT=30
ENV TZ=UTC
ENV CONTAINER_HOST=0.0.0.0

EXPOSE 8090 8091

RUN apk update && apk upgrade && \
    apk add --no-cache bash openjdk21-jre tzdata

ENV OPTIMIZE_HOME=/usr/local/optimize

WORKDIR ${OPTIMIZE_HOME}
VOLUME /tmp
VOLUME ${OPTIMIZE_HOME}/logs

RUN addgroup --gid 1001 camunda && \
    adduser -D -h ${OPTIMIZE_HOME} -G camunda -u 1001 camunda && \
    apk add --no-cache bash curl tini openjdk21-jre tzdata && \
    apk -U upgrade && \
    curl "https://raw.githubusercontent.com/vishnubob/wait-for-it/master/wait-for-it.sh" --output /usr/local/bin/wait-for-it.sh && \
    chmod +x /usr/local/bin/wait-for-it.sh && \
    # These directories are to be mounted by users, eagerly creating them and setting ownership
    # helps to avoid potential permission issues due to default volume ownership.
    mkdir ${OPTIMIZE_HOME}/logs && \
    chown -R 1001:0 ${OPTIMIZE_HOME} && \
    chmod -R 0775 ${OPTIMIZE_HOME}

COPY --from=prepare --chown=1001:0 --chmod=0775 /tmp/optimize ${OPTIMIZE_HOME}

USER 1001:1001

ENTRYPOINT ["/sbin/tini", "--", "/usr/local/optimize/bin/optimize"]
