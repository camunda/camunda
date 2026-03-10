/**
 * Naming Pattern Test Classifier Strategy
 *
 * Identifies tests and utilities based on filename patterns (Maven naming conventions).
 * Also classifies test type based on filename suffix.
 * Does NOT scan file contents.
 */

import { TestClassifierStrategy } from './classifier-strategy.js';

// Test naming conventions (Maven defaults + custom patterns)
const UNIT_TEST_PATTERNS = [
  /Test\.java$/,
  /Tests\.java$/,
  /TestCase\.java$/,
  /^Test.*\.java$/
];

const INTEGRATION_TEST_PATTERNS = [
  /IT\.java$/,
  /^IT.*\.java$/,
  /ITCase\.java$/
];

// Test fixture patterns: utility classes and test support infrastructure
// Matches TestUtil.java, TestData.java, and Test<Type>.java patterns where <Type> is a utility suffix
const TEST_FIXTURE_PATTERNS = [
  /TestUtil\.java$/,
  /TestData\.java$/,
  /TestEnvironment\.java$/,
  /TestPlugin\.java$/,
  /TestProvider\.java$/,
  /TestFactory\.java$/,
  /TestBuilder\.java$/,
  /TestHelper\.java$/,
  /TestSupport\.java$/,
  // Test<Type>.java patterns (e.g., TestController.java, TestService.java)
  // Matches Test followed DIRECTLY by utility suffix - excludes TestUserController.java
  new RegExp(`^Test(${[
    'Controller', 'Service', 'Repository', 'Config', 'Factory', 'Builder',
    'Helper', 'Support', 'Details', 'Applier', 'Provider', 'Descriptor',
    'Store', 'Manager', 'Protocol', 'Appender', 'Engine', 'Streams',
    'Cache', 'Entity', 'Interceptor', 'Member', 'Loggers', 'Reader',
    'Entry', 'Context', 'State', 'Simulator', 'Chunk', 'Sender',
    'Configuration', 'Record', 'Value', 'Client', 'Clock', 'Observer',
    'Application', 'Segment', 'Stream'
  ].join('|')})\\.java$`)
];

/**
 * Check if filename matches test naming patterns
 * @param {string} fileName - Just the file name
 * @returns {string|null} - 'unit', 'integration', or null
 */
function matchesTestNamingPattern(fileName) {
  if (INTEGRATION_TEST_PATTERNS.some(pattern => pattern.test(fileName))) {
    return 'integration';
  }
  if (UNIT_TEST_PATTERNS.some(pattern => pattern.test(fileName))) {
    return 'unit';
  }
  return null;
}

/**
 * Check if filename matches utility class patterns
 * @param {string} fileName - Just the file name
 * @returns {boolean}
 */
function isUtilityByNaming(fileName) {
  // Check against all test fixture patterns
  for (const pattern of TEST_FIXTURE_PATTERNS) {
    if (pattern.test(fileName)) {
      // Don't treat it as a utility if it also matches test suffixes
      if (/Test\.java$|Tests\.java$|TestCase\.java$|IT\.java$|ITCase\.java$/.test(fileName)) {
        return false;
      }
      return true;
    }
  }

  return false;
}

/**
 * Check if a file is an interface based on filename
 * @param {string} fileName - Just the file name
 * @returns {boolean}
 */
function isInterface(fileName) {
  return fileName.endsWith('Interface.java');
}

/**
 * Naming Pattern Test Classifier Strategy
 *
 * Identifies tests and utilities based on filename patterns following Maven naming conventions.
 * Classifies test type based on suffix (*Test.java = unit, *IT.java = integration).
 * Fast and efficient - does not read file contents.
 */
export class NamingStrategy extends TestClassifierStrategy {
  /**
   * Classify file based on naming pattern
   * Returns independent opinion without seeing previous classifier results
   */
  async classify(file) {
    const fileName = file.path.split('/').pop();

    // Check for interfaces first (they override everything)
    if (isInterface(fileName)) {
      return {
        type: 'utility',
        testType: null
      };
    }

    // Check for utility classes next (before test prefix check)
    // This is important because TestUtil, TestData, TestController, etc.
    // would otherwise match the ^Test.* pattern
    if (isUtilityByNaming(fileName)) {
      return {
        type: 'utility',
        testType: null
      };
    }

    // Finally, check if filename matches test patterns
    // This comes last to allow specific utility patterns to take precedence
    const testType = matchesTestNamingPattern(fileName);
    if (testType) {
      return {
        type: 'test',
        testType: testType
      };
    }

    // No opinion
    return {
      type: null,
      testType: null
    };
  }

  getName() {
    return 'naming';
  }
}
