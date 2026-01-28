# hadolint global ignore=DL3006
ARG BASE_IMAGE="reg.mini.dev/1212/openjre-base-compat:21-dev"
ARG BASE_DIGEST="sha256:6d72cf910cea8b66f3c4691b69371527c0c2dd528eddf87af55c1870e904707d"
ARG JATTACH_VERSION="v2.2"
ARG JATTACH_CHECKSUM_AMD64="acd9e17f15749306be843df392063893e97bfecc5260eef73ee98f06e5cfe02f"
ARG JATTACH_CHECKSUM_ARM64="288ae5ed87ee7fe0e608c06db5a23a096a6217c9878ede53c4e33710bdcaab51"

# If you don't have access to Minimus hardened base images, you can use public
# base images like this instead on your own risk:
#ARG BASE_IMAGE="eclipse-temurin:21-jre-noble"
#ARG BASE_DIGEST="sha256:20e7f7288e1c18eebe8f06a442c9f7183342d9b022d3b9a9677cae2b558ddddd"

FROM alpine AS tools
ARG TARGETARCH
ARG JATTACH_VERSION
ARG JATTACH_CHECKSUM_AMD64
ARG JATTACH_CHECKSUM_ARM64

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
    mv jattach /jattach

# Prepare tasklist Distribution
FROM ${BASE_IMAGE}@${BASE_DIGEST} AS prepare

ARG DISTBALL="dist/target/camunda-zeebe-*.tar.gz"
WORKDIR /tmp/tasklist

# download tasklist
COPY ${DISTBALL} tasklist.tar.gz
RUN tar xzvf tasklist.tar.gz --strip 1 && \
    rm tasklist.tar.gz

### Application Image ###
# hadolint ignore=DL3006
FROM ${BASE_IMAGE}@${BASE_DIGEST} AS app
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
LABEL org.opencontainers.image.authors="hto@camunda.com"
LABEL org.opencontainers.image.url="https://camunda.com/platform/tasklist/"
LABEL org.opencontainers.image.documentation="https://docs.camunda.io/docs/self-managed/tasklist-deployment/install-and-start/"
LABEL org.opencontainers.image.source="https://github.com/camunda/camunda"
LABEL org.opencontainers.image.version="${VERSION}"
LABEL org.opencontainers.image.revision="${REVISION}"
LABEL org.opencontainers.image.vendor="Camunda Services GmbH"
LABEL org.opencontainers.image.licenses="(Apache-2.0 AND LicenseRef-Camunda-License-1.0)"
LABEL org.opencontainers.image.title="Camunda Tasklist"
LABEL org.opencontainers.image.description="Tasklist is a ready-to-use application to rapidly implement business processes alongside user tasks in Zeebe"

# OpenShift labels: https://docs.openshift.com/container-platform/4.10/openshift_images/create-images.html#defining-image-metadata
LABEL io.openshift.tags="bpmn,tasklist,camunda"
LABEL io.openshift.wants="zeebe,elasticsearch"
LABEL io.openshift.non-scalable="false"
LABEL io.openshift.min-memory="1Gi"
LABEL io.openshift.min-cpu="1"
LABEL io.k8s.description="Tasklist is a ready-to-use application to rapidly implement business processes alongside user tasks in Zeebe"

EXPOSE 8080

ENV TASKLIST_HOME=/usr/local/tasklist

WORKDIR ${TASKLIST_HOME}
VOLUME /tmp
VOLUME ${TASKLIST_HOME}/logs

USER root
RUN addgroup --gid 1001 camunda && \
    adduser -S -h ${TASKLIST_HOME} -G camunda -u 1001 camunda && \
    # These directories are to be mounted by users, eagerly creating them and setting ownership
    # helps to avoid potential permission issues due to default volume ownership.
    mkdir ${TASKLIST_HOME}/logs && \
    chown -R 1001:0 ${TASKLIST_HOME} && \
    chmod -R 0775 ${TASKLIST_HOME}

COPY --from=tools --chown=1001:0 /jattach /usr/bin/jattach
COPY --from=prepare --chown=1001:0 --chmod=0775 /tmp/tasklist ${TASKLIST_HOME}

# rename tasklist-migrate script to migrate (as expected by SaaS)
RUN mv ${TASKLIST_HOME}/bin/tasklist-migrate ${TASKLIST_HOME}/bin/migrate

USER 1001:1001

ENTRYPOINT ["tini", "--", "/usr/local/tasklist/bin/tasklist"]
