FROM eclipse-temurin:21@sha256:6054ac3d599388ceca4916f3d0e1864e24cdd14bb760e662f740dfb9b9e3bee8

EXPOSE 8090
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY distrib /optimize

RUN groupadd camunda && useradd -ms /bin/bash -G camunda optimize && chown -R root:camunda /optimize && chmod -R g+w /optimize
USER optimize:camunda

ENTRYPOINT [ "sh", "-c", "/optimize/optimize-startup.sh" ]