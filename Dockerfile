FROM openjdk:8

ARG DISTBALL

ENV ZB_HOME=/usr/local/zeebe/

COPY ${DISTBALL} ${ZB_HOME}

RUN tar xfvz ${ZB_HOME}/*.tar.gz --strip 1 -C ${ZB_HOME} && rm ${ZB_HOME}/*.tar.gz

WORKDIR ${ZB_HOME}/bin
EXPOSE 51016 51017 51015
VOLUME ${ZB_HOME}/bin/data

ENTRYPOINT ["./broker"]
