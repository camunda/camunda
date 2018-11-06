FROM openjdk:8-jre-alpine

ARG DISTBALL

ENV ZB_HOME=/usr/local/zeebe \
    ZEEBE_LOG_LEVEL=info \
    ZEEBE_GATEWAY_HOST=0.0.0.0 \
    DEPLOY_ON_KUBERNETES=false \
    BOOTSTRAP=1
ENV PATH "${ZB_HOME}/bin:${PATH}"
ENV JAVA_OPTS -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap

COPY ${DISTBALL} ${ZB_HOME}/

# libc6-compat is required to run rocksdb on alpine
RUN apk add --no-cache bash libc6-compat tini && \
    tar xfvz ${ZB_HOME}/*.tar.gz --strip 1 -C ${ZB_HOME}/ && rm ${ZB_HOME}/*.tar.gz

WORKDIR ${ZB_HOME}
EXPOSE 26500 26501 26502 26503 26504
VOLUME ${ZB_HOME}/data

COPY docker/utils/startup.sh /usr/local/bin

ENTRYPOINT ["tini", "--", "/usr/local/bin/startup.sh"]
