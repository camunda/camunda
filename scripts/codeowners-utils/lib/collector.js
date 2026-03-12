/**
 * Test Collector
 *
 * Scans the filesystem and applies multiple strategies to classify test files.
 * The collection process follows three distinct phases:
 * 1. Scan all files in the repository
 * 2. Classify all files (test vs utility, test type)
 * 3. Assign ownership to all files
 *
 * Uses combined-strategy orchestrator to coordinate independent classifiers.
 * Each classifier returns its own opinion, and the orchestrator decides the final classification.
 */

import { findTestFiles } from './test-finder.js';
import { getBatchCodeowners, isCodeownersCliAvailable } from './codeowners-util.js';
import { CombinedStrategy } from './combined-strategy.js';

/**
 * Phase 1: Scan all files in test directories
 * @param {string} repoRoot - Repository root directory
 * @param {boolean} verbose - Whether to output progress
 * @returns {Object} Scan results with files and warnings
 */
function scanAllFiles(repoRoot, verbose) {
  if (verbose) {
    console.error('📂 Phase 1/3: Scanning filesystem for test files...');
  }
  const scanStart = Date.now();
  const { testFiles: allFiles, warnings: scanWarnings } = findTestFiles(repoRoot);
  const scanTime = Date.now() - scanStart;

  if (verbose) {
    console.error(`   ✓ Found ${allFiles.length} Java files in test directories (${scanTime}ms)\n`);
  }

  return { allFiles, scanWarnings, scanTime };
}

/**
 * Phase 2: Classify all files using the combined strategy
 * @param {Array} allFiles - All files to classify
 * @param {CombinedStrategy} combinedStrategy - Combined strategy instance
 * @param {boolean} verbose - Whether to output progress
 * @returns {Object} Classification results with tests, utilities, warnings, and strategy metrics
 */
async function classifyAllFiles(allFiles, combinedStrategy, verbose) {
  if (verbose) {
    console.error('🔍 Phase 2/3: Classifying test files...');
  }
  const classifyStart = Date.now();

  const tests = [];
  const utilities = [];
  const warnings = [];
  const strategyMetrics = {}; // Track per-strategy contribution

  for (let i = 0; i < allFiles.length; i++) {
    const file = allFiles[i];

    if (verbose && (i % 500 === 0 || i === allFiles.length - 1)) {
      const processed = i + 1;
      const percent = Math.round((processed / allFiles.length) * 100);
      console.error(`   Progress: ${processed}/${allFiles.length} (${percent}%)`);
    }

    try {
      // Use combined strategy to get classification
      const result = await combinedStrategy.classify(file);

      // Track per-strategy contribution
      if (result.opinions) {
        for (const opinion of result.opinions) {
          const strategyName = opinion.strategy;
          if (!strategyMetrics[strategyName]) {
            strategyMetrics[strategyName] = {
              testsFound: 0,
              utilitiesFound: 0,
              testTypesSet: 0
            };
          }

          // Count if this strategy found a test
          if (opinion.type === 'test') {
            strategyMetrics[strategyName].testsFound++;
          }
          // Count if this strategy found a utility
          if (opinion.type === 'utility') {
            strategyMetrics[strategyName].utilitiesFound++;
          }
          // Count if this strategy set a test type
          if (opinion.testType !== null) {
            strategyMetrics[strategyName].testTypesSet++;
          }
        }
      }

      // Process based on classification
      if (result.type === 'test') {
        tests.push({
          path: file.path,
          owners: [], // Will be filled in phase 3
          testType: result.testType || null
        });

        // Warn if no test type could be determined
        if (!result.testType) {
          warnings.push({
            type: 'unclassified',
            path: file.path,
            message: 'Unable to classify test type - may be missing from test execution'
          });
        }
      } else if (result.type === 'utility') {
        utilities.push({
          path: file.path
        });
      }
    } catch (error) {
      warnings.push({
        type: 'classification-error',
        path: file.path,
        message: `Error during classification: ${error.message}`
      });
    }
  }

  const classifyTime = Date.now() - classifyStart;
  if (verbose) {
    console.error(`   ✓ Classified ${tests.length} tests and ${utilities.length} utilities (${classifyTime}ms)\n`);
  }

  return { tests, utilities, warnings, classifyTime, strategyMetrics };
}

/**
 * Phase 3: Assign ownership to all classified tests
 * @param {Array} tests - All classified tests
 * @param {string} repoRoot - Repository root directory
 * @param {boolean} skipOwners - Whether to skip owner resolution
 * @param {boolean} verbose - Whether to output progress
 * @returns {Object} Results with updated tests, warnings, and timing
 */
