FROM eclipse-temurin:21@sha256:798e8ee1da62d926613189d41448b64056428781ba659d5a70f23e7f06073f2f

EXPOSE 8090
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY distrib /optimize

RUN groupadd camunda && useradd -ms /bin/bash -G camunda optimize && chown -R root:camunda /optimize && chmod -R g+w /optimize
USER optimize:camunda

ENTRYPOINT [ "sh", "-c", "/optimize/optimize-startup.sh" ]