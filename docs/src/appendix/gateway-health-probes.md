# Gateway Health Probes

The health probes for a standalone gateway are available at `localhost:8080/actuator/health`

The following health indicators are enabled by default
* Gateway Started - checks whether the Gateway is running (i.e. not currently starting and not yet shut down)
* Disk Space - checks that the free disk space is greater than 10 MB
* Memory - checks that at least 10% of max memory are still available

Health probes are set to sensible defaults. For specific use cases, it might be necessary to customize the health probes.

## Customizing Health Probes

Global settings for all health inidcators:
* `management.endpoint.health.show-details=always/never` - toggles whether a summary or details (default) of the health indicators will be returned
* `management.health.defaults.enabled=true` - enables (default) or disables all health indicators

### Gateway Started ###

Settings for gateway started health indicator:
* `management.health.gatewayStarted.enabled=true` - enables (default) or disables this health indicator


### Disk Space
This is arguably the least critical health indicator given that the standalone gateway does not write to disk. The only exception may be the writing of log files, which depend on the log configuration.

Settings for disk space health indicator:
* `management.health.diskspace.enabled=true` - enables (default) or disables this health indicator
* `management.health.diskspace.threshold=10MB` - defines the threshold for the required free disk space
* `management.health.diskspace.path=.` - defines the path for which the free disk space is examined

### Memory
This health indicator examines free memory.

Settings for memory health indicator:
* `management.health.memory.enabled=true` - enables (default) or disables this health indicator
* `management.health.memory.threshold=0.1` - defines the threshold for the required free emory. The default is 0.1 which is interpreted as 10% of max memory
