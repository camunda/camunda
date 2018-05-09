FROM openjdk:8-jre-alpine

ARG DISTBALL

ENV ZB_HOME=/usr/local/zeebe \
    ZEEBE_LOG_LEVEL=info \
    DEPLOY_ON_KUBERNETES=false \
    BOOTSTRAP=1
ENV PATH "${ZB_HOME}/bin:${PATH}"

COPY ${DISTBALL} ${ZB_HOME}/

RUN apk add --no-cache bash && \
    tar xfvz ${ZB_HOME}/*.tar.gz --strip 1 -C ${ZB_HOME}/ && rm ${ZB_HOME}/*.tar.gz

WORKDIR ${ZB_HOME}
EXPOSE 51016 51017 51015
VOLUME ${ZB_HOME}/data

COPY docker/utils/startup.sh /usr/local/bin

ENTRYPOINT ["/usr/local/bin/startup.sh"]
