# Gateway Health Indicators and Probes

The health status for a standalone gateway is available at `{zeebe-gateway}:8080/actuator/health`

The following health indicators are enabled by default
* Gateway Started - checks whether the Gateway is running (i.e. not currently starting and not yet shut down)
* Gateway Responsive - checks whether the Gateway can handle a request within a given timeout
* Gateway Cluster Awareness - checks whether the Gateway is aware of other nodes in the cluster
* Gateway Partition Leader Awareness - checks whether the Gateway is aware of partition leaders in the cluster
* Disk Space - checks that the free disk space is greater than 10 MB
* Memory - checks that at least 10% of max memory (heap) are still available

Health indicators are set to sensible defaults. For specific use cases, it might be necessary to customize health probes.

## Startup Probe
The started probe is available at `{zeebe-gateway}:8080/actuator/health/startup`

In the default configuration this is merely an alias for the _Gateway Started_ health indicator. Other configurations are possible (see below)

## Liveness Probe
The liveness probe is available at `{zeebe-gateway}:8080/actuator/health/liveness`

It is based on the health indicators mentioned above.

In the default configuration, the liveness probe is comprised of the following health indiactors:

* Gateway Started - checks whether the Gateway is running (i.e. not currently starting and not yet shut down)
* Liveness Gateway Responsive - checks whether the Gateway can handle a request within an ample timeout, but will only report a *DOWN* health status after the underlying health indicator is down for more than 10 minutes
* Liveness Gateway Cluster Awareness - based on Gateway cluster awareness, but will only report a *DOWN* health status after the underlying health indicator is down for more than 5 minutes
* Liveness Gateway Partition Leader Awareness - based on Gateway partition leader awareness, but will only report a *DOWN* health status after the underlying health indicator is down for more than 5 minutes
* Liveness Disk Space - checks that the free disk space is greater than 1 MB
* Liveness Memory - checks that at least 1% of max memory (heap) are still available

Note that health indicators with the *liveness* prefix are intended to be customized for the livness probe. This allows defining tighter thresholds (e.g. for free memory 1% for liveness vs. 10% for health), as well as adding tolerance for short downtimes (e.g. gateway has no awereness of other nodes in the cluster for more than 5 minutes).

## Customizing Health Probes

Global settings for all health indicators:
* `management.health.defaults.enabled=true` - enables (default) or disables all health indicators
* `management.endpoint.health.show-details=always/never` - toggles whether a summary or details (default) of the health indicators will be returned

### Startup Probe
Settings for started probe:
* `management.endpoint.health.group.startup.show-details=never` - toggles whether a summary (default) or details of the startup probe will be returned
* `management.endpoint.health.group.startup.include=gatewayStarted` - defines which health indicators are included in the startup probe

### Liveness Probe
Settings for liveness probe:
* `management.endpoint.health.group.liveness.show-details=never` - toggles whether a summary (default) or details of the liveness probe will be returned
* `management.endpoint.health.group.liveness.include=gatewayStarted,livenessGatewayResponsive,livenessGatewayClusterAwareness,livenessGatewayPartitionLeaderAwareness,livenessDiskSpace,livenessMemory` - defines which health indicators are included in the liveness probe

Note that the individual contributing health indicators of the liveness probe can be configured as well (see below).

### Gateway Started
Settings for gateway started health indicator:
* `management.health.gateway-started.enabled=true` - enables (default) or disables this health indicator

### Gateway Responsive

Settings for gateway repsonsiveness health indicator:
* `management.health.gateway-responsive.enabled=true` - enables (default) or disables this health indicator
* `management.health.gateway-responsive.requestTimeout=500ms` - defines the timeout for the request; if the test completes before the timeout, the health status is _UP_, otherwise it is _DOWN_
* `management.health.liveness.gateway-responsive.requestTimeout=5s` - defines the timeout for the request for liveness probe; if the request completes before the timeout, the health status is _UP_
* `management.health.liveness.gateway-responsive.maxdowntime=10m` - - defines the maximum downtime before the liveness health indicator for responsiveness will flip

### Gateway Cluster Awareness ###

Settings for gateway cluster awareness health indicator:
* `management.health.gateway-clusterawareness.enabled=true` - enables (default) or disables this health indicator (and its liveness counterpart)
* `management.health.liveness.gateway-clusterawareness.maxdowntime=5m` - defines the maximum downtime before the liveness health indicator for cluster awareness will flip. In other words: this health indicator will report _DOWN_ after the gateway was unaware of other members in the cluster for more than 5 minutes


### Gateway Partition Leader Awareness ###

Settings for gateway partition leader awareness health indicator:
* `management.health.gateway-partitionleaderawareness.enabled=true` - enables (default) or disables this health indicator (and its liveness counterpart)
* `management.health.liveness.gateway-partitionleaderawareness.maxdowntime=5m` - defines the maximum downtime before the liveness health indicator for partition leader awareness will flip. In other words: this health indicator will report _DOWN_ after the gateway was unaware of partition leaders for more than 5 minutes

### Disk Space
This is arguably the least critical health indicator given that the standalone gateway does not write to disk. The only exception may be the writing of log files, which depend on the log configuration.

Settings for disk space health indicator:
* `management.health.diskspace.enabled=true` - enables (default) or disables this health indicator (and its liveness counterpart)
* `management.health.diskspace.threshold=10MB` - defines the threshold for the required free disk space
* `management.health.diskspace.path=.` - defines the path for which the free disk space is examined
* `management.health.liveness.diskspace.threshold=1MB` - defines the threshold for the required free disk space for liveness
* `management.health.liveness.diskspace.path=.` - defines the path for which the free disk space for liveness is examined

### Memory
This health indicator examines free memory (heap).

Settings for memory health indicator:
* `management.health.memory.enabled=true` - enables (default) or disables this health indicator (and its liveness counterpart)
* `management.health.memory.threshold=0.1` - defines the threshold for the required free memory. The default is 0.1 which is interpreted as 10% of max memory
* `management.health.liveness.memory.threshold=0.01` - defines the threshold for the required free memory for liveness. The default is 0.01 which is interpreted as 10 of max memory
