# Camunda Debug CLI

The **Camunda Debug CLI** is a command-line tool for debugging and troubleshooting Camunda 8 clusters.
It allows operators and developers to inspect, print, and manipulate internal files and metadata
used by Camunda components, in particular related to Zeebe component.

## Scope

- Inspect and print Camunda cluster topology files (e.g., `.topology.meta`)
- Designed for advanced users, operators, and developers

## Usage

Run the CLI with:

```
debug-cli [COMMAND] [OPTIONS]
```

### Available Commands

#### `topology`
- **Description:** Print or edit the `topology.meta` file from the dynamic-config module.
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

#### `help`
- **Description:** Show help for the CLI or any subcommand.
- **Example:**
  ```
  debug-cli --help
  debug-cli topology --help
  ```


## License

[Camunda License 1.0](../LICENSE)

