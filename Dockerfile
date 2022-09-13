FROM alpine:3.16.2 as builder

ARG VERSION=2.0.0
ARG DISTRO=production
ARG ARTIFACT_PATH=./distro/target

ENV TMP_DIR=/tmp/optimize \
    BUILD_DIR=/tmp/build

RUN mkdir -p ${TMP_DIR} && \
    mkdir -p ${BUILD_DIR}

ADD ${ARTIFACT_PATH}/camunda-optimize-${VERSION}-${DISTRO}.tar.gz ${BUILD_DIR}
ADD docker/bin/optimize.sh ${BUILD_DIR}/optimize.sh
# Prevent environment-config.yaml from overriding service-config.yaml since the
# service-config.yaml allows usage of OPTIMIZE_ environment variables
RUN rm ${BUILD_DIR}/config/environment-config.yaml

##### FINAL IMAGE #####

FROM alpine:3.16.2

ENV WAIT_FOR=
ENV WAIT_FOR_TIMEOUT=30
ENV TZ=UTC
ENV CONTAINER_HOST=0.0.0.0

EXPOSE 8090 8091

RUN apk add --no-cache bash curl tini openjdk11-jre tzdata && \
    curl "https://raw.githubusercontent.com/vishnubob/wait-for-it/master/wait-for-it.sh" --output /usr/local/bin/wait-for-it.sh && \
    chmod +x /usr/local/bin/wait-for-it.sh && \
    addgroup -S optimize && \
    adduser -S -g optimize optimize && \
    mkdir -p /optimize && \
    chown optimize:optimize /optimize

WORKDIR /optimize
USER optimize

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["./optimize.sh"]

COPY --chown=optimize:optimize --from=builder /tmp/build .
