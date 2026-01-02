# Helm Unused Values Checker

A Go-based tool to identify values defined in values.yaml that are not used in Helm chart templates.

## Features

- Identifies completely unused keys
- Detects values used in templates via various Helm patterns (see Registry.Builtins)
- Parallel processing for faster analysis on multi-core systems
- Supports filtering by key pattern
- Output in text or JSON format

## Installation

1. Ensure you have Go 1.22+ installed
2. Install the required dependencies:
   - `yq` - YAML processor
   - `jq` - JSON processor
   - `ripgrep` (optional, but recommended for faster searches)

3. Build the project:

```
cd scripts/helm_unused_values
go build -o helm-unused-values
```

## Usage

```
helm-unused-values [OPTIONS] <templates_dir>

or

go run ./main.go [OPTIONS <templates_dir>]
```

### Options

```
--no-colors         Disable colored output
--show-all-keys     Show all keys, not just unused ones
--json              Output results in JSON format (useful for CI)
--exit-code=CODE    Set exit code when unused values are found (default: 0)
--quiet             Suppress all output except results and errors
--usage-shell       Uses a direct shell command rather than go exec
--filter=PATTERN    Only show keys that match the specified pattern
--debug             Enable verbose debug logging
--parallelism=NUM   Number of parallel workers (0 = auto based on CPU cores)
```

### Examples

Check for unused values in a chart:

```
./helm-unused-values charts/mychart/templates
```

Filter for specific keys:

```
./helm-unused-values --filter=deployment charts/mychart/templates
```

Generate JSON output:

```
./helm-unused-values --json charts/mychart/templates
```

CI mode with exit code:

```
./helm-unused-values --ci-mode --exit-code=1 charts/mychart/templates
```

Set specific parallelism level:

```
./helm-unused-values --parallelism=4 charts/mychart/templates
```

## Project Structure

The project is organized into modular packages:

```
helm-unused-values/
├── pkg/
│   ├── config/    - Application configuration
│   ├── patterns/  - Pattern registry and pattern operations
│   ├── search/    - Key usage search functionality
│   ├── values/    - Value key extraction and operations
│   └── output/    - Display and reporting functions
└── main.go        - Entry point and command line interface
```

### Packages

- **config**: Manages application configuration and environment detection
- **patterns**: Registers and manages search patterns for Helm templates
- **search**: Implements key usage search and analysis
- **values**: Handles YAML key extraction and filtering
- **output**: Manages terminal display, progress indicators, and report formatting

## Development

### Adding New Patterns

To add new patterns for detecting used values, modify the `RegisterBuiltins` method in `pkg/patterns/registry.go`. Before this, add your test cases to the search package.

