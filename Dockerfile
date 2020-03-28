# It is very important that both the jrebuilder and the target container have the same ELF program
# interpreter, so it's simpler if we just use the same base image for both
FROM azul/zulu-openjdk-alpine:11 as jrebuilder

# Required for strip utility
RUN apk add --no-cache --update binutils

# Build custom JRE
# For non-dev builds we can remove jdk.jcmd which provides JDK tools (e.g. jcmd, jstats, etc.)
RUN ${JAVA_HOME}/bin/jlink --verbose --compress 2 --strip-debug --no-header-files --no-man-pages \
      --output /opt/minijre \
      --add-modules java.base,java.xml,java.desktop,java.naming,java.sql,java.management,java.instrument,jdk.unsupported,jdk.jcmd \
  &&  strip -p /opt/minijre/lib/server/libjvm.so

FROM maven:3.6.0-jdk-11 as builder

# Build Zeebe
WORKDIR /usr/local/src/zeebe

# Copies all necessary dependencies to build the Zeebe distribution
# Would be simpler if they were under the same directory, but meh
# Might be that there is a better way of listing these, or just use
# Jib which should manage these things
COPY ./NOTICE.txt /usr/local/src/zeebe/NOTICE.txt
COPY ./pom.xml /usr/local/src/zeebe/pom.xml
COPY ./bom /usr/local/src/zeebe/bom
COPY ./bpmn-model /usr/local/src/zeebe/bpmn-model
COPY ./broker /usr/local/src/zeebe/broker
COPY ./build-tools /usr/local/src/zeebe/build-tools
COPY ./dispatcher /usr/local/src/zeebe/dispatcher
COPY ./dist /usr/local/src/zeebe/dist
COPY ./engine /usr/local/src/zeebe/engine
COPY ./exporter-api /usr/local/src/zeebe/exporter-api
COPY ./exporters /usr/local/src/zeebe/exporters
COPY ./expression-language /usr/local/src/zeebe/expression-language
COPY ./gateway /usr/local/src/zeebe/gateway
COPY ./gateway-protocol /usr/local/src/zeebe/gateway-protocol
COPY ./gateway-protocol-impl /usr/local/src/zeebe/gateway-protocol-impl
COPY ./json-path /usr/local/src/zeebe/json-path
COPY ./legacy /usr/local/src/zeebe/legacy
COPY ./licenses /usr/local/src/zeebe/licenses
COPY ./logstreams /usr/local/src/zeebe/logstreams
COPY ./msgpack-core /usr/local/src/zeebe/msgpack-core
COPY ./msgpack-value /usr/local/src/zeebe/msgpack-value
COPY ./parent /usr/local/src/zeebe/parent
COPY ./protocol /usr/local/src/zeebe/protocol
COPY ./protocol-impl /usr/local/src/zeebe/protocol-impl
COPY ./transport /usr/local/src/zeebe/transport
COPY ./util /usr/local/src/zeebe/util
COPY ./zb-db /usr/local/src/zeebe/zb-db

# RUN mvn clean dependency:go-offline -P dist
RUN mvn package -P dist -DskipTests -Dcheckstyle.skip

FROM alpine:latest as mini

ENV ZEEBE_HOME=/usr/local/zeebe \
    ZEEBE_LOG_LEVEL=info \
    ZEEBE_BROKER_GATEWAY_NETWORK_HOST=0.0.0.0 \
    ZEEBE_STANDALONE_GATEWAY=false \
    JAVA_HOME=/opt/minijre
ENV PATH "${ZEEBE_HOME}/bin:${JAVA_HOME}/bin:${PATH}"

WORKDIR ${ZEEBE_HOME}
EXPOSE 26500 26501 26502
VOLUME ${ZEEBE_HOME}/data

RUN apk add --no-cache tini
COPY docker/utils/startup.sh /usr/local/bin
ENTRYPOINT ["tini", "--", "/usr/local/bin/startup.sh"]

COPY --from=jrebuilder /opt/minijre ${JAVA_HOME}/
COPY --from=builder /usr/local/src/zeebe/dist/target/zeebe-broker ${ZEEBE_HOME}/

