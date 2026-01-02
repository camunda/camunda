# Search Package

This package provides functionality for analyzing key usage in Helm templates, with a focus on finding unused values.

## Functionality

### Key Features

1. **Pattern-based search**: Detect values used through various Helm patterns
2. **Parallel processing**: Distribute work across multiple worker goroutines
3. **Progress tracking**: Visual progress indicators for long-running operations

## Design Decisions

### Parallel Processing

The analysis is performed in three phases:

1. Initial key extraction
2. Direct Pattern matching (parallelized)
3. Indirect Pattern matching (parallelized)

### Search Implementation

Two search strategies are available, with automatic fallback:

1. Direct command execution (ripgrep or grep)
2. Shell-based execution

### Pattern Handling

Custom pattern definitions are used to detect values used in various ways:

- toYaml function calls
- Helm include directives
- Context objects
- Image parameters

## Usage

The primary method to use is `FindUnusedKeys`, which analyzes a list of keys
and returns information about their usage status in the templates.

