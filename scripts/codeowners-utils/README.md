# Codeowners Utilities

Utilities for analyzing codeowners and test classification in the Camunda monorepo.

## Purpose

Enable generating CI test jobs scoped by team ownership by providing a comprehensive inventory of all tests with their ownership and classification.

## Requirements

- **Node.js** - The scripts are written in JavaScript using Node.js
- **`.codeowners` file** - Must be present at the repository root (or anywhere in the directory tree above where the script is run). The script will throw an error if no `.codeowners` file is found.
- **`codeowners-cli`** (required) - Required for ownership resolution. The script will fail with a clear error if not installed.
  - Install from: https://github.com/multimediallc/codeowners-plus
  - Or use the GitHub Action: `.github/actions/codeowners-setup-cli`
  - The script automatically detects `codeowners-cli` using multiple methods:
    1. Direct PATH lookup (for global installations)
    2. User's shell environment with `-i` flag (for shell aliases in zsh/bash)
    3. Local `node_modules/.bin` directory
    4. npx fallback (for local npm installations)

The script automatically traverses up the directory tree from where it's executed to find the `.codeowners` file. If no file is found or if `codeowners-cli` is not installed, the script will exit with a clear error message.

## Scripts

### Test Collection (`collect-tests.js`)

Inventories all Java test files in the repository, mapping each to its codeowners and test type.

The script can be run from any directory in the repository - it automatically traverses up the directory tree to find the `.codeowners` file at the repository root.

The script uses an enrichment chain of strategies that process files in order.

**Usage Modes**:
- **Quiet mode** (default): Only outputs the final results list - ideal for piping to other tools
- **Verbose mode** (`-v`): Shows progress updates, per-strategy metrics, and timing breakdown

```bash
# Run from the scripts/codeowners-utils directory
cd scripts/codeowners-utils

# Basic usage - quiet mode (default: only show results)
npm run collect-tests

# Verbose mode - show progress, metrics, and timing
npm run collect-tests -- -v

# Or run from any directory in the repository
cd /path/to/anywhere/in/repo
node scripts/codeowners-utils/collect-tests.js

# Save results to output files
npm run collect-tests -- --output

# Verbose with progress and metrics
npm run collect-tests -- -v --output

# JSON output to console (quiet mode)
npm run collect-tests -- --format json

# JSON output with verbose metrics
npm run collect-tests -- -v --format json --output

# Use annotation + location strategies (most comprehensive)
npm run collect-tests -- --strategies annotation,location --output

# Use all strategies in sequence with progress
npm run collect-tests -- -v --strategies naming,annotation,location --output

# Disable summary file generation (when --output is enabled)
npm run collect-tests -- --output --no-summary

# Skip owner resolution for faster execution
npm run collect-tests -- --skip-owners

# Show help
npm run collect-tests -- --help
```

### Test Metrics (`calculate-metrics.js`)

Calculates and displays statistics about test distribution.

```bash
npm run test-metrics
```

## Output Format

### Console Output

By default, the script runs in **quiet mode** and only outputs the final results list. This is ideal for piping to other tools or when you only need the data.

Use the `-v` or `--verbose` flag to see:
- Progress updates during each phase (scanning, classification, ownership assignment)
- Per-strategy metrics showing what each strategy contributed
- Timing breakdown for performance analysis
- Summary statistics and warnings

Use `--output` to save results to files in `scripts/codeowners-utils/output/`.

### Text Format (default)

```
<relative-path> | <owner(s)> | <test-type>
```

Example:

```
zeebe/engine/src/test/java/io/camunda/zeebe/engine/EngineTest.java | @camunda/core-features | unit
qa/acceptance-tests/src/test/java/io/camunda/it/client/ClientIT.java | @camunda/camundaex | acceptance
```

### JSON Format

```json
[
  {
    "path": "zeebe/engine/src/test/java/io/camunda/zeebe/engine/EngineTest.java",
    "owners": ["@camunda/core-features"],
    "testType": "unit"
  }
]
```

### Output Files

