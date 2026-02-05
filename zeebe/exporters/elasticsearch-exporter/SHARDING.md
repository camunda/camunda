# Elasticsearch Sharding Strategy

## Overview

The Elasticsearch exporter uses a combination of **index sharding** and **partition-based routing** to distribute load across Elasticsearch nodes. This document explains the sharding strategy and configuration options.

## Current Shard Configuration

As of the latest changes, index templates use the following default shard counts:

### High-Traffic Indices (3 shards)

These indices handle high write volumes during normal process execution and use 3 shards for better load distribution:

- **process-instance**: Process instance lifecycle events
- **job**: Job creation, activation, completion events  
- **user-task**: User task lifecycle events
- **variable**: Variable updates (very frequent)
- **incident**: Error/incident events
- **message**: Message send/receive events
- **message-subscription**: Message subscription events
- **decision-evaluation**: DMN decision evaluation events
- **timer**: Timer/scheduled event management
- **global-listener**: Global listener events
- **global-listener-batch**: Batched global listener events

### Low-Traffic Indices (1 shard)

These indices handle less frequent operations and use 1 shard to minimize resource usage:

- **deployment**: Process/decision deployments
- **decision**: Decision definition metadata
- **decision-requirements**: Decision requirements definition
- **error**: Error events
- **signal**: Signal events
- And 28 other specialized index types

## Partition-Based Routing

The exporter implements partition-aware routing to ensure data isolation and load distribution:

```java
// From RecordIndexRouter.java
String routingFor(final Record<?> record) {
  return String.valueOf(record.getPartitionId());
}
```

### How It Works

1. **Document Routing**: Each record is routed to a specific Elasticsearch shard based on its `partitionId`
2. **Shard Isolation**: Records from the same Zeebe partition always go to the same Elasticsearch shard
3. **Load Distribution**: With multiple shards, different partitions can be processed by different Elasticsearch nodes

### Example

With a 3-partition Zeebe cluster and 3 shards per index:

```
Zeebe Partition 1 → Elasticsearch Shard 1 
Zeebe Partition 2 → Elasticsearch Shard 2
Zeebe Partition 3 → Elasticsearch Shard 0 (wraps around via routing hash)
```

> **Note**: Elasticsearch uses the routing value to determine shard assignment via a hash function. While the mapping isn't strictly sequential, the routing ensures consistent placement.

## Configuration

### Override Default Shard Counts

You can override the default shard count for all indices via configuration:

```yaml
exporters:
  elasticsearch:
    className: io.camunda.zeebe.exporter.ElasticsearchExporter
    args:
      index:
        numberOfShards: 6  # Override default for all indices
        numberOfReplicas: 1  # Also configurable
```

### Best Practices

1. **Match or exceed partition count**: Set `numberOfShards` >= your partition count for optimal distribution
2. **Production recommendation**: Use at least 3 shards for high-traffic indices
3. **Consider cluster size**: More shards = more distribution, but also more overhead
4. **Monitor performance**: Adjust based on your workload and cluster characteristics

### Calculation Examples

| Zeebe Partitions | Recommended Shards | Rationale |
|------------------|-------------------|-----------|
| 3 | 3 | 1:1 mapping, each partition to dedicated shard |
| 6 | 6 | 1:1 mapping for isolation |
| 9 | 9 or 12 | Match partition count or use multiple of 3 |

## Impact on Performance

### Before (1 shard for most indices)

```
All partitions → Single Elasticsearch shard → Single node bottleneck
```

**Problem**: Even with partition-based routing, all data went to the same shard, overwhelming single nodes.

### After (3+ shards for high-traffic indices)

```
Partition 1 → Shard 0 → Node A
Partition 2 → Shard 1 → Node B  
Partition 3 → Shard 2 → Node C
```

**Benefit**: Load is distributed across multiple Elasticsearch nodes, preventing bottlenecks.

## Monitoring

Monitor these metrics to verify proper load distribution:

1. **Shard distribution**: Check that shards are evenly distributed across nodes
2. **Indexing rate**: Should be balanced across nodes for high-traffic indices
3. **Disk usage**: Should grow evenly across nodes
4. **CPU/Memory**: Should be balanced when shards are properly distributed

## Related

- [RecordIndexRouter.java](src/main/java/io/camunda/zeebe/exporter/RecordIndexRouter.java) - Routing implementation
- [ElasticsearchExporterConfiguration.java](src/main/java/io/camunda/zeebe/exporter/ElasticsearchExporterConfiguration.java) - Configuration options
- [Index Templates](src/main/resources/) - Default shard configurations

## References

- GitHub Issue: https://github.com/camunda/product-hub/issues/2640
- Elasticsearch Shard Documentation: https://www.elastic.co/guide/en/elasticsearch/reference/current/scalability.html
