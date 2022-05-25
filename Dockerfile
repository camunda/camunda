FROM alpine:3.16.0 as builder

ARG SKIP_DOWNLOAD=false
ARG VERSION=2.0.0
ARG SNAPSHOT=false
ARG DISTRO=production

ARG NEXUS_USR
ARG NEXUS_PSW

RUN apk add --no-cache maven tar

# settings.xml is suffixed with a wildcard as it's optional (it's only needed for SKIP_DOWNLOAD=false)
# see https://forums.docker.com/t/copy-only-if-file-exist/3781
COPY settings.xml* docker/download.sh /tmp/
# release artifacts should always win, thus copied last if present
COPY distro/target/*-${DISTRO}.tar.gz target/checkout/distro/target/*-${DISTRO}.tar.gz /tmp/

WORKDIR /build

RUN /tmp/download.sh

##### FINAL IMAGE #####

FROM alpine:3.16.0

ENV WAIT_FOR=
ENV WAIT_FOR_TIMEOUT=30
ENV TZ=UTC
ENV CONTAINER_HOST=0.0.0.0

EXPOSE 8090 8091

RUN apk add --no-cache bash curl tini openjdk11-jre tzdata && \
    wget -O /usr/local/bin/wait-for-it.sh "https://raw.githubusercontent.com/vishnubob/wait-for-it/master/wait-for-it.sh" && \
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