When using the `--output` flag, the script generates multiple files in `scripts/codeowners-utils/output/`:

- **`result-<strategies>.txt`** - Text format results
- **`result-<strategies>.json`** - JSON format results
- **`utilities-<strategies>.txt`** - Text format utilities list
- **`utilities-<strategies>.json`** - JSON format utilities list
- **`warnings-<strategies>.txt`** - Text format warnings
- **`warnings-<strategies>.json`** - JSON format warnings
- **`summary-<strategies>.txt`** - Human-readable summary (unless --no-summary)
- **`summary-<strategies>.json`** - Machine-readable summary (unless --no-summary)

Where `<strategies>` is a joined string of strategy names used (e.g., `naming+location`).

## Strategy-Based Classification

The script uses an **enrichment chain pattern** where each strategy can enrich the classification from previous strategies. Strategies are applied in the order specified.

### Classification Result Structure

Each file is classified with:
- **`type`**: `'test' | 'utility' | 'other'` - What kind of file it is
- **`testType`**: `'unit' | 'integration' | 'acceptance' | 'other' | null` - What type of test it is (only for tests)

### Available Strategies

#### 1. Naming Strategy (`naming`)

**Purpose**: Identifies tests and utilities by filename patterns (Maven naming conventions).

**What it discovers**:
- **Tests** by filename suffix:
- `*Test.java` → unit test
- `*Tests.java` → unit test
- `*TestCase.java` → unit test
- `Test*.java` → unit test
- `*IT.java` → integration test
- `IT*.java` → integration test
- `*ITCase.java` → integration test
- **Utilities** by filename patterns:
- `TestUtil.java`, `TestData.java`, etc.
- `TestController.java`, `TestService.java` (but NOT `TestUserService.java`)
- Interfaces with test-like names

**Characteristics**:
- **Fastest** - Only checks filenames, minimal file content reading
- **Sets both type and testType** - Identifies if it's a test/utility AND what kind
- **Precise fixture detection** - Distinguishes `TestController` (utility) from `TestUserController` (test)

**Does NOT read**: File content (except for interface detection)

#### 2. Annotation Strategy (`annotation`)

**Purpose**: Identifies tests and utilities by JUnit annotations in file content.

**What it discovers**:
- **Tests** by annotations:
- `@Test` (JUnit 4 and JUnit 5)
- `@ParameterizedTest` (JUnit 5)
- `@RepeatedTest` (JUnit 5)
- `@TestFactory` (JUnit 5)
- `@TestTemplate` (JUnit 5)
- **Utilities** by annotations:
- `@TestConfiguration` (Spring test configuration)

**Characteristics**:
- **Slowest** - Reads every Java file in test directories
- **Comprehensive** - Finds tests regardless of naming convention
- **Preserves testType** - Doesn't override previous classifier's testType

**Does NOT detect**: Test type (unit vs integration) - only detects IF it's a test

#### 3. Location Strategy (`location`)

**Purpose**: Classifies test type based on directory path location only.

**What it does**:
- **Enriches testType** for tests based on directory:
- `qa/acceptance-tests/` → acceptance
- `qa/` → integration
- `zeebe/qa/` → integration
- `operate/qa/` → integration
- `tasklist/qa/` → integration
- **Overrides previous testType** - Location-based rules take precedence

**Characteristics**:
- **Fast** - Only checks path patterns, no file access
- **Only classifies testType** - Doesn't determine if something is a test
- **Overriding** - Can change testType set by earlier strategies

**Does NOT use**: Filename patterns (only directory path)

### Strategy Combinations

#### `naming,location` (Default)

```bash
npm run collect-tests
```

**How it works**:
1. Naming strategy identifies tests by filename and sets initial testType
2. Location strategy overrides testType for QA directories

**Best for**: Standard repositories following Maven conventions

#### `annotation,location`

```bash
npm run collect-tests -- --strategies annotation,location
```

**How it works**:
1. Annotation strategy finds all files with `@Test` annotations
2. Location strategy sets testType based on directory

**Best for**: Finding all tests regardless of naming, or when tests don't follow Maven conventions

