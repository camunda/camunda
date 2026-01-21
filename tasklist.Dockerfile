# hadolint global ignore=DL3006
ARG BASE_IMAGE="reg.mini.dev/1212/openjre-base:21-dev"
ARG BASE_DIGEST="sha256:7d2162e98d86ad3c4f0187a6eb4ff77057c7653623b52eaa106c63a99e021d31"

# If you don't have access to Minimus hardened base images, you can use public
# base images like this instead on your own risk:
#ARG BASE_IMAGE="alpine:3.23.0"
#ARG BASE_DIGEST="sha256:51183f2cfa6320055da30872f211093f9ff1d3cf06f39a0bdb212314c5dc7375"

# Prepare Tasklist Distribution
FROM ${BASE_IMAGE}@${BASE_DIGEST} AS prepare
ARG DISTBALL="dist/target/camunda-zeebe-*.tar.gz"
WORKDIR /tmp/tasklist

# download tasklist
COPY ${DISTBALL} tasklist.tar.gz
RUN tar xzvf tasklist.tar.gz --strip 1 && \
    rm tasklist.tar.gz
COPY docker-notice.txt notice.txt
RUN sed -i '/^exec /i cat /usr/local/tasklist/notice.txt' bin/tasklist

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

# Switch to root to allow setting up our own user
USER root
RUN addgroup --gid 1001 camunda && \
    adduser -D -h ${TASKLIST_HOME} -G camunda -u 1001 camunda && \
    # These directories are to be mounted by users, eagerly creating them and setting ownership
    # helps to avoid potential permission issues due to default volume ownership.
    mkdir ${TASKLIST_HOME}/logs && \
    chown -R 1001:0 ${TASKLIST_HOME} && \
    chmod -R 0775 ${TASKLIST_HOME}

COPY --from=prepare --chown=1001:0 --chmod=0775 /tmp/tasklist ${TASKLIST_HOME}

# rename tasklist-migrate script to migrate (as expected by SaaS)
RUN mv ${TASKLIST_HOME}/bin/tasklist-migrate ${TASKLIST_HOME}/bin/migrate

USER 1001:1001

ENTRYPOINT ["/usr/local/tasklist/bin/tasklist"]
