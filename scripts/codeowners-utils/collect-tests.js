#!/usr/bin/env node

/**
 * Test Collection Script
 *
 * Inventories every test file in the monorepo, mapping each to its codeowner and test type.
 *
 * Usage:
 *   npm run collect-tests [-- [options]]
 *   node collect-tests.js [options]
 *
 * Options:
 *   --format <format>      Output format: 'text' (default) or 'json'
 *   --strategies <list>    Comma-separated list of strategies: 'naming', 'annotation', 'location' (default: 'naming,location')
 *   --skip-owners          Skip codeowner resolution (faster, outputs '(unowned)' for all)
 *   --help                 Show this help message
 *
 * Discovery Strategies:
 *   naming      - Identifies tests by filename patterns (e.g., *Test.java, *IT.java)
 *   annotation  - Identifies tests by @Test annotations in file content
 *   location    - Classifies test type based on file path location
 *
 * Strategy Order:
 *   Strategies are applied in the order specified. Each strategy enriches results from previous ones.
 *   - Test discovery strategies (naming, annotation) determine IF a file is a test
 *   - Classification strategies (location) determine WHAT TYPE of test it is
 *
 * Examples:
 *   npm run collect-tests                                    # Default: naming + location
 *   npm run collect-tests -- --strategies annotation,location # Annotation-based discovery
 *   npm run collect-tests -- --strategies naming,annotation,location # Combined approach
 *   npm run collect-tests -- --skip-owners                   # Skip owner resolution
 *   npm run collect-tests -- --format json
 */

import { dirname, join } from 'path';
import { fileURLToPath } from 'url';
import { writeFileSync, mkdirSync } from 'fs';
import { collectTests } from './lib/collector.js';
import { NamingStrategy } from './lib/naming-strategy.js';
import { AnnotationStrategy } from './lib/annotation-strategy.js';
import { LocationStrategy } from './lib/location-strategy.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// Repository root is two levels up from this script
const REPO_ROOT = join(__dirname, '..', '..');
// Output directory is in scripts/codeowners-utils/output
const OUTPUT_DIR = join(__dirname, 'output');

/**
 * Parse command line arguments
 */
function parseArgs(args) {
  const options = {
    format: 'text',
    strategies: ['annotation', 'naming', 'location'], // Default strategy chain
    help: false,
    summary: true,
    output: false, // Output files disabled by default - results go to console only
    skipOwners: false, // By default, resolve owners
    verbose: false // By default, don't show progress/metrics - only show final results
  };

  for (let i = 0; i < args.length; i++) {
    const arg = args[i];

    if (arg === '--help' || arg === '-h') {
      options.help = true;
    } else if (arg === '--format' || arg === '-f') {
      options.format = args[++i] || 'text';
    } else if (arg === '--strategies' || arg === '-s') {
      const strategiesStr = args[++i] || 'annotation,naming,location';
      options.strategies = strategiesStr.split(',').map(s => s.trim());
    } else if (arg === '--no-summary') {
      options.summary = false;
    } else if (arg === '--output' || arg === '-o') {
      options.output = true;
    } else if (arg === '--skip-owners') {
      options.skipOwners = true;
    } else if (arg === '--verbose' || arg === '-v') {
      options.verbose = true;
    }
  }

  return options;
}

/**
 * Show help message
 */
