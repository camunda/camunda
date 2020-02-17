# Prepare Operate Distribution
FROM alpine:3.11.3 as prepare

WORKDIR /tmp/operate

# download operate
COPY distro/target/camunda-operate-*.tar.gz operate.tar.gz
RUN tar xzvf operate.tar.gz --strip 1
RUN rm operate.tar.gz

# Operate Image
FROM openjdk:11-jre

EXPOSE 8080

ADD https://github.com/krallin/tini/releases/download/v0.18.0/tini /bin/tini

RUN chmod +x /bin/tini

WORKDIR /usr/local/operate

COPY --from=prepare /tmp/operate /usr/local/operate

ENTRYPOINT ["/bin/tini", "--", "/usr/local/operate/bin/operate"]
