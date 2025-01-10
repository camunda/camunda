# Identity

---

Identity is a component within the [Camunda self-managed](https://docs.camunda.io/docs/self-managed/about-self-managed/)
stack. It is responsible for authentication and authorization along with providing functionality to manage related aspects
such as users, roles, permissions, and OAuth clients.

## Running locally

1. Build project from the root folder of the monorepo using:

```
mvn clean install -U --projects dist --also-make -DskipTests -Dskip.fe.build=false
```

2. Run elasticsearch by using one of the already configured docker-compose file in our codebase.

Example: navigate to Operate's folder in the codebase and run:

```
docker compose -f config/docker-compose.yml up -d elasticsearch
```

3. Run this command for some final setup:

```
CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED=true \
ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_CLASSNAME=io.camunda.exporter.CamundaExporter \
CAMUNDA_REST_QUERY_ENABLED=true \
./dist/target/camunda-zeebe/bin/broker
```

4. Run the frontend by navigating to the `identity/client` folder and running:

```shell
yarn dev
```

