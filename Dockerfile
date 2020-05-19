FROM openjdk:11-jre

ARG DISTBALL

ENV ZB_HOME=/usr/local/zeebe \
    ZEEBE_LOG_LEVEL=info \
    ZEEBE_BROKER_GATEWAY_NETWORK_HOST=0.0.0.0 \
    ZEEBE_STANDALONE_GATEWAY=false
ENV PATH "${ZB_HOME}/bin:${PATH}"

RUN groupadd -g 1000 zeebe && \
    adduser -u 1000 zeebe --system --ingroup zeebe

ADD https://github.com/krallin/tini/releases/download/v0.18.0/tini /bin/tini
RUN chmod +x /bin/tini && chown 1000:0 /bin/tini

COPY --chown=1000:0 ${DISTBALL} ${ZB_HOME}/

RUN tar xfvz ${ZB_HOME}/*.tar.gz --strip 1 -C ${ZB_HOME}/ && rm ${ZB_HOME}/*.tar.gz && \
    mkdir ${ZB_HOME}/data && chown 1000:0 -R ${ZB_HOME}/ && chmod 0775 ${ZB_HOME}/ ${ZB_HOME}/data

WORKDIR ${ZB_HOME}
EXPOSE 26500 26501 26502
VOLUME ${ZB_HOME}/data

COPY --chown=1000:0 docker/utils/startup.sh /usr/local/bin
RUN chgrp 0 /usr/local/bin/startup.sh && chmod g=u /etc/passwd && chmod 0775 /usr/local/bin/startup.sh

### Set execution flag (otherwise, if image is built in a Windows environment, the start scripts won't be executable)
RUN chmod +x ${ZB_HOME}/bin/broker
RUN chmod +x ${ZB_HOME}/bin/gateway

RUN apt-get --purge remove -y --auto-remove curl

ENTRYPOINT ["tini", "--", "/usr/local/bin/startup.sh"]
