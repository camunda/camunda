FROM eclipse-temurin:11

EXPOSE 8090
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

ADD distrib /optimize

RUN groupadd camunda && useradd -ms /bin/bash -G camunda optimize && chown -R root:camunda /optimize && chmod -R g+w /optimize
USER optimize:camunda

ENTRYPOINT [ "sh", "-c", "/optimize/optimize-startup.sh" ]