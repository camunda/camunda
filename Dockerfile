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

FROM openjdk:11-jre-slim as app

ENV ZB_HOME=/usr/local/zeebe \
    ZEEBE_LOG_LEVEL=info \
    ZEEBE_BROKER_GATEWAY_NETWORK_HOST=0.0.0.0 \
    ZEEBE_STANDALONE_GATEWAY=false
ENV PATH "${ZB_HOME}/bin:${PATH}"

RUN groupadd -g 1000 zeebe && \
    adduser -u 1000 zeebe --system --ingroup zeebe

WORKDIR ${ZB_HOME}
EXPOSE 26500 26501 26502
VOLUME ${ZB_HOME}/data

COPY --chown=1000:0 docker/utils/startup.sh /usr/local/bin
RUN chgrp 0 /usr/local/bin/startup.sh && chmod g=u /etc/passwd && chmod 0775 /usr/local/bin/startup.sh
COPY --from=builder --chown=1000:0 /tmp/zeebe ${ZB_HOME}
RUN chown 1000:0 -R ${ZB_HOME}/ && chmod 0775 ${ZB_HOME}/ ${ZB_HOME}/data

# Set execution flag (otherwise, if image is built in a Windows environment, the start scripts
# won't be executable)
RUN chmod +x ${ZB_HOME}/bin/broker
RUN chmod +x ${ZB_HOME}/bin/gateway

ENTRYPOINT ["tini", "--", "/usr/local/bin/startup.sh"]