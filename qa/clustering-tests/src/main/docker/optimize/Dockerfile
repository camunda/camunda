FROM eclipse-temurin:21@sha256:47e65cc0585169c00e68b757423c72e5a64511c5f3fbc87985885bd39e1395fa

EXPOSE 8090

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY distrib /optimize

RUN groupadd --gid 1001 camunda && \
    useradd --uid 1001 --gid 1001 -s /bin/bash camunda && \
    chown -R 1001:1001 /optimize && \
    chmod -R g+w /optimize

USER 1001:1001

ENTRYPOINT [ "sh", "-c", "/optimize/optimize-startup.sh" ]