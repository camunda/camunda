FROM eclipse-temurin:21@sha256:3c7a92219be3ece03e97c3704f11132f9f0a21ce2c00f13d05f2b869a84a7d9c

EXPOSE 8090

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY distrib /optimize

RUN groupadd --gid 1001 camunda && \
    useradd --uid 1001 --gid 1001 -s /bin/bash camunda && \
    chown -R 1001:1001 /optimize && \
    chmod -R g+w /optimize

USER 1001:1001

ENTRYPOINT [ "sh", "-c", "/optimize/optimize-startup.sh" ]