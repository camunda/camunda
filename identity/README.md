# Identity

---

Identity is a component within the [Camunda self-managed](https://docs.camunda.io/docs/self-managed/about-self-managed/)
stack. It is responsible for authentication and authorization along with providing functionality to manage related aspects
such as users, roles, permissions, and OAuth clients.

## Running locally

**NOTE:** Currently, this is NOT working with OIDC!
For this you have to start the frontend within the orchestration cluster.
See [Running within the Orchestration Cluster](#running-within-the-orchestration-cluster)

All commands should be run from the root folder of the monorepo unless stated otherwise.

1. Build project:

```
mvn clean install -DskipTests=true -DskipChecks -Dskip.fe.build=false -DskipQaBuild -T1C
```

#### With Elasticsearch

2. Run elasticsearch by using one of the already configured docker-compose file in our codebase.

Example (using Operate's configuration):

```
docker compose -f operate/docker-compose.yml up -d elasticsearch
```

3. Run the application with initial setup variables:

```
SPRING_PROFILES_ACTIVE=consolidated-auth,broker,identity,elasticsearch \
./dist/target/camunda-zeebe/bin/camunda
```

#### With RDBMS (H2)

2. No container needed as H2 is embedded.

3. Run the application with initial setup variables:

```
SPRING_PROFILES_ACTIVE=consolidated-auth,broker,identity,rdbmsH2 \
./dist/target/camunda-zeebe/bin/camunda
```

### Running the frontend

1. Run the frontend in another terminal tab by navigating to the `identity/client` folder and running:

```shell
npm run dev
```

2. Navigate to `http://localhost:5173/admin` in your browser. (Port could be different depending on your configuration, see the terminal when starting it.)

### Variations

To have a demo user created by default, add the following env variables when running the application:

```
export CAMUNDA_SECURITY_INITIALIZATION_USERS_0_USERNAME=demo \
CAMUNDA_SECURITY_INITIALIZATION_USERS_0_PASSWORD=demo \
CAMUNDA_SECURITY_INITIALIZATION_USERS_0_NAME=Demo \
CAMUNDA_SECURITY_INITIALIZATION_USERS_0_EMAIL=demo@example.com \
CAMUNDA_SECURITY_INITIALIZATION_DEFAULTROLES_ADMIN_USERS_0=demo
```

To have authorizations enabled by default, add the following env variable when running the application:

```
CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED=true \
```

If you would like to enable operate and tasklist, you can add their profile to the `SPRING_PROFILES_ACTIVE` variable like this:

```
SPRING_PROFILES_ACTIVE=operate,tasklist,consolidated-auth,broker,identity,elasticsearch  \
./dist/target/camunda-zeebe/bin/camunda
```

## Running within the Orchestration Cluster

```
SPRING_PROFILES_ACTIVE=broker,identity,consolidated-auth \
./dist/target/camunda-zeebe/bin/camunda
```