function showHelp() {
  const help = `
Test Collection Script

Inventories every test file in the monorepo, mapping each to its codeowner and test type.

Usage:
  npm run collect-tests [-- [options]]
  node collect-tests.js [options]

Options:
  --format <format>       Output format: 'text' (default) or 'json'
  --strategies <list>     Comma-separated list of strategies (default: 'annotation,naming,location')
                          Available: naming, annotation, location
  --skip-owners           Skip codeowner resolution (faster, outputs '(unowned)' for all)
  --output, -o            Enable output file generation (disabled by default)
  --no-summary            Disable summary output file generation (only when --output is enabled)
  --verbose, -v           Show progress, metrics, and summary (without this, only final results are shown)
  --help, -h              Show this help message

Output:
  By default, results are printed to console only.
  Use --output to save results to files in scripts/codeowners-utils/output/
  Use --verbose to see progress updates, per-strategy metrics, and timing breakdown.

Strategy Descriptions:
  naming      - Identifies tests by filename patterns (e.g., *Test.java, *IT.java)
                Fast, follows Maven conventions
  annotation  - Identifies tests by @Test annotations in file content
                Slower, finds tests regardless of naming convention
  location    - Classifies test type based on file path location
                Determines if test is unit/integration/acceptance

Strategy Order:
  Strategies are applied in the order specified. Each strategy enriches results from previous ones.
  - Test discovery strategies (naming, annotation) determine IF a file is a test
  - Classification strategies (location) determine WHAT TYPE of test it is

Recommended Strategy Combinations:
  naming,location              - Fast, Maven conventions only (default)
  annotation,location          - Comprehensive, finds all tests with annotations
  naming,annotation,location   - Combined approach (naming first, then annotations for missed files)

Examples:
  npm run collect-tests                                      # Quiet mode: only show results
  npm run collect-tests -- -v                                # Verbose: show progress and metrics
  npm run collect-tests -- --output --no-summary             # Save files without summary
  npm run collect-tests -- -v --strategies annotation,location
  npm run collect-tests -- -v --skip-owners                  # Skip owner resolution
  npm run collect-tests -- --format json

Output Format (text):
  <relative-path> | <owner(s)> | <test-type>

Output Format (json):
  [
    {
      "path": "<relative-path>",
      "owners": ["<owner1>", "<owner2>"],
      "testType": "<test-type>"
    }
  ]

Results are automatically saved to output/result-<strategies>.txt and output/result-<strategies>.json
`;
  console.log(help);
}

/**
 * Output results in text format
 */
function outputText(tests) {
  const lines = [];
  for (const test of tests) {
    const ownersStr = test.owners.join(' ');
    lines.push(`${test.path} | ${ownersStr} | ${test.testType}`);
  }
  return lines.join('\n');
}

/**
 * Output results in JSON format
 */
function outputJson(tests) {
  return JSON.stringify(tests, null, 2);
}

/**
 * Print warnings
 */
function printWarnings(warnings) {
  if (warnings.length === 0) {
    return;
  }

  const groupedWarnings = {};
  for (const warning of warnings) {
    if (!groupedWarnings[warning.type]) {
      groupedWarnings[warning.type] = [];
    }
    groupedWarnings[warning.type].push(warning);
  }

  console.error('\n⚠️  WARNINGS:');
  for (const [type, warns] of Object.entries(groupedWarnings)) {
    console.error(`\n  ${type} (${warns.length}):`);
    // Show first 10 warnings of each type
    for (const warn of warns.slice(0, 10)) {
      console.error(`    ${warn.path}`);
      if (warn.message) {
        console.error(`      ${warn.message}`);
      }
    }
    if (warns.length > 10) {
      console.error(`    ... and ${warns.length - 10} more`);
    }
  }
  console.error('');
}

/**
 * Print summary statistics
 */
function printSummary(tests, utilities, warnings, strategiesUsed, outputEnabled) {
  console.error('\n' + '='.repeat(80));
  console.error('SUMMARY');
  console.error('='.repeat(80));
  console.error(`Strategies: ${strategiesUsed}`);
  console.error(`Total tests collected: ${tests.length}`);
  console.error(`Total utilities identified: ${utilities.length}`);

  // Count by test type
  const typeCount = {};
  for (const test of tests) {
    typeCount[test.testType] = (typeCount[test.testType] || 0) + 1;
  }

  console.error('\nTests by type:');
  for (const [type, count] of Object.entries(typeCount).sort((a, b) => b[1] - a[1])) {
    console.error(`  ${type}: ${count}`);
  }

  // Count by owner
  const ownerCount = {};
  for (const test of tests) {
    for (const owner of test.owners) {
      ownerCount[owner] = (ownerCount[owner] || 0) + 1;
    }
  }

  const topOwners = Object.entries(ownerCount)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 10);

  console.error('\nTop 10 test owners:');
  for (const [owner, count] of topOwners) {
    console.error(`  ${owner}: ${count}`);
  }

  if (warnings.length > 0) {
    const warningsByType = {};
    for (const warning of warnings) {
      warningsByType[warning.type] = (warningsByType[warning.type] || 0) + 1;
    }

    console.error('\nWarnings:');
    for (const [type, count] of Object.entries(warningsByType).sort((a, b) => b[1] - a[1])) {
      console.error(`  ${type}: ${count}`);
    }
  }

  if (outputEnabled) {
    console.error('\nOutput files:');
    console.error(`  ${join(OUTPUT_DIR, `result-${strategiesUsed}.txt`)}`);
    console.error(`  ${join(OUTPUT_DIR, `result-${strategiesUsed}.json`)}`);
    console.error(`  ${join(OUTPUT_DIR, `warnings-${strategiesUsed}.txt`)}`);
    console.error(`  ${join(OUTPUT_DIR, `warnings-${strategiesUsed}.json`)}`);
    console.error(`  ${join(OUTPUT_DIR, `summary-${strategiesUsed}.txt`)}`);
    console.error(`  ${join(OUTPUT_DIR, `summary-${strategiesUsed}.json`)}`);
  }
  console.error('='.repeat(80) + '\n');
}

