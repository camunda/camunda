ARG BASE_IMAGE_NAME="alpine:3.19.0"
ARG BASE_IMAGE_SHA_AMD64="sha256:d695c3de6fcd8cfe3a6222b0358425d40adfd129a8a47c3416faff1a8aece389"
ARG BASE_IMAGE_SHA_ARM64="sha256:d4ade3639c27579321046d78cc44ec978cea8357d56932611984f601d27e30ac"

# Building prod image amd64
FROM ${BASE_IMAGE_NAME}@${BASE_IMAGE_SHA_AMD64} as prod-amd64

# leave unset to use the default value at the top of the file
ARG BASE_IMAGE_SHA_AMD64
ARG BASE_SHA="${BASE_IMAGE_SHA_AMD64}"

# Building prod image arm64
FROM ${BASE_IMAGE_NAME}@${BASE_IMAGE_SHA_ARM64} as prod-arm64

# leave unset to use the default value at the top of the file
ARG BASE_IMAGE_SHA_ARM64
ARG BASE_SHA="${BASE_IMAGE_SHA_ARM64}"

# Building builder image
# hadolint ignore=DL3006
FROM ${BASE_IMAGE_NAME} as builder

ARG VERSION=""
ARG DISTRO=production
ARG ARTIFACT_PATH=./distro/target

ENV TMP_DIR=/tmp/optimize \
    BUILD_DIR=/tmp/build

RUN mkdir -p ${TMP_DIR} && \
    mkdir -p ${BUILD_DIR}

COPY ${ARTIFACT_PATH}/camunda-optimize-${VERSION}-${DISTRO}.tar.gz ${BUILD_DIR}
RUN tar -xzf ${BUILD_DIR}/camunda-optimize-${VERSION}-${DISTRO}.tar.gz -C ${BUILD_DIR} && \
    rm ${BUILD_DIR}/camunda-optimize-${VERSION}-${DISTRO}.tar.gz
COPY docker/bin/optimize.sh ${BUILD_DIR}/optimize.sh
# Prevent environment-config.yaml from overriding service-config.yaml since the
# service-config.yaml allows usage of OPTIMIZE_ environment variables
RUN rm ${BUILD_DIR}/config/environment-config.yaml

##### FINAL IMAGE #####
# The value of TARGETARCH is provided by the build command from docker and based on that value, prod-amd64 or
# prod-arm64 will be built as defined above
# hadolint ignore=DL3006
FROM prod-${TARGETARCH}

ARG VERSION=""
ARG DATE=""
ARG REVISION=""

# leave the values below unset to use the default value at the top of the file
ARG BASE_IMAGE_NAME
ARG BASE_SHA

# OCI labels: https://github.com/opencontainers/image-spec/blob/main/annotations.md
LABEL org.opencontainers.image.base.name="docker.io/library/${BASE_IMAGE_NAME}"
LABEL org.opencontainers.image.base.digest="${BASE_SHA}"
LABEL org.opencontainers.image.created="${DATE}"
LABEL org.opencontainers.image.authors="optimize@camunda.com"
LABEL org.opencontainers.image.url="https://docs.camunda.io/docs/components/optimize/what-is-optimize/"
LABEL org.opencontainers.image.documentation="https://docs.camunda.io/docs/self-managed/optimize-deployment/install-and-start/"
LABEL org.opencontainers.image.source="https://github.com/camunda/camunda-optimize"
LABEL org.opencontainers.image.version="${VERSION}"
LABEL org.opencontainers.image.revision="${REVISION}"
LABEL org.opencontainers.image.vendor="Camunda Services GmbH"
LABEL org.opencontainers.image.licenses="Proprietary"
LABEL org.opencontainers.image.title="Camunda Optimize"
LABEL org.opencontainers.image.description="Provides business activity monitoring for workflows and uses BPMN-based analysis to uncover process bottlenecks"

# OpenShift labels: https://docs.openshift.com/container-platform/4.10/openshift_images/create-images.html#defining-image-metadata
LABEL io.openshift.tags="bpmn,optimization,camunda"
LABEL io.openshift.wants="zeebe,elasticsearch,identity,keycloak"
LABEL io.k8s.description="Provides business activity monitoring for workflows and uses BPMN-based analysis to uncover process bottlenecks"
LABEL io.openshift.non-scalable="false"
LABEL io.openshift.min-memory="2Gi"
LABEL io.openshift.min-cpu="1"

ENV WAIT_FOR=
ENV WAIT_FOR_TIMEOUT=30
ENV TZ=UTC
ENV CONTAINER_HOST=0.0.0.0

EXPOSE 8090 8091

VOLUME /tmp

RUN apk add --no-cache bash curl tini openjdk17-jre tzdata && \
    apk -U upgrade && \
    curl "https://raw.githubusercontent.com/vishnubob/wait-for-it/master/wait-for-it.sh" --output /usr/local/bin/wait-for-it.sh && \
    chmod +x /usr/local/bin/wait-for-it.sh && \
    addgroup -S -g 1001 camunda && \
    adduser -S -g 1001 -u 1001 camunda && \
    mkdir -p /optimize && \
    chown 1001:1001 /optimize

WORKDIR /optimize
USER 1001:1001

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["./optimize.sh"]

COPY --chown=1001:1001 --from=builder /tmp/build .
