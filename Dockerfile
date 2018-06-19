FROM alpine:latest as builder
WORKDIR /build

ARG REPO=camunda-optimize
ARG VERSION=2.0.0
ARG SNAPSHOT=false
ARG DISTRO=standalone

# Nexus credentials
ARG USERNAME
ARG PASSWORD

# Download Optimize
RUN apk add --no-cache tar wget
COPY docker/ /
RUN /bin/download.sh

############ Production image ###############
FROM openjdk:8u151-jre-alpine3.7

ENV OPTIMIZE_HOME=/optimize \
    JAVA_OPTS="-Xms512m -Xmx512m -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=256m -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap" \
    TZ=Europe/Berlin
ENV OPTIMIZE_CLASSPATH=${OPTIMIZE_HOME}/environment:${OPTIMIZE_HOME}/plugin/*:${OPTIMIZE_HOME}/*

WORKDIR ${OPTIMIZE_HOME}

EXPOSE 8090 8091

ENTRYPOINT ["/sbin/tini", "--"]
CMD exec java ${JAVA_OPTS} -cp "${OPTIMIZE_CLASSPATH}" -Dfile.encoding=UTF-8 org.camunda.optimize.Main

RUN apk add --no-cache bash curl tini tzdata && \
    addgroup -S optimize && \
    adduser -S -g optimize optimize && \
    chown optimize:optimize /optimize

COPY --chown=optimize:optimize --from=builder /build .

USER optimize
