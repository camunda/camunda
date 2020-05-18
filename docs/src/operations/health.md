# Health Status

## Broker
Zeebe broker exposes two http endpoints to query its health status.
* Ready check
* Health check

### Ready Check
Ready check endpoint is exposed via `http://{zeebe-broker}:{zeebe.broker.network.monitoringApi.port}/ready` (by default port 9600).
This endpoint return an empty 204 response. If it is not ready, it will return a 503 error.

A broker is ready when it has installed all necessary services to start processing in all partitions.
A broker is ready doesn't mean that it is the leader for the partitions.
It only means that it is participating in the replication and can be either a leader or a follower of all the partitions that is assigned to it.
Once it is ready it will never become unready again.

A ready check is useful, for example, to use as a `readinessProbe` in a kubernetes configuration to control when a pod can be restarted for rolling upgrade.
Depending on the cluster configuration, restarting one pod before the previous one is ready might make the system unavailable because the quorum of replicas is not available.
By configuring a `readinessProbe` that uses the ready check endpoint we can inform Kubernetes when it is safe to proceed with rolling update.

### Health Check
Health check endpoint is exposed via `http://{zeebe-broker}:{zeebe.broker.network.monitoringApi.port}/health` (by default port 9600).
This endpoint return an empty 204 response if the broker is healthy. If it is not healthy, it will return a 503 error.
A broker is never healthy before it is ready.
Unlike ready check, a broker can become unhealthy after it is healthy.
Hence it gives a better status of a running broker.

A broker is healthy, when it can process workflows, accepts commands, and perform all its expected tasks.
If it is unhealthy, then it can mean three things:
 * **it is only temporarily unhealthy**, e.g. due to environmental circumstances such as temporary I/O issues
 * **it is partially unhealthy**, could mean that one or more partitions is unhealthy, while the rest of them are able to process workflows
 * **it is completely dead**

[Metrics](/operations/metrics.html) give more insight into which partition is healthy or unhealthy.
When a broker becomes unhealthy, it is recommended to check the logs to see what went wrong.


## Gateway

Zeebe gateway exposes three HTTP endpoints to query its health status:

* Health Status - `http://{zeebe-gateway}:9600/health`
* Startup Probe - `http://{zeebe-gateway}:9600/actuator/health/startup`
* Liveness Probe - `http://{zeebe-gateway}:9600/actuator/health/liveness`

(The default port can be changed in the configuration: `{zeebe.gateway.monitoring.port}`)


### Health Status

The gateway is healthy if it:
 * Started successfully
 * Has sufficient free memory and disk space to work with
 * Is able to respond to requests within a defined timeout
 * Is aware of other nodes in the cluster
 * Is aware of leaders for partitions

### Startup Probe

The gateway is started if it finished its boot sequence successfully and is ready to receive requests. It is no longer started when it initiated the shutdown sequence.

The started probe can be used as Kubernetes startup probe.

### Liveness Probe

The gateway is live if it:
* Started successfully
* Has a minimal amount of free memory and disk space to work with
* Is able to respond to requests within a defined timeout, or misses the timeout for less than 10 minutes
* Is aware of other nodes in the cluster, or lost awareness of other nodes for less than 5 minutes
* Is aware of leaders for partitions, or lost awareness of partition leaders for less than 5 minutes

The liveness probe can be used as Kubernetes liveness probe.

### Status Responses
Each endpoint returns a status which can be one of
* `UNKNWON` (HTTP status code 200)
* `UP` (HTTP status code 200)
* `DOWN` (HTTP status code 503)
* `OUT_OF_SERVICE` (HTTP status code 503)

If details are enabled (default) the response will also contain additional details.

### Customization
Health indicators are set to sensible defaults. For specific use cases, it might be necessary to [customize health indicators](/appendix/gateway-health-probes.md).
