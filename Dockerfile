## Builder Image
FROM alpine:latest as builder

ARG DISTBALL

ENV TMP_ARCHIVE=/tmp/zeebe.tar.gz \
    TMP_DIR=/tmp/zeebe \
    TINI_VERSION=v0.19.0

COPY ${DISTBALL} ${TMP_ARCHIVE}

RUN mkdir -p ${TMP_DIR} && \
    tar xfvz ${TMP_ARCHIVE} --strip 1 -C ${TMP_DIR} && \
    # already create volume dir to later have correct rights
    mkdir ${TMP_DIR}/data

ADD https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini ${TMP_DIR}/bin/tini
COPY docker/utils/startup.sh ${TMP_DIR}/bin/startup.sh
RUN chmod +x -R ${TMP_DIR}/bin/

## Final Image
FROM openjdk:11-jre

ENV ZEEBE_USER=zeebe \
    ZEEBE_GROUP=zeebe \
    ZEEBE_HOME=/usr/local/zeebe \
    ZEEBE_LOG_LEVEL=info \
    ZEEBE_BROKER_GATEWAY_NETWORK_HOST=0.0.0.0 \
    ZEEBE_STANDALONE_GATEWAY=false

ENV PATH "${ZEEBE_HOME}/bin:${PATH}"

EXPOSE 26500 26501 26502

RUN groupadd -r ${ZEEBE_GROUP} && \
    useradd -r -g ${ZEEBE_GROUP} ${ZEEBE_USER}

COPY --from=builder --chown=${ZEEBE_USER}:${ZEEBE_GROUP} /tmp/zeebe ${ZEEBE_HOME}

WORKDIR ${ZEEBE_HOME}
VOLUME ${ZEEBE_HOME}/data

# remove curl to mitigate vulnerability
RUN apt-get --purge remove -y --auto-remove curl

USER ${ZEEBE_USER}
ENTRYPOINT ["tini", "--", "startup.sh"]
