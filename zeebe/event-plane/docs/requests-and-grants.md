# Requests and grants

What each part asks of the bucket, measured and read from the code. For the cost assessment and the
IAM questions raised in the initiative's discussions.

## Grants per role

Read from the prototype's code, not from a policy that ran against AWS.

| Role | Objects | PUT | GET / HEAD | LIST | DELETE |
|---|---|---|---|---|---|
| Broker (publisher, snapshot upload) | its cluster's prefix | yes | its own history parts and snapshot descriptors | no | no |
| Broker, with `backupLog` | its tenant's `histories/`, and its logs | backup logs, under `backups/` | yes: history parts, segment footers | yes: history parts, segments | backup logs, with their backups |
| Restore (offline app) | its cluster's prefix | no | yes | yes | no |
| Maintenance | the clusters it maintains | rollups, retention markers | yes, backup logs too | yes, backup logs too | segments, markers, snapshots; never what a backup log holds |
| Worker of a group | the logs it reads | no | yes | yes | no |
| Worker of a group | its group's home: `_groups/{group}/` | heartbeats, cursors | yes | yes | its own heartbeat, on close |
| Service | everything it shows | no | yes | yes | no |

So a broker never deletes data, and only maintenance does. Without `backupLog`, a broker never lists
nor deletes at all. With it, a backup costs a few LISTs and one ranged GET per segment between the
checkpoint and the backup's first log position, walking back from the newest segment.

## Requests per hour

`RequestCountIT`: 3 partitions, 3 workers, default timings (segment every 5 s, heartbeat 5 s,
cursor 10 s, listing 60 s with the service and 1 s without), about 20 commands a second, counted
over 90 s and extrapolated. A worker holds one log here.

| Who | Request | For | With the service | Without |
|---|---|---|---:|---:|
| publisher, per partition | PUT | segments | 640 | 640 |
| worker | PUT | heartbeats | 720 | 720 |
| worker | PUT | cursors | 360 | 360 |
| worker | LIST | heartbeats | 720 | 720 |
| worker | LIST | segments | 2,680 | 5,600 |
| worker | LIST | histories | 120 | 3,640 |
| worker | LIST | retention markers | 80 | 3,600 |
| worker | LIST | discovery of logs | 240 | 2,160 |
| worker | GET | segments | 1,320 | 1,320 |
| worker | GET | heartbeats of the others | 1,413 | 1,440 |
| worker | GET | cursors, histories | 160 | 160 |
| worker, PUT + LIST in all | | | 4,920 | 16,800 |
| worker, GET in all | | | 2,893 | 2,920 |

At S3 Standard list prices (PUT, COPY, POST, LIST: $0.005 per 1,000; GET: $0.0004 per 1,000), that
is about $0.0032 an hour, $2.30 a month, per partition for publishing, and $0.026 an hour, $19 a
month, per worker with the service ($0.085 an hour without). Storage and transfer come on top.

What dominates, and what would cut it next:
- **Segment LISTs**, two per segment: one finds it, one finds nothing after it. A worker woken by a
  notice could take the key from the notice and skip both.
- **Heartbeat GETs** grow with the square of a group's workers: each reads every other one's
  heartbeat. The listing's last-modified time could tell a changed heartbeat without the GET.
- Without the service, the 1 s listing interval is what costs; a longer one costs latency.

The numbers before the reader looked for histories and retention markers only every listing
interval were 1,400 and 1,360 LISTs an hour for those two, with the service.