#### `naming,annotation,location`

```bash
npm run collect-tests -- --strategies naming,annotation,location
```

**How it works**:
1. Naming strategy identifies tests by filename and sets testType
2. Annotation strategy adds tests missed by naming (e.g., non-standard names)
3. Location strategy overrides testType for QA directories

**Best for**: Maximum coverage with warnings for non-standard naming

### Comparison

|     Strategy Combination     |  Speed   |     Finds Tests By     | Sets testType By |
|------------------------------|----------|------------------------|------------------|
| `naming,location` (default)  | Fastest  | Filename only          | Suffix + Path    |
| `annotation,location`        | Slowest  | Annotations only       | Path only        |
| `naming,annotation,location` | Moderate | Filename + Annotations | Suffix + Path    |

## Test Naming Conventions

### ⚠️ IMPORTANT: QA Modules

**In `qa/`, `zeebe/qa/`, `operate/qa/`, and `tasklist/qa/` modules:**

- **Surefire is SKIPPED** (no unit tests run via surefire)
- **ALL tests run via Failsafe** (integration test plugin)
- **Even `*Test.java` files are integration/acceptance tests**

This means a file named `SomethingTest.java` under `zeebe/qa/` is an **integration test**, not a unit test, because of how Maven is configured in those modules.

### Acceptance Tests

Any test under `qa/acceptance-tests/` (regardless of naming)

## Test Type Classification Priority

The enrichment chain processes files in order, with each strategy enriching previous results:

### 1. Naming Classifier (Optional)

If included, sets initial test identification and test type:
- `*Test.java`, `Test*.java` → unit test
- `*IT.java`, `IT*.java` → integration test
- `TestUtil`, `TestData`, `TestController` → utility

### 2. Annotation Classifier (Optional)

If included, adds tests missed by naming:
- Files with `@Test` annotations → test (preserves testType from naming if set)
- Files with `@TestConfiguration` → utility

### 3. Location Classifier (Recommended)

If included, overrides testType based on path:
- `qa/acceptance-tests/` → acceptance (overrides any previous testType)
- `qa/`, `zeebe/qa/`, etc. → integration (overrides any previous testType)

### 4. Fallback

Any test without a testType → `other` (generates warning)

## Architecture

The codebase uses the **Strategy Pattern** with an **Enrichment Chain**:

### Core Files

- **`lib/classifier-strategy.js`**: Base strategy interface
- **`lib/naming-strategy.js`**: Naming pattern-based classifier
- **`lib/annotation-strategy.js`**: Annotation-based classifier
- **`lib/location-strategy.js`**: Location-based classifier
- **`lib/collector.js`**: Orchestrates the enrichment chain
- **`lib/test-finder.js`**: Scans filesystem for Java test files

### How the Enrichment Chain Works

```javascript
import { collectTests } from './lib/collector.js';
import { NamingStrategy } from './lib/naming-strategy.js';
import { LocationStrategy } from './lib/location-strategy.js';

// Define strategy chain
const strategies = [
  new NamingStrategy(),    // Sets type + testType
  new LocationStrategy()   // Enriches/overrides testType
];

// Execute chain
const result = await collectTests(repoRoot, strategies);
// result = { tests: [...], utilities: [...], warnings: [...] }
```

Each strategy receives the current classification and can:
- Leave it unchanged (pass through)
- Enrich it (add information)
- Override it (change information)

### Strategy Interface

```javascript
class TestClassifierStrategy {
  async classify(file, currentClassification) {
    // file: { path, fullPath }
    // currentClassification: { type, testType }
    // returns: { type, testType }
  }

  getName() {
    // returns: strategy name string
  }
}
```

## Utilities vs Tests

The classifier distinguishes between:

- **Tests**: Files that contain actual test methods (`@Test` annotations or matching test naming patterns)
- **Utilities**: Helper classes, test fixtures, test configurations that support tests but aren't tests themselves

