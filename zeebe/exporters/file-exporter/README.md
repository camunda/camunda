# File Exporter

Writes every record, unfiltered (commands, events and rejections of all value types), as one JSON
line into local files. It is meant for offline analysis of the raw records of a cluster, for
example a load test, without an external sink: point it at a volume of the broker pod and copy the
files out afterwards.

Records are written to size-rolled files, one directory per physical tenant and partition:

```
<directory>/<physicalTenantId>/partition-<partitionId>/records-<firstPosition>.ndjson
```

## Configuration

|        Argument        |               Default                |                Description                 |
|------------------------|--------------------------------------|--------------------------------------------|
| `directory`            | none (required)                      | Base directory to write the files into     |
| `maxFileSizeBytes`     | `134217728` (128 MiB)                | Size after which a new file is started     |
| `flushInterval`        | `PT1S`                               | How often the files are flushed and the exported position is acknowledged |

For example, via environment variables:

```bash
CAMUNDA_DATA_EXPORTERS_FILE_CLASSNAME=io.camunda.exporter.file.FileExporter
CAMUNDA_DATA_EXPORTERS_FILE_ARGS_DIRECTORY=/usr/local/camunda/logs/records
```

Copy the files out of a broker pod with:

```bash
kubectl cp <namespace>/<pod>:/usr/local/camunda/logs/records ./records
```

## Caveats

- **At-least-once**: a position is only acknowledged once flushed, so after a restart the records
  written since the last flush are written again. Deduplicate on `(partitionId, position)`.
- **Disk usage**: nothing is ever deleted. The exporter stops when the volume is full, which also
  blocks log compaction on the broker. Keep runs short and watch the volume.
- **Only records still in the log**: a newly added exporter starts from the oldest record that has
  not been compacted yet, not from the beginning of the cluster's history.
