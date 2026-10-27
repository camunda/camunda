# Camunda Debug CLI

The **Camunda Debug CLI** is a command-line tool for debugging and troubleshooting Camunda 8 clusters.
It allows operators and developers to inspect, print, and manipulate internal files and metadata
used by Camunda components, in particular related to Zeebe component.

## Scope

- Inspect and print Camunda cluster topology files (e.g., `.topology.meta`)
- Recover lost secondary-storage data (ES/OS) from a Zeebe primary-storage snapshot
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
  - `-e`, `--exporter-id`: Id the exporter is configured under in `zeebe.broker.exporters`
    (default: `camundaexporter`).
  - `--position`: New `lastIncidentUpdatePosition` (required). Use `-1` to reprocess all incidents
    from the start.
  - `-v`, `--verbose`: Enable verbose output.
- **Example:**

  ```
  debug-cli state reset-incident-position -r /path/to/partition-1 \
    --snapshot 12-34-... --position -1 --runtime /tmp/runtime
  ```
- **Note:** The `EXPORTER` column family is partition-local and replicated, so run this against
  **every replica** of the partition, each on its own latest snapshot. Do **not** copy the partition
  data folder across brokers — it also contains the per-replica raft journal and metadata. After
  patching, delete the previous snapshot and restart the broker.

  The full cluster runbook, including scripts to generate the Kubernetes Jobs that apply the fix
  to all broker PVCs, lives in
  [scripts/reset-incident-position](./scripts/reset-incident-position/README.md).

##### `state check-process-definition-deletions`

- **Description:**
  Find process definitions whose deletion did not fully apply across the cluster, leaving them stuck
  and unable to complete.

  A cluster stores its data in partitions. When you delete a process definition, the deletion is
  first applied on partition `1` (the partition that coordinates deployments and deletions) and then
  forwarded to the others. A definition that still has running instances enters a `DRAINING` state:
  it is scheduled for deletion and removed once its last instance finishes.

  To finish a deletion, partition `1` keeps a coordination entry for each partition it is still
  waiting on. An older defect could leave a definition `DRAINING` on a partition that partition `1`
  has no such entry for: the coordination was never set up, so nothing will ever finish the deletion.
  The definition stays stuck, and its data in Elasticsearch or OpenSearch no longer matches the
  engine.

  This command reads every partition's data and reports each definition that is `DRAINING` on a
  partition that partition `1` is not tracking for deletion. That mismatch — not merely a definition
  missing from partition `1`, which also happens during a normal deletion — is the signature of a
  stuck definition. Nothing is modified; the data is only read.

  Run it if you deleted process definitions on an older version and suspect that some deletions did
  not take effect everywhere. When a definition is found, the report explains how to finish its
  deletion.

- **Options:**

  - `-r`, `--root`: Path of the `raft-partition/partitions` directory, which holds one numbered
    subdirectory per partition. It **must contain the data of all partitions** in the cluster,
    including partition `1`. In a cluster with more than one broker, each broker stores only some of
    the partitions, so first collect every partition's directory into a single `partitions/`
    directory. The command reads the cluster's partition list from partition `1` and refuses to run
    if any partition's data is missing, so an incomplete set is reported rather than scanned. It
    reads each partition's latest snapshot itself.
  - `--runtime`: Optional temporary working directory the data is copied into before reading. A fresh
    temporary directory is created and deleted automatically if omitted.
- **Output:** a readable report is printed to **stderr**. A machine-readable summary is printed to
  **stdout**: one line per stuck definition
  (`key=.. bpmnProcessId=.. version=.. tenant=.. draining=[..] uncoordinated=[..] orphanedInstances=..`,
  where `draining` lists the partitions still holding the definition, `uncoordinated` lists those of
  them that partition `1` is not tracking for deletion, and `orphanedInstances` shows whether running
  instances of the definition still remain), followed by a summary line
  (`stranded=.. partitions=[..]`). The exit code is `0` when nothing is stuck, `2` when stuck
  definitions are found, and `1` on a configuration or I/O error (including missing partition data).