/**
 * Generate summary data
 */
function generateSummary(tests, warnings, strategiesUsed) {
  // Count by test type
  const typeCount = {};
  for (const test of tests) {
    typeCount[test.testType] = (typeCount[test.testType] || 0) + 1;
  }

  // Count by owner
  const ownerCount = {};
  for (const test of tests) {
    for (const owner of test.owners) {
      ownerCount[owner] = (ownerCount[owner] || 0) + 1;
    }
  }

  // Count warnings by type
  const warningsByType = {};
  for (const warning of warnings) {
    warningsByType[warning.type] = (warningsByType[warning.type] || 0) + 1;
  }

  // Sort owners by count
  const sortedOwners = Object.entries(ownerCount)
    .sort((a, b) => b[1] - a[1])
    .map(([owner, count]) => ({ owner, count }));

  return {
    strategies: strategiesUsed,
    timestamp: new Date().toISOString(),
    totalTests: tests.length,
    testsByType: typeCount,
    testsByOwner: sortedOwners,
    totalWarnings: warnings.length,
    warningsByType
  };
}

/**
 * Generate text version of summary
 */
function generateSummaryText(summary) {
  const lines = [];
  lines.push('='.repeat(80));
  lines.push('TEST SUMMARY');
  lines.push('='.repeat(80));
  lines.push(`Strategies: ${summary.strategies}`);
  lines.push(`Generated: ${summary.timestamp}`);
  lines.push(`Total Tests: ${summary.totalTests}`);
  lines.push('');
  lines.push('Tests by Type:');
  for (const [type, count] of Object.entries(summary.testsByType).sort((a, b) => b[1] - a[1])) {
    const percentage = Math.round((count / summary.totalTests) * 100);
    lines.push(`  ${type}: ${count} (${percentage}%)`);
  }
  lines.push('');
  lines.push('Top 10 Owners:');
  for (let i = 0; i < Math.min(10, summary.testsByOwner.length); i++) {
    const { owner, count } = summary.testsByOwner[i];
    lines.push(`  ${owner}: ${count}`);
  }
  if (summary.totalWarnings > 0) {
    lines.push('');
    lines.push(`Total Warnings: ${summary.totalWarnings}`);
    lines.push('Warnings by Type:');
    for (const [type, count] of Object.entries(summary.warningsByType).sort((a, b) => b[1] - a[1])) {
      lines.push(`  ${type}: ${count}`);
    }
  }
  lines.push('='.repeat(80));
  return lines.join('\n');
}

/**
 * Generate text version of warnings
 */
function generateWarningsText(warnings) {
  if (warnings.length === 0) {
    return 'No warnings';
  }

  const lines = [];
  lines.push('='.repeat(80));
  lines.push('WARNINGS');
  lines.push('='.repeat(80));
  lines.push(`Total: ${warnings.length}`);
  lines.push('');

  // Group by type
  const groupedWarnings = {};
  for (const warning of warnings) {
    if (!groupedWarnings[warning.type]) {
      groupedWarnings[warning.type] = [];
    }
    groupedWarnings[warning.type].push(warning);
  }

  for (const [type, warns] of Object.entries(groupedWarnings)) {
    lines.push(`${type} (${warns.length}):`);
    for (const warn of warns) {
      lines.push(`  ${warn.path}`);
      if (warn.message) {
        lines.push(`    ${warn.message}`);
      }
    }
    lines.push('');
  }

  lines.push('='.repeat(80));
  return lines.join('\n');
}

/**
 * Generate text version of utilities
 */
