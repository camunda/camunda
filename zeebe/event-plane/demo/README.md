# Trying the Event Plane in the bucket

Three brokers publish into MinIO, the service passes the uploads on to a consumer group of three
workers, and maintenance rolls the segments up a minute after they arrive.

## Build

From the repository root:

```sh
./mvnw install -T1C -DskipChecks -DskipTests -PskipFrontendBuild -pl dist,zeebe/event-plane/demo -am
docker build --tag camunda/camunda:event-plane-trial --build-arg DISTBALL='dist/target/camunda-zeebe*.tar.gz' \
  --build-arg BASE=public --target app --file ./camunda.Dockerfile .
docker build --tag event-plane-tools:trial zeebe/event-plane/demo
```

## Run

```sh
cd zeebe/event-plane/demo
docker compose up -d
./load.sh 20 600                          # 20 messages a second, for ten minutes
docker compose logs -f worker-1 worker-2 worker-3
```

Every ten seconds each worker prints, per log it holds, how many records it read, how many came
again, and how old the newest one was when it arrived.

## Look around

- The bucket: <http://localhost:9001> (minio / minio-secret), bucket `event-plane`:
  `event-plane-trial/default/{partition}/{history}/` holds the segments, `histories/` the
  manifests, `_groups/demo/` the workers' heartbeats and cursors.
- The group, as the service sees it: `curl -s localhost:8085/v1/groups/demo | jq`.
- The floor: `curl -s localhost:9600/actuator/partitions | jq`.

## Break things

- Kill a worker: `docker compose kill worker-2`. Its logs move to the others after the lease (30
  s), from its cursors. `docker compose start worker-2` gives some back.
- Close a worker: `docker compose stop worker-3`. Its logs move at once.
- Kill a leader: `docker compose kill broker-0` (it also serves the REST API: point `load.sh` at
  another broker by publishing their port 8080). The new leader resumes after the floor.
- Restart the service: `docker compose restart service`. Workers reconnect, and read what they
  missed.
- Stop maintenance, or let it run: level-1 rollups replace level-0 segments under the readers.

## Clean up

```sh
docker compose down -v
```
