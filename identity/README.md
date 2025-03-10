# Identity

---

Identity is a component within the [Camunda self-managed](https://docs.camunda.io/docs/self-managed/about-self-managed/)
stack. It is responsible for authentication and authorization along with providing functionality to manage related aspects
such as users, roles, permissions, and OAuth clients.

## Running locally

- All commands should be run from the root folder of the monorepo unless stated otherwise.

1. Build project:

```
mvn clean install -DskipTests=true -DskipChecks -Dskip.fe.build=false -DskipQaBuild -T1C
```

2. Run elasticsearch by using one of the already configured docker-compose file in our codebase.

Example (using Operate's configuration):

```
docker compose -f operate/docker-compose.yml up -d elasticsearch
```

3. Run this command for some final setup and running the Zeebe broker:

```
SPRING_PROFILES_ACTIVE=consolidated-auth,broker,identity \
CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI=false \
CAMUNDA_SECURITY_AUTHENTICATION_METHOD=BASIC \
ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_TYPE=elasticsearch \
ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_URL=http://localhost:9200 \
ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CREATESCHEMA=true \
ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_INDEX_SHOULDWAITFORIMPORTERS=false \
ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_RETENTION_ENABLED=false \
ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_CLASSNAME=io.camunda.exporter.CamundaExporter \
./dist/target/camunda-zeebe/bin/camunda
```

If you would like to enable operate and tasklist, you can add their profile to the `SPRING_PROFILES_ACTIVE` variable like this:

```
SPRING_PROFILES_ACTIVE=operate,tasklist,consolidated-auth,broker,identity
```

4. Run the frontend by navigating to the `identity/client` folder and running:

```shell
yarn dev
```

