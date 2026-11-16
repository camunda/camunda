FROM lazyfs-camunda:8.8.41
RUN apt-get update && apt-get install -y --no-install-recommends nfs-kernel-server nfs-common rpcbind kmod e2fsprogs xfsprogs dmsetup util-linux && rm -rf /var/lib/apt/lists/*
