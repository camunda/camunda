# Prepare Operate Distribution
FROM alpine:3.12.0 as prepare

WORKDIR /tmp/operate

# download operate
COPY distro/target/camunda-operate-*.tar.gz operate.tar.gz
RUN tar xzvf operate.tar.gz --strip 1
RUN rm operate.tar.gz

# prepare migration
WORKDIR /tmp/operate/migration
COPY els-schema/target/migration.zip migration.zip
RUN unzip migration.zip 
RUN rm migration.zip

# Operate Image
FROM openjdk:11-jre

EXPOSE 8080

ADD https://github.com/krallin/tini/releases/download/v0.18.0/tini /bin/tini

WORKDIR /usr/local/operate

COPY --from=prepare /tmp/operate /usr/local/operate

RUN chmod +x /bin/tini /usr/local/operate/migration/migrate.sh

ENTRYPOINT ["/bin/tini", "--", "/usr/local/operate/bin/operate"]
