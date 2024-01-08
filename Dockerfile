# hadolint global ignore=DL3006
ARG BASE_IMAGE="alpine:3.19.0"
ARG BASE_DIGEST_AMD64="sha256:13b7e62e8df80264dbb747995705a986aa530415763a6c58f84a3ca8af9a5bcd"
ARG BASE_DIGEST_ARM64="sha256:a70bcfbd89c9620d4085f6bc2a3e2eef32e8f3cdf5a90e35a1f95dcbd7f71548"

# Prepare Operate Distribution
FROM ${BASE_IMAGE} as prepare

WORKDIR /tmp/operate

# download operate
COPY distro/target/camunda-operate-*.tar.gz operate.tar.gz
RUN tar xzvf operate.tar.gz --strip 1 && \
    rm operate.tar.gz
COPY docker-notice.txt notice.txt
RUN sed -i '/^exec /i cat /usr/local/operate/notice.txt' bin/operate

### AMD64 base image ###
# BASE_DIGEST_AMD64 is defined at the top of the Dockerfile
# hadolint ignore=DL3006
FROM ${BASE_IMAGE}@${BASE_DIGEST_AMD64} as base-amd64
ARG BASE_DIGEST_AMD64
ARG BASE_DIGEST="${BASE_DIGEST_AMD64}"

# Install Tini for amd64
RUN apk update && apk add --no-cache tini

### ARM64 base image ##
# BASE_DIGEST_ARM64 is defined at the top of the Dockerfile
# hadolint ignore=DL3006
FROM ${BASE_IMAGE}@${BASE_DIGEST_ARM64} as base-arm64
ARG BASE_DIGEST_ARM64
ARG BASE_DIGEST="${BASE_DIGEST_ARM64}"

# Install Tini for arm64
RUN apk update && apk add --no-cache tini

### Application Image ###
# TARGETARCH is provided by buildkit
# https://docs.docker.com/engine/reference/builder/#automatic-platform-args-in-the-global-scope
# hadolint ignore=DL3006

FROM base-${TARGETARCH} as app
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
LABEL org.opencontainers.image.licenses="Proprietary"
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
RUN apk add --no-cache bash openjdk17-jre tzdata

WORKDIR /usr/local/operate
VOLUME /tmp

COPY --from=prepare /tmp/operate /usr/local/operate

RUN addgroup --gid 1001 camunda && adduser -D -h /usr/local/operate -G camunda -u 1001 camunda
USER 1001:1001

ENTRYPOINT ["/sbin/tini", "--", "/usr/local/operate/bin/operate"]
