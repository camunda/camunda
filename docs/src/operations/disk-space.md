# Disk space

Zeebe uses the local disk for storage of it's persistent data. Therefore if the Zeebe broker runs out of disk space the system is in an invalid state, as the broker cannot
update it's state.

To prevent the system to end in an unrecoverable state Zeebe expects a minimum size of free disk space available. If this limit is violated the broker will reject new
requests, to allow the operations team to free more disk space and the broker to continue to update it's state.


Zeebe can be configured with the following settings for the disk usage watermarks:

* **zeebe.broker.data.diskUsageReplicationWatermark**: the fraction of used disk space before the replication is paused (default: 0.99)
* **zeebe.broker.data.diskUsageCommandWatermark**: the fraction of used disk space before new user commands are rejected (default: 0.97), this has to be less then `diskUsageReplicationWatermark`
* **zeebe.broker.data.diskUsageMonitoringInterval**: the interval in which the disk space usage is checked (default 1 second)

For **production** use cases we recommend to set the values for `diskUsageReplicationWatermark` and `diskUsageCommandWatermark` to smaller values, for example `diskUsageReplicationWatermark=0.9` and `diskUsageCommandWatermark=0.8`.
