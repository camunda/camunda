FROM eclipse-temurin:21@sha256:b87713d2fdd11faa1f283aec70b194ccf98013257662658575c120408793b600

EXPOSE 8090

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY distrib /optimize

RUN groupadd --gid 1001 camunda && \
    useradd --uid 1001 --gid 1001 -s /bin/bash camunda && \
    chown -R 1001:1001 /optimize && \
    chmod -R g+w /optimize

USER 1001:1001

ENTRYPOINT [ "sh", "-c", "/optimize/optimize-startup.sh" ]