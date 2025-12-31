# Docs: Multi-region Overview
[comment]: # (TODO: add a link to the multi-region official documentation when 8.5 docs are released.)
In this repo, it is possible to configure Camunda 8 to be compatible with a multi-region setup. You are able to modify the following values in the values.yaml:

```yaml
global:
  multiregion:
    regions: 1
    regionId: 0
    installationType: normal
```

The `regions` value allows you to set the total number of regions you would like to have. The default is 1 region.

The `regionId` value specifies the ID of the camunda installation in a specific region. The `regionId` **must** start from 0. For example, if you have two regions, France and England, then France will have a regionId of 0, and England will have a regionId of 1.

The `InstallationType` value can have three different types:
1. normal
2. failOver
3. failBack

These types mainly dictate how the zeebe pods behave before or after a region outage.</br>
The default is `normal`, meaning that camunda 8 will be installed normally.

When a region outage occurs and a quorum is lost between the zeebe brokers, a separate installation can be made to the surviving region with the `faillover` type. This type of installation sets up temporary zeebe brokers (with the same node IDs of the lost zeebe brokers) in the surviving region in order to establish a quorum. 

When the failed region is back to a healthy state, an installation can be made to this region with a `failback` type. This will put some zeebe brokers to sleep that have the same node ID as the temporary brokers in the surviving region.
