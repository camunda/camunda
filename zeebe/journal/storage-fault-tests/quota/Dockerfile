FROM lazyfs-camunda:8.8.41
RUN apt-get update && apt-get install -y --no-install-recommends python3-minimal && rm -rf /var/lib/apt/lists/*
COPY quota_patch.py /tmp/quota_patch.py
RUN python3 /tmp/quota_patch.py /opt/lazyfs/lazyfs/src/lazyfs.cpp \
    && cd /opt/lazyfs/lazyfs && ./build.sh
