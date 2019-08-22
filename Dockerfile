FROM alpine:3.10 as builder

ARG SKIP_DOWNLOAD=false
ARG VERSION=2.0.0
ARG SNAPSHOT=false
ARG DISTRO=production

ARG NEXUS_USR
ARG NEXUS_PSW

RUN apk add --no-cache maven tar

COPY settings.xml docker/download.sh distro/target/*-${DISTRO}.tar.gz /tmp/

WORKDIR /build

RUN /tmp/download.sh

##### FINAL IMAGE #####

FROM alpine:3.10

ENV OPTIMIZE_CLASSPATH=/optimize/environment:/optimize/plugin/*:/optimize/*
ENV WAIT_FOR=
ENV WAIT_FOR_TIMEOUT=30
ENV TZ=UTC
ENV JAVA_OPTS="-Xms512m -Xmx512m -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=256m"

EXPOSE 8090 8091

# Downgrading wait-for-it is necessary until this PR is merged
# https://github.com/vishnubob/wait-for-it/pull/68
RUN apk add --no-cache bash curl tini openjdk8-jre tzdata && \
    wget -O /usr/local/bin/wait-for-it.sh "https://raw.githubusercontent.com/vishnubob/wait-for-it/a454892f3c2ebbc22bd15e446415b8fcb7c1cfa4/wait-for-it.sh" && \
    chmod +x /usr/local/bin/wait-for-it.sh && \
    addgroup -S optimize && \
    adduser -S -g optimize optimize && \
    mkdir -p /optimize && \
    chown optimize:optimize /optimize

WORKDIR /optimize
USER optimize

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["./optimize.sh"]

COPY --chown=optimize:optimize --from=builder /build .
COPY docker/bin/optimize.sh ./optimize.sh
