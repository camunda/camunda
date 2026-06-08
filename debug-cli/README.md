# Camunda Debug CLI

The **Camunda Debug CLI** is a command-line tool for debugging and troubleshooting Camunda 8 clusters.
It allows operators and developers to inspect, print, and manipulate internal files and metadata
used by Camunda components, in particular related to Zeebe component.

## Scope

- Inspect and print Camunda cluster topology files (e.g., `.topology.meta`)
- Designed for advanced users, operators, and developers

## Usage

The CLI is currently packaged into a fat-jar, so it can be run with:

```
java -jar target/cdbg-${version}.jar  [COMMAND] [OPTIONS]
```

To improve readability, in the rest of the document we will use this alias:

```
alias debug-cli="java -jar target/cdbg-${version}.jar"
```

### Available Commands

#### `topology`

- **Description:**
  Print or edit the `.topology.meta` file that contains the dynamic cluster config configuration.
  The file is present in the root data folder of each broker.
- **Options:**
  - `-s`, `--save`: Save the JSON from standard input into the file.
  - `-f`, `--file`: Path to the `.topology.meta` file.
  - `--source`: Input file to use when saving.
- **Examples:**
  - Print the topology:

    ```
    debug-cli topology -f /path/to/topology.meta > output.json
    ```
  - Edit the topology file with a text-editor
  - Save a new topology from stdin or a file:

    ```
    debug-cli topology -s -f /path/to/topology.meta --source /path/to/input.json
    ```

#### `sbe`

- **Description:**
  Decode an SBE-encoded file to JSON using a user-provided schema.
- **Options:**
  - `-s`, `--schema`: Path to the SBE schema (`.xml` or `.sbeir`).
  - `-o`, `--offset`: Byte offset where the SBE message header starts. Defaults to `0`.
  - `-f`, `--file`: Path to the encoded input file.
  - `FILE`: Positional alternative to `--file`.
- **Examples:**

  ```
  debug-cli sbe --schema /path/to/protocol.xml /path/to/message.bin
  debug-cli sbe --schema /path/to/raft-entry-schema.xml -f /path/to/default-partition-1.meta --offset 1
  ```

#### `state`

- **Description:**
  Offline manipulation of a stopped broker's RocksDB snapshot. Each subcommand opens the source
  snapshot, applies the edit, and persists a new checksum-valid snapshot that preserves the
  index/term/processed/exported positions. The broker must be stopped while these commands run.

##### `state update-key`

- **Description:** Overwrite the next key (and optionally the max key) in the key-generator column
  family.
- **Key options:** `-r/--root`, `--runtime`, `-s/--snapshot`, `--partition-id`, `-k/--key`,
  `--max-key`.

##### `state reset-incident-position`

- **Description:**
  Reset an exporter's `lastIncidentUpdatePosition` in the `EXPORTER` column family while preserving
  its `exporterPosition`. Use this to recover after the incident-update cursor ends up ahead of the
  exported log position (e.g. a faulty backup/restore), which makes `IncidentUpdateTask` silently
  skip pending incident updates.
- **Options:**
  - `-r`, `--root`: Path of the partition folder (`…/raft-partition/partitions/<id>`); its
    `snapshots/` subdirectory holds the snapshot named by `--snapshot`.
  - `--runtime`: Path to a temporary runtime directory the command may create.
  - `-s`, `--snapshot`: Id of the source snapshot directory.
  - `-e`, `--exporter-id`: Id of the exporter whose cursor should be reset.
  - `--position`: New `lastIncidentUpdatePosition`. Defaults to `-1` (reprocess all incidents from
    the start).
  - `-v`, `--verbose`: Enable verbose output.
- **Example:**

  ```
  debug-cli state reset-incident-position -r /path/to/partition-1 \
    --snapshot 12-34-... --exporter-id camundaexporter --runtime /tmp/runtime
  ```
- **Note:** The `EXPORTER` column family is partition-local and replicated, so run this against
  **every replica** of the partition, each on its own latest snapshot. Do **not** copy the partition
  data folder across brokers — it also contains the per-replica raft journal and metadata. After
  patching, delete the previous snapshot and restart the broker.

###### Cluster runbook

The cursor lives in **every** replica's own snapshot, so the leader/follower role is irrelevant —
you must patch all replicas of every affected partition. On disk each replica lives at:

```
<data-dir>/raft-partition/partitions/<partitionId>/snapshots/<snapshotId>/
```

That per-partition folder is what you pass to `--root`; the snapshot id is the directory name under
`snapshots/`.

**Worked example — 3 partitions, 3 nodes, replication factor 2.** Replicas are spread round-robin,
so each node holds two partition replicas and there are 6 replicas to patch:

| Partition |   Replicas   |   | Node  | Hosts  |
|-----------|--------------|---|-------|--------|
| P1        | node0, node1 |   | node0 | P1, P3 |
| P2        | node1, node2 |   | node1 | P1, P2 |
| P3        | node2, node0 |   | node2 | P2, P3 |

1. **Stop all brokers.** Every node hosts replicas you are about to patch; the broker must not run
   while you edit its snapshot.
2. **Back up** each `partitions/<id>` folder so you can roll back.
3. **Patch every replica.** For each partition folder on each node, find its snapshot id
   (`ls <data-dir>/raft-partition/partitions/<id>/snapshots/`) and run the command. Use a fresh
   empty `--runtime` directory per run. For the example above that is 6 runs:

   | Run | Node  | `--root` …/partitions/ |
   |-----|-------|------------------------|
   | 1   | node0 | 1                      |
   | 2   | node0 | 3                      |
   | 3   | node1 | 1                      |
   | 4   | node1 | 2                      |
   | 5   | node2 | 2                      |
   | 6   | node2 | 3                      |

4. **Delete the stale snapshot** for each replica (the tool writes a new checksum-valid snapshot
   beside it and prints this as a next step).

5. **Restart all brokers**, confirm each partition elects a healthy leader, and verify the
   incident-update queue is draining into Elasticsearch/OpenSearch.

#### `help`

- **Description:** Show help for the CLI or any subcommand.
- **Example:**

  ```
  debug-cli --help
  debug-cli topology --help
  debug-cli sbe --help
  ```

## License

[Camunda License 1.0](../LICENSE)

