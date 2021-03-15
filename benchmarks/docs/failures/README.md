# Failures

This section should collect several common failures, we have observed on running our benchmarks.

## Problems with Elasticsearch Exporter 

If you observing that the elasticsearch exporter is not able to export data it is very likely that it is due to out of disk space issues.

Check the log if you some logs statements which are similar to the following:
```
I 2019-10-17T12:39:27.467341628Z 2019-10-17 12:39:27.467 [exporter] [zeebe-1.zeebe.default.svc.cluster.local:26501-zb-fs-workers-4] WARN  io.zeebe.broker.exporter.elasticsearch - Failed to flush bulk completely
 
I 2019-10-17T12:39:29.379064699Z 2019-10-17 12:39:29.378 [exporter] [zeebe-1.zeebe.default.svc.cluster.local:26501-zb-fs-workers-1] WARN  io.zeebe.broker.exporter.elasticsearch - Failed to flush at least one bulk request ElasticsearchException[Elasticsearch exception [type=cluster_block_exception, reason=blocked by: [FORBIDDEN/12/index read-only / allow delete (api)];]]
 
I 2019-10-17T12:39:29.379268820Z 2019-10-17 12:39:29.379 [exporter] [zeebe-1.zeebe.default.svc.cluster.local:26501-zb-fs-workers-1] WARN  io.zeebe.broker.exporter.elasticsearch - Failed to flush bulk completely
```

Please also check the grafana board or directly the nodes.

If they went out of disk space then remove some indices with curator or manually. If there are indices left then these are probably locked. To release them execute the following with the kibana console

```
PUT zeebe-record-job/_settings
{
  "index.blocks.read_only_allow_delete": null
}
PUT zeebe-record-process-instance/_settings
{
  "index.blocks.read_only_allow_delete": null
}
PUT zeebe-record-deployment/_settings
{
  "index.blocks.read_only_allow_delete": null
}
```

## Not sufficient throughput

On running several benchmarks we found out that gke limits the IO performance based on the
used disk sizes, which caused in our benchmark less performance.

Take a look at there doc's https://cloud.google.com/compute/docs/disks/performance for more information.
