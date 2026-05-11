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