- **Consistency:** the check compares state *across* partitions, so it needs their snapshots to
  line up. Run it against a **stopped cluster**, or against snapshots gathered from all partitions
  at about the same point in time.
- **Example:**

  ```
  debug-cli state check-process-definition-deletions \
    -r /usr/local/camunda/data/raft-partition/partitions
  ```
- **Note:** Finding no stuck definitions is the expected result.

#### `recover`

- **Description:**
  Recover secondary-storage (Elasticsearch/OpenSearch) data from Zeebe primary storage (a RocksDB
  snapshot), for disaster recovery when secondary-storage documents have been lost.

##### `recover process-definitions`

- **Description:**
  Re-export **ACTIVE** process definitions (and their embedded start forms) from a **partition-1**
  snapshot into secondary storage. Partition 1 is the deployment partition, so its process state is
  a complete superset of all deployments across all tenants, and every process-definition key is the
  id of the corresponding secondary-storage document. The command drives the *real* exporter
  handlers, so the recovered documents are identical to normally-exported ones.

  The snapshot is read strictly read-only (copied into a throwaway runtime first), so the command can
  be run **in-pod on a live broker** that hosts a partition-1 replica, leader or follower — **no
  outage required**.
  Definitions already present are skipped unless `--override` is given, so the command is idempotent
  and safe to re-run. Standalone forms and DMN decisions are out of scope.

- **Read options:**

  - `-r`, `--root`: Path of the partition-1 directory (the folder containing `snapshots/`), e.g.
    `<data>/raft-partition/partitions/1`.
  - `-s`, `--snapshot`: Id of the snapshot directory to read (a sub-directory of `<root>/snapshots/`).
  - `--runtime`: Optional temporary runtime directory the snapshot is copied into before reading. A
    fresh temp directory is created and deleted automatically if omitted.
- **Connection options** (default from the container env vars shown in parentheses):
  - `--connect-type`: `elasticsearch`|`opensearch` (`CAMUNDA_DATABASE_TYPE`, default
    `elasticsearch`).
  - `--connect-url`: Secondary storage URL (`CAMUNDA_DATABASE_URL`, default `http://localhost:9200`).
  - `--connect-username` / `--connect-password` (`CAMUNDA_DATABASE_USERNAME` /
    `CAMUNDA_DATABASE_PASSWORD`).
  - `--index-prefix`: Index prefix of the target installation (`CAMUNDA_DATABASE_INDEXPREFIX`). **This
    is the main footgun** — a wrong prefix targets a non-existent index; the command **fails fast** if
    the target process index does not already exist.
  - TLS: `--connect-security-enabled`, `--connect-security-certificate-path`,
    `--connect-security-verify-hostname`, `--connect-security-self-signed`.
- **Behavior options:**
  - `--override`: Rewrite definitions that already exist (default: only write missing ones).
  - `--dry-run`: Compute and report the diff without writing anything.
  - `--batch-size`: Definitions per bulk request (default `50`).
- **Output:** human-readable progress and the final summary go to **stderr**; a machine-readable
  summary line (`total=.. present=.. written=.. skipped=.. failed=..`) goes to **stdout**. Exit code
  `0` on success, `2` if any definition failed to recover, `1` on a configuration/validation error.
- **Example (in-pod, connection defaulted from the container env):**

  ```
  # find the latest partition-1 snapshot id
  ls /usr/local/camunda/data/raft-partition/partitions/1/snapshots/

  debug-cli recover process-definitions \
    -r /usr/local/camunda/data/raft-partition/partitions/1 \
    -s <snapshot-id> --dry-run
  ```
- **Note:** RDBMS secondary storage is not yet supported (Elasticsearch/OpenSearch only).

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

