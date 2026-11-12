# hadolint global ignore=DL3006
ARG BASE_IMAGE="reg.mini.dev/1212/openjre-base-compat:21-dev"
ARG BASE_DIGEST="sha256:185eeb5a0420bb96c371bf40b53a573a15855283cd70365d1ea22c18430386ed"

# If you don't have access to Minimus hardened base images, you can use public
# base images like this instead on your own risk.
# Simply pass `--build-arg BASE=public` in order to build with Alpine.
ARG BASE_IMAGE_PUBLIC="alpine:3.23.8"
ARG BASE_DIGEST_PUBLIC="sha256:469c9ea410b9215eaee7976ba6c0639039b444be8e5d638f7abbada189971eb2"
ARG BASE="hardened"

### Base Application Image ###
# hadolint ignore=DL3006
FROM ${BASE_IMAGE}@${BASE_DIGEST} AS base-hardened

### Base Public Application Image ###
# hadolint ignore=DL3006
FROM ${BASE_IMAGE_PUBLIC}@${BASE_DIGEST_PUBLIC} AS base-public

# Prepare Operate Distribution
FROM base-${BASE} AS prepare
ARG DISTBALL="dist/target/camunda-zeebe-*.tar.gz"
WORKDIR /tmp/operate

# download operate
COPY ${DISTBALL} operate.tar.gz
RUN tar xzvf operate.tar.gz --strip 1 && \
    rm operate.tar.gz
COPY docker-notice.txt notice.txt
RUN sed -i '/^exec /i cat /usr/local/operate/notice.txt' bin/operate

### Application Image ###
# TARGETARCH is provided by buildkit
# https://docs.docker.com/engine/reference/builder/#automatic-platform-args-in-the-global-scope
# hadolint ignore=DL3006
FROM base-${BASE} AS app

# leave unset to use the default value at the top of the file
ARG BASE_IMAGE
ARG BASE_DIGEST
ARG VERSION=""
ARG DATE=""
ARG REVISION=""

# OCI labels: https://github.com/opencontainers/image-spec/blob/main/annotations.md
LABEL org.opencontainers.image.base.name="${BASE_IMAGE}"
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

ENV OPE_HOME=/usr/local/operate

WORKDIR ${OPE_HOME}
VOLUME /tmp
VOLUME ${OPE_HOME}/logs

# Switch to root to allow setting up our own user
USER root
RUN addgroup --gid 1001 camunda && \
    adduser -S -G camunda -u 1001 -h ${OPE_HOME} camunda && \
    # These directories are to be mounted by users, eagerly creating them and setting ownership
    # helps to avoid potential permission issues due to default volume ownership.
    mkdir ${OPE_HOME}/logs && \
    chown -R 1001:0 ${OPE_HOME} && \
    chmod -R 0775 ${OPE_HOME}

# CVE-2026-114820: Oracle's Security Alert removes the eMudhra roots emSign G1, G2 and ECC G3 and
# Sectigo's AAA Certificate Services from the JDK cacerts, but both bases still ship emSign G1 in
# ca-certificates. Deselect it in /etc/ca-certificates.conf, so update-ca-certificates drops it from
# the OS bundle (and from the Minimus JVM cacerts, which is rebuilt from it) and keeps it out when a
# derived image runs it to add its own CA. Plain Alpine has no bash, keytool or JRE: the step is POSIX
# sh, gets ca-certificates and hashes each bundle certificate, and checks the JVM cacerts only where
# keytool exists. The build fails if G1, G3 or AAA is left in a store or a store looks truncated.
# emSign C1 and C3 are not part of Oracle's removal.
# hadolint ignore=DL3018
RUN set -eu; \
    g3=86A1ECBA089C4A8D3BBE2734C612BA341D813E043CF9E8A862CD5C57A36BBE6B; \
    g1=40F6AF0346A99AA1CD1D555A4E9CCE62C7F9634603EE406615833DC8C8D00367; \
    aaa=D7A7A0FB5D7E2731D771E9484EBCDEF71D5F0C3E0A2948782BC83EE0EA699EF4; \
    tmp=$(mktemp -d); \
    chk() { \
      awk -v a="$g3" -v b="$g1" -v c="$aaa" '$1=="SHA256:"{f=$2; gsub(":","",f); n++; if (f==a||f==b||f==c) x=1} END{exit (n>100 && !x ? 0 : 1)}' "$1" \
        || { echo "forbidden root left in $2, or it looks truncated" >&2; exit 1; }; \
    }; \
    if ! command -v update-ca-certificates >/dev/null 2>&1; then \
      apk add --no-cache ca-certificates; \
      apk add --no-cache --upgrade ca-certificates-bundle; \
    fi; \
    sed -i 's#^mozilla/emSign_Root_CA_-_G1\.crt$#!&#' /etc/ca-certificates.conf; \
    update-ca-certificates; \
    awk -v d="$tmp" '/-----BEGIN CERTIFICATE-----/{n++; f=d"/c"n".b64"; next} /-----END CERTIFICATE-----/{if (f != "") close(f); f=""; next} f != ""{sub(/\r$/, ""); print > f}' /etc/ssl/certs/ca-certificates.crt; \
    for b in "$tmp"/*.b64; do base64 -d "$b" > "${b%.b64}.der"; done; \
    sha256sum "$tmp"/*.der > "$tmp/os.sum"; \
    awk '{print "SHA256: " toupper($1)}' "$tmp/os.sum" > "$tmp/os.txt"; \
    chk "$tmp/os.txt" "OS bundle"; \
    if command -v keytool >/dev/null 2>&1; then \
      keytool -list -v -cacerts -storepass changeit > "$tmp/jvm.txt"; \
      chk "$tmp/jvm.txt" "JVM cacerts"; \
    fi; \
    rm -rf "$tmp"

COPY --from=prepare --chown=1001:0 --chmod=0775 /tmp/operate ${OPE_HOME}

USER 1001:1001

ENTRYPOINT ["tini", "--", "/usr/local/operate/bin/operate"]
