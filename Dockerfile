# Prepare Operate Distribution
FROM alpine:3.13.2 as prepare

WORKDIR /tmp/operate

# download operate
COPY distro/target/camunda-operate-*.tar.gz operate.tar.gz
RUN tar xzvf operate.tar.gz --strip 1
RUN rm operate.tar.gz
COPY docker-notice.txt notice.txt
RUN sed -i '/^exec /i cat /usr/local/operate/notice.txt' bin/operate

# Operate Image
FROM openjdk:19-ea-slim-buster

EXPOSE 8080

ADD https://github.com/krallin/tini/releases/download/v0.19.0/tini /bin/tini

WORKDIR /usr/local/operate

COPY --from=prepare /tmp/operate /usr/local/operate

RUN chmod +x /bin/tini

ENTRYPOINT ["/bin/tini", "--", "/usr/local/operate/bin/operate"]
