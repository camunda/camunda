FROM lazyfs-camunda:8.8.41
ARG JFS_VERSION=1.2.1
ARG TARGETARCH
RUN curl -fsSL -o /tmp/jfs.tgz https://github.com/juicedata/juicefs/releases/download/v${JFS_VERSION}/juicefs-${JFS_VERSION}-linux-${TARGETARCH}.tar.gz \
    && tar -xzf /tmp/jfs.tgz -C /usr/local/bin juicefs && rm /tmp/jfs.tgz && juicefs version
