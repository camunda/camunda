# It is very important that both the jrebuilder and the target container have the same ELF program
# interpreter, so it's simpler if we just use the same base image for both
FROM azul/zulu-openjdk-alpine:11 as jrebuilder

# Required for strip utility
RUN apk add --no-cache --update binutils

# Build custom JRE
# For non-dev builds we can remove jdk.jcmd which provides JDK tools (e.g. jcmd, jstats, etc.)
RUN ${JAVA_HOME}/bin/jlink --verbose --compress 2 --strip-debug --no-header-files --no-man-pages \
      --output /opt/zeebe-jre \
      --add-modules java.base,java.xml,java.desktop,java.naming,java.sql,java.management,java.instrument,jdk.unsupported,jdk.jcmd \
  &&  strip -p /opt/zeebe-jre/lib/server/libjvm.so

# Having a specific builder stage removes an unnecessary copy layer from the usual build process,
# and gives us flexibility in the future on different build processes (e.g. not using a DISTBALL)
FROM alpine:latest as builder

ARG DISTBALL
ENV ZEEBE_HOME=/usr/local/zeebe
WORKDIR ${ZEEBE_HOME}

RUN apk add --no-cache tar

COPY ${DISTBALL} /tmp/${DISTBALL}
RUN ls -lash /tmp/${DISTBALL}
RUN tar xfvz /tmp/${DISTBALL} --strip 1 -C ${ZEEBE_HOME}/ \
  && rm -rf /tmp/${DISTBALL}

FROM alpine:latest as zeebe

ENV ZEEBE_HOME=/usr/local/zeebe \
    ZEEBE_LOG_LEVEL=info \
    ZEEBE_BROKER_GATEWAY_NETWORK_HOST=0.0.0.0 \
    ZEEBE_BROKER_NETWORK_HOST=0.0.0.0 \
    ZEEBE_STANDALONE_GATEWAY=false \
    JAVA_HOME=/opt/zeebe-jre
ENV PATH "${ZEEBE_HOME}/bin:${JAVA_HOME}/bin:${PATH}"

WORKDIR ${ZEEBE_HOME}
EXPOSE 26500 26501 26502
VOLUME ${ZEEBE_HOME}/data

# RocksDB requires libstdc++
# Temporarily install bash to be compatible with helm charts and other startup scripts
RUN apk add --no-cache tini libstdc++ bash
COPY docker/utils/startup.sh /usr/local/bin
ENTRYPOINT ["tini", "--", "/usr/local/bin/startup.sh"]

COPY --from=jrebuilder /opt/zeebe-jre ${JAVA_HOME}/
COPY --from=builder ${ZEEBE_HOME} ${ZEEBE_HOME}
