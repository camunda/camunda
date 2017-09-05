FROM openjdk:8

ARG DISTBALL

ENV ZB_HOME=/usr/local/zeebe/ \
    DEBUG=false \
    DEPLOY_ON_KUBERNETES=false

COPY ${DISTBALL} ${ZB_HOME}

RUN tar xfvz ${ZB_HOME}/*.tar.gz --strip 1 -C ${ZB_HOME} && rm ${ZB_HOME}/*.tar.gz

WORKDIR ${ZB_HOME}/bin
EXPOSE 51016 51017 51015
VOLUME ${ZB_HOME}/bin/data

COPY docker/utils/startup.sh /usr/local/bin
COPY docker/utils/zeebe.cfg.toml $ZB_HOME/conf/

ENTRYPOINT ["/usr/local/bin/startup.sh"]