function generateUtilitiesText(utilities) {
  if (utilities.length === 0) {
    return 'No utility classes';
  }

  const lines = [];
  lines.push('='.repeat(80));
  lines.push('UTILITY CLASSES');
  lines.push('='.repeat(80));
  lines.push(`Total: ${utilities.length}`);
  lines.push('');

  for (const util of utilities) {
    lines.push(`  ${util.path}`);
    if (util.message) {
      lines.push(`    ${util.message}`);
    }
  }

  lines.push('');
  lines.push('='.repeat(80));
  return lines.join('\n');
}

/**
 * Create strategy instances from strategy names
 */
function createStrategies(strategyNames) {
  const strategies = [];
  const strategyMap = {
    'naming': NamingStrategy,
    'annotation': AnnotationStrategy,
    'location': LocationStrategy
  };

  for (const name of strategyNames) {
    const StrategyClass = strategyMap[name];
    if (!StrategyClass) {
      throw new Error(`Unknown strategy: ${name}. Available: ${Object.keys(strategyMap).join(', ')}`);
    }
    strategies.push(new StrategyClass());
  }

  return strategies;
}

/**
 * Main entry point
 */
async function main() {
  const options = parseArgs(process.argv.slice(2));

  if (options.help) {
    showHelp();
    process.exit(0);
  }

  // Validate format
  if (options.format !== 'text' && options.format !== 'json') {
    console.error(`Error: Invalid format '${options.format}'. Must be 'text' or 'json'.`);
    process.exit(1);
  }

  // Validate strategies
  const validStrategies = ['naming', 'annotation', 'location'];
  for (const strategy of options.strategies) {
    if (!validStrategies.includes(strategy)) {
      console.error(`Error: Invalid strategy '${strategy}'. Must be one of: ${validStrategies.join(', ')}.`);
      process.exit(1);
    }
  }

  try {
    // Create strategy instances
    const strategies = createStrategies(options.strategies);
    const strategiesUsed = options.strategies.join('+');

    // Run collector with strategies
    const result = await collectTests(REPO_ROOT, strategies, {
      verbose: options.verbose,
      skipOwners: options.skipOwners
    });

    // Generate output text
    const textOutput = outputText(result.tests);
    const jsonOutput = outputJson(result.tests);

    // Save results to files if --output is enabled
    if (options.output) {
      // Create output directory if it doesn't exist
      mkdirSync(OUTPUT_DIR, { recursive: true });

      // Save results to files with strategy suffix
      writeFileSync(join(OUTPUT_DIR, `result-${strategiesUsed}.txt`), textOutput);
      writeFileSync(join(OUTPUT_DIR, `result-${strategiesUsed}.json`), jsonOutput);

      // Save utilities to file
      if (result.utilities && result.utilities.length > 0) {
        writeFileSync(join(OUTPUT_DIR, `utilities-${strategiesUsed}.json`), JSON.stringify(result.utilities, null, 2));
        writeFileSync(join(OUTPUT_DIR, `utilities-${strategiesUsed}.txt`), generateUtilitiesText(result.utilities));
      }

      // Save warnings to file (both json and txt)
      writeFileSync(join(OUTPUT_DIR, `warnings-${strategiesUsed}.json`), JSON.stringify(result.warnings, null, 2));
      writeFileSync(join(OUTPUT_DIR, `warnings-${strategiesUsed}.txt`), generateWarningsText(result.warnings));

      // Save summary to file if enabled (both json and txt)
      if (options.summary) {
        const summary = generateSummary(result.tests, result.warnings, strategiesUsed);
        writeFileSync(join(OUTPUT_DIR, `summary-${strategiesUsed}.json`), JSON.stringify(summary, null, 2));
        writeFileSync(join(OUTPUT_DIR, `summary-${strategiesUsed}.txt`), generateSummaryText(summary));
      }
    }

    // Output results to console based on format
    if (options.format === 'json') {
      console.log(jsonOutput);
    } else {
      console.log(textOutput);
    }

    // Print summary and warnings only in verbose mode
    if (options.verbose) {
      printSummary(result.tests, result.utilities || [], result.warnings, strategiesUsed, options.output);

      // Print warnings at the end
      if (result.warnings.length > 0) {
        printWarnings(result.warnings);
      }
    }
  } catch (error) {
    console.error(`\nError: ${error.message}`);
    if (error.stack) {
      console.error(error.stack);
    }
    process.exit(1);
  }
}

// Run if executed directly
if (import.meta.url === `file://${process.argv[1]}`) {
  main();
}

export { collectTests, NamingStrategy, AnnotationStrategy, LocationStrategy };
