ARG BASE_IMAGE="alpine:3.23.6"
ARG BASE_DIGEST="sha256:85fe1e81d6758c208f3e1eed4338a1997e19d4be002d4dd32d3100c9a8c010a0"

FROM ${BASE_IMAGE}@${BASE_DIGEST} AS base
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
FROM base AS app

ARG VERSION=""
ARG DATE=""
ARG REVISION=""

# leave the values below unset to use the default value at the top of the file
ARG BASE_IMAGE
ARG BASE_DIGEST

# OCI labels: https://github.com/opencontainers/image-spec/blob/main/annotations.md
LABEL org.opencontainers.image.base.name="docker.io/library/${BASE_IMAGE}"
LABEL org.opencontainers.image.base.digest="${BASE_DIGEST}"
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

RUN apk add --no-cache bash curl tini openjdk21-jre tzdata && \
    apk -U upgrade && \
    curl "https://raw.githubusercontent.com/vishnubob/wait-for-it/master/wait-for-it.sh" --output /usr/local/bin/wait-for-it.sh && \
    chmod +x /usr/local/bin/wait-for-it.sh && \
    addgroup -S -g 1001 camunda && \
    adduser -S -g 1001 -u 1001 camunda && \
    mkdir -p /optimize && \
    chown 1001:1001 /optimize

# CVE-2026-114820: Oracle's Security Alert removes the eMudhra roots emSign G1, G2 and ECC G3 and
# Sectigo's AAA Certificate Services from the JDK cacerts. Alpine's ca-certificates still ships
# emSign G1, and update-ca-certificates rebuilds both the OS bundle and the JVM cacerts from
# /etc/ca-certificates.conf. Deselect G1 there, so it stays out when a derived image runs
# update-ca-certificates to add its own CA. The build fails if G1, G3 or AAA is in either store.
# emSign C1 and C3 are not part of Oracle's removal and stay.
SHELL ["/bin/bash", "-eo", "pipefail", "-c"]
RUN g3=86A1ECBA089C4A8D3BBE2734C612BA341D813E043CF9E8A862CD5C57A36BBE6B; \
    g1=40F6AF0346A99AA1CD1D555A4E9CCE62C7F9634603EE406615833DC8C8D00367; \
    aaa=D7A7A0FB5D7E2731D771E9484EBCDEF71D5F0C3E0A2948782BC83EE0EA699EF4; \
    sed -i 's|^mozilla/emSign_Root_CA_-_G1\.crt$|!&|' /etc/ca-certificates.conf; \
    update-ca-certificates; \
    fingerprints() { keytool "$@" | awk '$1=="SHA256:"{f=$2; gsub(":","",f); print f}'; }; \
    jvm=$(fingerprints -list -v -cacerts -storepass changeit); \
    os=$(fingerprints -printcert -file /etc/ssl/certs/ca-certificates.crt); \
    [ "$(wc -l <<< "$jvm")" -gt 100 ]; \
    [ "$(wc -l <<< "$os")" -gt 100 ]; \
    ! grep -qxE "$g3|$g1|$aaa" <<< "$jvm"$'\n'"$os"
SHELL ["/bin/sh", "-c"]

WORKDIR /optimize
USER 1001:1001

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["./optimize.sh"]

COPY --chown=1001:1001 --from=base /tmp/build .
