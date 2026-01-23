# hadolint global ignore=DL3006
ARG BASE_IMAGE="reg.mini.dev/1212/openjre-base-compat:21-dev"
ARG BASE_DIGEST="sha256:6d72cf910cea8b66f3c4691b69371527c0c2dd528eddf87af55c1870e904707d"
ARG JATTACH_VERSION="v2.2"
ARG JATTACH_CHECKSUM_AMD64="acd9e17f15749306be843df392063893e97bfecc5260eef73ee98f06e5cfe02f"
ARG JATTACH_CHECKSUM_ARM64="288ae5ed87ee7fe0e608c06db5a23a096a6217c9878ede53c4e33710bdcaab51"
ARG WAITFORIT_CHECKSUM="b7a04f38de1e51e7455ecf63151c8c7e405bd2d45a2d4e16f6419db737a125d6"

# If you don't have access to Minimus hardened base images, you can use public
# base images like this instead on your own risk.
# Simply pass `--build-arg BASE=public` in order to build with the Temurin JDK.
ARG BASE_IMAGE_PUBLIC="alpine:3.23.0"
ARG BASE_DIGEST_PUBLIC="sha256:51183f2cfa6320055da30872f211093f9ff1d3cf06f39a0bdb212314c5dc7375"
ARG BASE="hardened"

### Download jattach ###
# hadolint ignore=DL3006,DL3007
FROM alpine AS tools
ARG TARGETARCH
ARG JATTACH_VERSION
ARG JATTACH_CHECKSUM_AMD64
ARG JATTACH_CHECKSUM_ARM64
ARG WAITFORIT_CHECKSUM

# hadolint ignore=DL4006,DL3018
RUN --mount=type=cache,target=/root/.tools,rw \
    apk add -q --no-cache curl 2>/dev/null && \
    if [ "${TARGETARCH}" = "amd64" ]; then \
      JATTACH_BINARY="linux-x64"; \
      JATTACH_CHECKSUM="${JATTACH_CHECKSUM_AMD64}"; \
    else  \
      JATTACH_BINARY="linux-arm64"; \
      JATTACH_CHECKSUM="${JATTACH_CHECKSUM_ARM64}"; \
    fi && \
    # Download jattach \
    curl -sL "https://github.com/jattach/jattach/releases/download/${JATTACH_VERSION}/jattach-${JATTACH_BINARY}.tgz" -o jattach.tgz && \
    echo "${JATTACH_CHECKSUM} jattach.tgz" | sha256sum -c && \
    tar -xzf "jattach.tgz" && \
    chmod +x jattach && \
    mv jattach /jattach && \
    # Download wait-for-it.sh \
    curl -sL "https://raw.githubusercontent.com/vishnubob/wait-for-it/master/wait-for-it.sh" -o /wait-for-it.sh && \
    echo "${WAITFORIT_CHECKSUM} /wait-for-it.sh" | sha256sum -c && \
    chmod +x /wait-for-it.sh

### Base Application Image ###
# hadolint ignore=DL3006
FROM ${BASE_IMAGE}@${BASE_DIGEST} AS base-hardened

### Base Public Application Image ###
# hadolint ignore=DL3006
FROM ${BASE_IMAGE_PUBLIC}@${BASE_DIGEST_PUBLIC} AS base-public

FROM base-${BASE} AS base
WORKDIR /

ARG VERSION=""
ARG DISTRO=production
ARG ARTIFACT_PATH=./optimize-distro/target

ENV TMP_DIR=/tmp/optimize \
    BUILD_DIR=/tmp/build

RUN mkdir -p ${TMP_DIR} && \
    mkdir -p ${BUILD_DIR}

COPY ${ARTIFACT_PATH}/camunda-optimize-${VERSION}-${DISTRO}.tar.gz ${BUILD_DIR}
RUN tar -xzf ${BUILD_DIR}/camunda-optimize-${VERSION}-${DISTRO}.tar.gz -C ${BUILD_DIR} && \
    rm ${BUILD_DIR}/camunda-optimize-${VERSION}-${DISTRO}.tar.gz
COPY ./optimize/docker/bin/optimize.sh ${BUILD_DIR}/optimize.sh
# Prevent environment-config.yaml from overriding service-config.yaml since the
# service-config.yaml allows usage of OPTIMIZE_ environment variables
RUN rm ${BUILD_DIR}/config/environment-config.yaml

##### FINAL IMAGE #####
FROM ${BASE_IMAGE}@${BASE_DIGEST} AS app
# leave unset to use the default value at the top of the file
ARG VERSION=""
ARG DATE=""
ARG REVISION=""
ARG BASE_IMAGE
ARG BASE_DIGEST

# OCI labels: https://github.com/opencontainers/image-spec/blob/main/annotations.md
LABEL org.opencontainers.image.base.name="${BASE_IMAGE}"
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

VOLUME /tmp

USER root
RUN addgroup -S -g 1001 camunda && \
    adduser -S -g 1001 -u 1001 camunda && \
    mkdir -p /optimize && \
    chown 1001:1001 /optimize

COPY --from=tools --chown=1001:0 /jattach /usr/bin/jattach
COPY --from=tools --chown=1001:0 /wait-for-it.sh /usr/local/bin/wait-for-it.sh

WORKDIR /optimize
USER 1001:1001

ENTRYPOINT ["tini", "--"]
CMD ["./optimize.sh"]

COPY --chown=1001:1001 --from=base /tmp/build .
