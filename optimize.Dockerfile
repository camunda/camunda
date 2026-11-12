# hadolint global ignore=DL3006
ARG BASE_IMAGE="reg.mini.dev/1212/openjre-base:21-dev"
ARG BASE_DIGEST="sha256:b516bb84b4641b88759bf2274e41868d39d467ea435c2c0f9d9bf3d21a717149"

# If you don't have access to Minimus hardened base images, you can use public
# base images like this instead on your own risk.
# Simply pass `--build-arg BASE=public` in order to build with the Temurin JDK.
ARG BASE_IMAGE_PUBLIC="eclipse-temurin:21.0.13_9-jre-noble"
ARG BASE_DIGEST_PUBLIC="sha256:0d57a8e90c2bb844c5a8e1b2b71e11eff06dad7edcfd803cd21ed4ae203b2515"
ARG BASE="hardened"

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
ARG DISTBALL=""

ENV TMP_DIR=/tmp/optimize \
    BUILD_DIR=/tmp/build

RUN mkdir -p ${TMP_DIR} && \
    mkdir -p ${BUILD_DIR}

# Support both legacy path and monorepo DISTBALL pattern
COPY ${DISTBALL:-${ARTIFACT_PATH}/camunda-optimize-${VERSION}-${DISTRO}.tar.gz} ${BUILD_DIR}/optimize-distro.tar.gz
RUN tar -xzf ${BUILD_DIR}/optimize-distro.tar.gz -C ${BUILD_DIR} && \
    rm ${BUILD_DIR}/optimize-distro.tar.gz
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

# Switch to root to allow setting up our own user
USER root
RUN mkdir -p /usr/local/bin/ && \
    wget -nv -O /usr/local/bin/wait-for-it.sh "https://raw.githubusercontent.com/vishnubob/wait-for-it/master/wait-for-it.sh" && \
    chmod +x /usr/local/bin/wait-for-it.sh && \
    addgroup -S -g 1001 camunda && \
    adduser -S -g 1001 -u 1001 camunda && \
    mkdir -p /optimize && \
    chown 1001:1001 /optimize

# CVE-2026-114820: Oracle's Security Alert removes the eMudhra roots emSign G1, G2 and ECC G3 and
# Sectigo's AAA Certificate Services from the JDK cacerts, but the bases still ship emSign G1 (and
# on Temurin ECC G3) in ca-certificates. Deselect them in /etc/ca-certificates.conf, so
# update-ca-certificates drops them from the OS bundle and keeps them out when a derived image runs
# it to add its own CA. Temurin's JVM cacerts is separate, so G1 and G3 are also deleted from it by
# fingerprint; on Minimus it is rebuilt from the OS bundle. The build fails if G1, G3 or AAA is left
# in either store or a store looks truncated. emSign C1 and C3 are not part of Oracle's removal.
RUN set -eu; \
    g3=86A1ECBA089C4A8D3BBE2734C612BA341D813E043CF9E8A862CD5C57A36BBE6B; \
    g1=40F6AF0346A99AA1CD1D555A4E9CCE62C7F9634603EE406615833DC8C8D00367; \
    aaa=D7A7A0FB5D7E2731D771E9484EBCDEF71D5F0C3E0A2948782BC83EE0EA699EF4; \
    tmp=$(mktemp -d); \
    chk() { \
      awk -v a="$g3" -v b="$g1" -v c="$aaa" '$1=="SHA256:"{f=$2; gsub(":","",f); n++; if (f==a||f==b||f==c) x=1} END{exit (n>100 && !x ? 0 : 1)}' "$1" \
        || { echo "forbidden root left in $2, or it looks truncated" >&2; exit 1; }; \
    }; \
    sed -i -E 's#^mozilla/(emSign_Root_CA_-_G1|emSign_ECC_Root_CA_-_G3)\.crt$#!&#' /etc/ca-certificates.conf; \
    update-ca-certificates; \
    keytool -list -v -cacerts -storepass changeit > "$tmp/jvm.txt"; \
    for fp in "$g3" "$g1"; do \
      alias=$(awk -v fp="$fp" '/^Alias name: /{a=substr($0,13)} $1=="SHA256:"{f=$2; gsub(":","",f); if (f==fp) print a}' "$tmp/jvm.txt"); \
      if [ -n "$alias" ]; then keytool -delete -cacerts -storepass changeit -alias "$alias"; fi; \
    done; \
    keytool -list -v -cacerts -storepass changeit > "$tmp/jvm.txt"; \
    chk "$tmp/jvm.txt" "JVM cacerts"; \
    keytool -printcert -file /etc/ssl/certs/ca-certificates.crt > "$tmp/os.txt"; \
    chk "$tmp/os.txt" "OS bundle"; \
    rm -rf "$tmp"

WORKDIR /optimize
USER 1001:1001

CMD ["./optimize.sh"]

COPY --chown=1001:1001 --from=base /tmp/build .