**Utility patterns**:
- `TestUtil`, `TestData`, `TestHelper`, etc.
- `TestController`, `TestService` (but NOT `TestUserController`, `TestUserService`)
- `@TestConfiguration` annotated classes
- Test interfaces

**Utilities are tracked separately** in the output and not included in test counts.

## Defensive Warnings

The script provides several types of warnings:

1. **Unclassified Tests** (`testType = null`) - Tests without a determined type, may not run in CI
2. **No Owner** - Tests without a CODEOWNERS entry
3. **Multiple Owners** - Tests owned by multiple teams

These warnings help ensure no tests are accidentally missed in CI execution.

## Ownership Resolution

The script delegates ownership lookup to `codeowners-cli` from [codeowners-plus](https://github.com/multimediallc/codeowners-plus).

**Prerequisites**: Install `codeowners-cli` before running:

```bash
# Using GitHub release
curl -fsSL https://github.com/multimediallc/codeowners-plus/releases/download/v1.9.0/codeowners-cli_1.9.0_Linux_x86_64.tar.gz | tar -xz
sudo mv codeowners-cli /usr/local/bin/

# Or use the composite action in CI
- uses: ./.github/actions/codeowners-setup-cli
```

### Skipping Owner Resolution

If you don't need owner information or want maximum speed, use the `--skip-owners` flag:

```bash
# Skip owner resolution entirely (super fast, ~250ms)
npm run collect-tests -- --skip-owners

# All tests will be marked as (unowned)
```

This is useful for:
- Quick test inventory without ownership
- Local development without `codeowners-cli` installed
- CI jobs that don't require ownership information

## Testing

The utilities include comprehensive unit tests using Node's built-in test framework:

```bash
cd scripts/codeowners-utils
npm test
```

Tests are located in `lib/*.test.js` and validate:
- Individual strategy classification logic
- Enrichment chain behavior
- Utility vs test distinction
- Location-based overrides

## Performance Considerations

The script is highly optimized for performance:

### Execution Phases

The script runs in three distinct phases:

1. **Filesystem Scan** (~230ms) - Finds all Java files in test directories
2. **Classification** (~17ms for 4000+ files with naming strategy) - Determines test types
3. **Owner Resolution** (variable, can be skipped) - Batch resolves file ownership

### Performance Optimizations

- **Batch Owner Resolution**: All files are resolved in a single `codeowners-cli` invocation instead of spawning a process per file (huge speedup!)
- **Chunked Processing**: Ownership assignment processes files in chunks of 500 to avoid command-line length limits
- **Naming Strategy**: Fast - only checks filenames (recommended default)
- **Annotation Strategy**: Moderate - reads file content only when needed
- **Location Strategy**: Very fast - only checks paths
- **Progress Updates**: Real-time progress indicators (when using `-v` flag) every 500 files during classification and per-chunk during ownership assignment

### Verbose Output (with -v flag)

When running with the `-v` or `--verbose` flag, the script provides detailed progress and metrics:

```
================================================================================
TEST COLLECTION STARTED
================================================================================
Strategies: naming + location
Skip owners: no
================================================================================

📂 Phase 1/3: Scanning filesystem for test files...
   ✓ Found 3541 Java files in test directories (231ms)

🔍 Phase 2/3: Classifying test files...
   Progress: 500/3541 (14%)
   Progress: 1000/3541 (28%)
   ...
   Progress: 3541/3541 (100%)
   ✓ Classified 3541 tests and 44 utilities (17ms)

👥 Phase 3/3: Assigning ownership to all test files (batch)...
   Progress: 500/3541 (14%) - chunk 1/8
   Progress: 1000/3541 (28%) - chunk 2/8
   ...
   Progress: 3541/3541 (100%) - chunk 8/8
   ✓ Assigned owners to 3541 tests (582ms)

================================================================================
COLLECTION COMPLETE
================================================================================
Tests found: 3541
Utilities: 44
Warnings: 0

Per-strategy metrics:
  naming:
    Tests identified:       3450
    Utilities identified:   44
    Test types classified:  3450
  location:
    Tests identified:       0
    Utilities identified:   0
    Test types classified:  235

Timing breakdown:
  Filesystem scan:    231ms (28%)
  Classification:     17ms (2%)
  Owner assignment:   582ms (70%)
  Total:              830ms
================================================================================
```

### Timing Output

The verbose mode displays timing for each phase, allowing you to identify bottlenecks.

### Speed Recommendations

- **Fastest**: `--skip-owners --strategies naming,location` (~250ms)
- **Fast with owners**: `--strategies naming,location` (default, batch mode, ~830ms)
- **Comprehensive**: `-v --strategies annotation,location` (slower but finds all tests)
- **Production/CI**: Without `-v` for clean output that's easy to parse

## Troubleshooting

### Debug Mode

If you're experiencing issues with owner resolution (e.g., all files showing as `(unowned)`), enable debug mode:

```bash
DEBUG_CODEOWNERS=true node scripts/codeowners-utils/collect-tests.js
```

This will output detailed diagnostic information:
- How `codeowners-cli` is being detected (direct command, shell alias, npx, etc.)
- Chunk processing details (number of files per batch, command length)
- Raw output from `codeowners-cli` (first 200 characters)
- Error messages if command execution fails

**Common Issues**:

1. **Shell aliases not working**: If you have `codeowners-cli` defined as a shell alias, the script will automatically detect and use your shell (`$SHELL -i -c`) to execute it. Enable debug mode to verify this is working correctly.

2. **All files showing as `(unowned)`**: This usually means `codeowners-cli` execution is failing. Check:

   - Is `codeowners-cli` installed? Run `codeowners-cli --version`
   - Is there a `.codeowners` or `CODEOWNERS` file at the repository root?
   - Enable debug mode to see the actual error
3. **Slow owner resolution**: If owner resolution is taking more than a few seconds, check:
   - Are you using batch mode (automatic since the recent optimization)?
   - Enable debug mode to see chunk sizes and processing times
   - Consider using `--skip-owners` if you don't need owner information

### Alias Setup

If you use a shell alias for `codeowners-cli`, the script will automatically detect and use it:

```bash
# In your ~/.zshrc or ~/.bashrc
alias codeowners-cli='docker run --rm -v $(pwd):/repo codeowners-cli'
```

The script will detect this and execute commands using your shell with `-i -c` to load the alias.

## Best Practices

### For Test Authors

1. **Follow naming conventions**: Use `*Test.java` for unit tests, `*IT.java` for integration tests
2. **Avoid Test* prefix for utilities**: Use `TestUtil`, `TestHelper` for test utilities
3. **Use standard annotations**: Prefer `@Test` and other JUnit annotations
4. **Name fixture utilities clearly**: Use `TestController`, not `TestUserController`

### For CI/CD

1. **Use default strategies**: `naming,location` is fast and correct for most cases
2. **Monitor warnings**: Pay attention to unclassified tests
3. **Validate utilities**: Ensure test utilities aren't accidentally run as tests
4. **Check metrics**: Review test distribution by owner and type

## Enforcing Naming Conventions with ArchUnit

To ensure all tests follow proper naming conventions, consider adding ArchUnit tests:

```java
@ArchTest
static final ArchRule unit_tests_should_follow_naming_convention =
    classes()
        .that().haveSimpleNameNotContaining("IT")
        .and().areAnnotatedWith(Test.class)
        .should().haveSimpleNameEndingWith("Test")
        .orShould().haveSimpleNameStartingWith("Test");

@ArchTest
static final ArchRule integration_tests_should_follow_naming_convention =
    classes()
        .that().areAnnotatedWith(Test.class)
        .and().resideInAPackage("..it..")
        .should().haveSimpleNameEndingWith("IT")
        .orShould().haveSimpleNameStartingWith("IT");
```

## Implementation Details

- **Language**: JavaScript (Node.js)
- **Dependencies**: None (uses only Node.js built-ins)
- **External Tools**: Requires `codeowners-cli` for ownership resolution
- **Pattern**: Strategy pattern with enrichment chain
- **Node Version**: Requires Node.js 18+ (for native test runner)

