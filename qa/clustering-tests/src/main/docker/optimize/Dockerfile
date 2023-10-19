FROM eclipse-temurin:21@sha256:9d007c093e624bc410f113ee09f5209d3263e0bada7524e4de99eac9fbef06a5

EXPOSE 8090
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY distrib /optimize

RUN groupadd camunda && useradd -ms /bin/bash -G camunda optimize && chown -R root:camunda /optimize && chmod -R g+w /optimize
USER optimize:camunda

ENTRYPOINT [ "sh", "-c", "/optimize/optimize-startup.sh" ]