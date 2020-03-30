FROM alpine:latest as builder

ARG DISTBALL
ENV ZEEBE_HOME=/usr/local/zeebe
WORKDIR ${ZEEBE_HOME}

RUN apk add --no-cache tar

COPY ${DISTBALL} /tmp/${DISTBALL}
RUN ls -lash /tmp/${DISTBALL}
RUN tar xfvz /tmp/${DISTBALL} --strip 1 -C ${ZEEBE_HOME}/ \
  && rm -rf /tmp/${DISTBALL}

FROM openjdk:11-jre-slim as zeebe

ENV ZB_HOME=/usr/local/zeebe \
    ZEEBE_LOG_LEVEL=info \
    ZEEBE_BROKER_GATEWAY_NETWORK_HOST=0.0.0.0 \
    ZEEBE_BROKER_NETWORK_HOST=0.0.0.0 \
    ZEEBE_STANDALONE_GATEWAY=false
ENV PATH "${ZB_HOME}/bin:${PATH}"

WORKDIR ${ZB_HOME}
EXPOSE 26500 26501 26502
VOLUME ${ZB_HOME}/data

ADD https://github.com/krallin/tini/releases/download/v0.18.0/tini /bin/tini
RUN chmod +x /bin/tini
ENTRYPOINT ["tini", "--", "/usr/local/bin/startup.sh"]

COPY docker/utils/startup.sh /usr/local/bin
COPY --from=builder ${ZB_HOME} ${ZB_HOME}
