# hadolint global ignore=DL3006
ARG BASE_IMAGE="reg.mini.dev/1212/openjre-base-compat:21-dev"
ARG BASE_DIGEST="sha256:185eeb5a0420bb96c371bf40b53a573a15855283cd70365d1ea22c18430386ed"
ARG WAITFORIT_CHECKSUM="b7a04f38de1e51e7455ecf63151c8c7e405bd2d45a2d4e16f6419db737a125d6"

# If you don't have access to Minimus hardened base images, you can use public
# base images like this instead on your own risk.
# Simply pass `--build-arg BASE=public` in order to build with Alpine.
ARG BASE_IMAGE_PUBLIC="alpine:3.23.7"
ARG BASE_DIGEST_PUBLIC="sha256:ff7f0c727dd796aa36787bfcd9a64bf35a6ce72a73451b3359fde849b6703d63"
ARG BASE="hardened"

### Download wait-for-it.sh ###
# hadolint ignore=DL3006,DL3007
FROM alpine AS tools
ARG WAITFORIT_CHECKSUM

# hadolint ignore=DL4006,DL3018
RUN --mount=type=cache,target=/root/.tools,rw \
    apk add -q --no-cache curl 2>/dev/null && \
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

# Prepare Optimize Distribution
FROM base-${BASE} AS prepare
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
# hadolint ignore=DL3006
FROM base-${BASE} AS app
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

COPY --from=tools --chown=1001:0 /wait-for-it.sh /usr/local/bin/wait-for-it.sh

# CVE-2026-114820: Oracle's Security Alert removes the eMudhra roots emSign G1, G2 and ECC G3 and
# Sectigo's AAA Certificate Services from the JDK cacerts. Most bases still ship some of them in
# ca-certificates. Deselect them in /etc/ca-certificates.conf so update-ca-certificates keeps them
# out of the OS bundle (and out of the JVM cacerts where that is rebuilt from it) even when a derived
# image runs update-ca-certificates to add its own CA. Temurin keeps a separate JVM cacerts, so also
# delete the roots from it by fingerprint. The build fails if G1, G3 or AAA is left in either store.
# emSign C1 and C3 are not part of Oracle's removal and stay. POSIX sh without pipes: plain Alpine
# has no bash, and a failing "! pipeline" would not stop "set -e". Plain Alpine gets ca-certificates
# for update-ca-certificates and has no JVM store to check.
# hadolint ignore=DL3018
RUN set -eu; \
    g3=86A1ECBA089C4A8D3BBE2734C612BA341D813E043CF9E8A862CD5C57A36BBE6B; \
    g1=40F6AF0346A99AA1CD1D555A4E9CCE62C7F9634603EE406615833DC8C8D00367; \
    aaa=D7A7A0FB5D7E2731D771E9484EBCDEF71D5F0C3E0A2948782BC83EE0EA699EF4; \
    if ! command -v update-ca-certificates >/dev/null 2>&1 && command -v apk >/dev/null 2>&1; then \
      apk add --no-cache ca-certificates; \
    fi; \
    if command -v update-ca-certificates >/dev/null 2>&1; then \
      sed -i -E 's#^mozilla/(emSign_Root_CA_-_G[12]|emSign_ECC_Root_CA_-_G3|AAA_Certificate_Services)\.crt$#!&#' /etc/ca-certificates.conf; \
      update-ca-certificates; \
    fi; \
    for pem in emSign_Root_CA_-_G1 emSign_Root_CA_-_G2 emSign_ECC_Root_CA_-_G3 AAA_Certificate_Services; do \
      [ ! -e "/etc/ssl/certs/$pem.pem" ]; \
    done; \
    if command -v keytool >/dev/null 2>&1; then \
      tmp=$(mktemp -d); \
      fingerprints() { awk '/^Alias name: /{a=substr($0,13)} $1=="SHA256:"{f=$2; gsub(":","",f); print f"\t"a}' "$1"; }; \
      keytool -list -v -cacerts -storepass changeit > "$tmp/jvm.txt"; \
      fingerprints "$tmp/jvm.txt" > "$tmp/jvm.fp"; \
      for fp in "$g3" "$g1" "$aaa"; do \
        alias=$(awk -F'\t' -v fp="$fp" '$1==fp{print $2}' "$tmp/jvm.fp"); \
        if [ -n "$alias" ]; then keytool -delete -cacerts -storepass changeit -alias "$alias"; fi; \
      done; \
      keytool -list -v -cacerts -storepass changeit > "$tmp/jvm.txt"; \
      fingerprints "$tmp/jvm.txt" > "$tmp/jvm.fp"; \
      [ "$(wc -l < "$tmp/jvm.fp")" -gt 100 ]; \
      if awk -F'\t' -v a="$g3" -v b="$g1" -v c="$aaa" '$1==a||$1==b||$1==c{f=1} END{exit (f ? 0 : 1)}' "$tmp/jvm.fp"; then \
        echo "forbidden root left in JVM cacerts" >&2; exit 1; \
      fi; \
      rm -rf "$tmp"; \
    fi; \
    if [ -f /etc/ssl/certs/ca-certificates.crt ]; then \
      osd=$(mktemp -d); \
      awk -v d="$osd" '/-----BEGIN CERTIFICATE-----/{n++; f=d"/c"n".b64"; next} /-----END CERTIFICATE-----/{if (f != "") close(f); f=""; next} f != ""{print > f}' /etc/ssl/certs/ca-certificates.crt; \
      for b in "$osd"/*.b64; do base64 -d "$b" > "${b%.b64}.der"; done; \
      sha256sum "$osd"/*.der > "$osd/os.sum"; \
      [ "$(wc -l < "$osd/os.sum")" -gt 100 ]; \
      if awk -v a="$g3" -v b="$g1" -v c="$aaa" '{h=toupper($1)} h==a||h==b||h==c{f=1} END{exit (f ? 0 : 1)}' "$osd/os.sum"; then \
        echo "forbidden root left in OS bundle" >&2; exit 1; \
      fi; \
      rm -rf "$osd"; \
    fi

WORKDIR /optimize
USER 1001:1001

ENTRYPOINT ["tini", "--"]
CMD ["./optimize.sh"]

COPY --chown=1001:1001 --from=prepare /tmp/build .
