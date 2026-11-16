FROM ubuntu:22.04
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
      g++ cmake make git ca-certificates pkg-config libfuse3-dev fuse3 openjdk-21-jdk-headless procps \
    && rm -rf /var/lib/apt/lists/*
RUN grep -q '^user_allow_other' /etc/fuse.conf || echo user_allow_other >> /etc/fuse.conf
RUN git clone --depth 1 --branch 0.3.1 https://github.com/dsrhaslab/lazyfs.git /opt/lazyfs
# LazyFS 0.3.1 does not implement statfs, so the mount reports 0 bytes free. Forward it to the
# backing directory (paths are already mapped there by the FUSE subdir module). Caching and fault
# injection are unchanged.
RUN cd /opt/lazyfs/lazyfs \
    && sed -i 's|^\(\s*\)static int lfs_fsync (const char\* path, int isdatasync, struct fuse_file_info\* fi);|&\n\1static int lfs_statfs (const char* path, struct statvfs* stbuf);|' include/lazyfs/lazyfs.hpp \
    && grep -q 'lfs_statfs' include/lazyfs/lazyfs.hpp \
    && printf '\n#include <sys/statvfs.h>\nnamespace lazyfs {\nint LazyFS::lfs_statfs (const char* path, struct statvfs* stbuf) {\n    return statvfs (path, stbuf) == -1 ? -errno : 0;\n}\n}\n' >> src/lazyfs.cpp
RUN cd /opt/lazyfs/libs/libpcache && ./build.sh && cd /opt/lazyfs/lazyfs && ./build.sh
WORKDIR /work