function assignOwnership(tests, repoRoot, skipOwners, verbose) {
  if (skipOwners) {
    if (verbose) {
      console.error('👥 Phase 3/3: Skipping owner resolution (--skip-owners)\n');
    }
    // Set all to unowned
    for (const test of tests) {
      test.owners = ['(unowned)'];
    }
    return { warnings: [], ownersTime: 0 };
  }

  if (tests.length === 0) {
    return { warnings: [], ownersTime: 0 };
  }

  if (verbose) {
    console.error('👥 Phase 3/3: Assigning ownership to all test files (batch)...');
  }
  const ownersStart = Date.now();
  const warnings = [];

  try {
    const testPaths = tests.map(t => t.path);
    const ownersMap = getBatchCodeowners(testPaths, repoRoot, verbose);

    // Assign owners to each test
    for (const test of tests) {
      test.owners = ownersMap.get(test.path) || ['(unowned)'];
    }

    const ownersTime = Date.now() - ownersStart;
    if (verbose) {
      console.error(`   ✓ Assigned owners to ${tests.length} tests (${ownersTime}ms)\n`);
    }
    return { warnings, ownersTime };
  } catch (error) {
    const ownersTime = Date.now() - ownersStart;
    if (verbose) {
      console.error(`   ⚠ Failed to assign owners: ${error.message} (${ownersTime}ms)\n`);
    }
    // Set all to unowned on error
    for (const test of tests) {
      test.owners = ['(unowned)'];
    }
    warnings.push({
      type: 'codeowners-error',
      path: '(batch)',
      message: `Failed to assign owners: ${error.message}`
    });
    return { warnings, ownersTime };
  }
}

/**
 * Collect tests using given strategies
 *
 * The collection process follows three distinct phases:
 * 1. Scan all files in the repository
 * 2. Classify all files (test vs utility, test type)
 * 3. Assign ownership to all files
 *
 * @param {string} repoRoot - Repository root directory
 * @param {Array} strategies - Array of strategy instances to apply
 * @param {Object} options - Collection options
 * @returns {Promise<Object>} Results with tests, utilities, and warnings
 */
export async function collectTests(repoRoot, strategies, options = {}) {
  const { verbose = false, skipCodeownersCheck = false, skipOwners = false } = options;

  const timings = {};
  const startTime = Date.now();

  if (verbose) {
    const strategyNames = strategies.map(s => s.getName()).join(' + ');
    console.error(`\n${'='.repeat(80)}`);
    console.error(`TEST COLLECTION STARTED`);
    console.error(`${'='.repeat(80)}`);
    console.error(`Strategies: ${strategyNames}`);
    console.error(`Skip owners: ${skipOwners ? 'yes' : 'no'}`);
    console.error(`${'='.repeat(80)}\n`);
  }

  // Check if codeowners-cli is available early (unless we're skipping)
  if (!skipOwners && !skipCodeownersCheck && !isCodeownersCliAvailable()) {
    throw new Error(
      'codeowners-cli not found. Please install it first.\n' +
      'Installation instructions: https://github.com/multimediallc/codeowners-plus\n' +
      'You can also use the GitHub Action: .github/actions/codeowners-setup-cli\n' +
      'Or use --skip-owners flag to skip owner resolution.'
    );
  }

  // Phase 1: Scan all files
  const scanResult = scanAllFiles(repoRoot, verbose);
  timings.scan = scanResult.scanTime;

  // Phase 2: Classify all files
  const combinedStrategy = new CombinedStrategy(strategies);
  const classifyResult = await classifyAllFiles(
    scanResult.allFiles,
    combinedStrategy,
    verbose
  );
  timings.classify = classifyResult.classifyTime;

  // Phase 3: Assign ownership to all files
  const ownershipResult = assignOwnership(
    classifyResult.tests,
    repoRoot,
    skipOwners,
    verbose
  );
  timings.owners = ownershipResult.ownersTime;

  // Combine all warnings
  const warnings = [
    ...scanResult.scanWarnings,
    ...classifyResult.warnings,
    ...ownershipResult.warnings
  ];

  const totalTime = Date.now() - startTime;
  timings.total = totalTime;

  if (verbose) {
    console.error(`${'='.repeat(80)}`);
    console.error(`COLLECTION COMPLETE`);
    console.error(`${'='.repeat(80)}`);
    console.error(`Tests found: ${classifyResult.tests.length}`);
    console.error(`Utilities: ${classifyResult.utilities.length}`);
    console.error(`Warnings: ${warnings.length}`);

    // Display per-strategy metrics
    if (classifyResult.strategyMetrics && Object.keys(classifyResult.strategyMetrics).length > 0) {
      console.error(`\nPer-strategy metrics:`);
      for (const [strategyName, metrics] of Object.entries(classifyResult.strategyMetrics)) {
        console.error(`  ${strategyName}:`);
        console.error(`    Tests identified:       ${metrics.testsFound}`);
        console.error(`    Utilities identified:   ${metrics.utilitiesFound}`);
        console.error(`    Test types classified:  ${metrics.testTypesSet}`);
      }
    }

    console.error(`\nTiming breakdown:`);
    console.error(`  Filesystem scan:    ${timings.scan}ms (${Math.round((timings.scan/totalTime)*100)}%)`);
    console.error(`  Classification:     ${timings.classify}ms (${Math.round((timings.classify/totalTime)*100)}%)`);
    console.error(`  Owner assignment:   ${timings.owners}ms (${Math.round((timings.owners/totalTime)*100)}%)`);
    console.error(`  Total:              ${totalTime}ms`);
    console.error(`${'='.repeat(80)}\n`);
  }

  return {
    tests: classifyResult.tests,
    utilities: classifyResult.utilities,
    warnings,
    timings,
    strategyMetrics: classifyResult.strategyMetrics
  };
}
