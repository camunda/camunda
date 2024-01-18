FROM eclipse-temurin:21@sha256:f6a4ab7916848d0ba4cc77d690f00f81fd3aab3ef14bc3bdf23d6385eee20a14

EXPOSE 8090

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY distrib /optimize

RUN groupadd --gid 1001 camunda && \
    useradd --uid 1001 --gid 1001 -s /bin/bash camunda && \
    chown -R 1001:1001 /optimize && \
    chmod -R g+w /optimize

USER 1001:1001

ENTRYPOINT [ "sh", "-c", "/optimize/optimize-startup.sh" ]