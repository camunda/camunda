FROM lazyfs-zeebe:0.3.1-statfs
RUN apt-get update && apt-get install -y --no-install-recommends curl jq && rm -rf /var/lib/apt/lists/*
COPY --from=camunda/camunda:8.8.41 /usr/local/camunda /usr/local/camunda
# architecture-independent JAVA_HOME (amd64 or arm64)
RUN ln -s "$(dirname $(dirname $(readlink -f $(which java))))" /usr/lib/jvm/java-21
ENV CAMUNDA_HOME=/usr/local/camunda \
    JAVA_HOME=/usr/lib/jvm/java-21 \
    ROCKSDB_MUSL_LIBC=false
WORKDIR /work
