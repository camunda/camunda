FROM eclipse-temurin:21@sha256:9fcac9db90b6e142a462110c3730f0d43cb654350fa23bedee7566b93697b636

EXPOSE 8090

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY distrib /optimize

RUN groupadd --gid 1001 camunda && \
    useradd --uid 1001 --gid 1001 -s /bin/bash camunda && \
    chown -R 1001:1001 /optimize && \
    chmod -R g+w /optimize

USER 1001:1001

ENTRYPOINT [ "sh", "-c", "/optimize/optimize-startup.sh" ]