/**
 * Test Classifier Strategy Interface
 *
 * Defines the strategy pattern for test classification approaches.
 *
 * Each strategy returns its independent opinion about a file:
 * 1. File type: 'test', 'utility', or null if unknown
 * 2. Test type: 'unit', 'integration', 'acceptance', or null if unknown
 *
 * Strategies return what they think independently without seeing previous results.
 * A coordinator (combined-strategy or collector) decides the final classification
 * by consulting all strategies in order (enrichment pattern).
 */

/**
 * Base class for test classification strategies
 */
export class TestClassifierStrategy {
  /**
   * Classify a single file, returning an independent opinion
   *
   * @param {Object} file - File information with {path, fullPath}
   * @returns {Promise<Object>} Classification result {type, testType}
   *   - type: 'test' | 'utility' | null - the file type (null if unknown)
   *   - testType: 'unit' | 'integration' | 'acceptance' | null - the test type
   */
  async classify(file) {
    throw new Error('classify() must be implemented by subclass');
  }

  /**
   * Get strategy name
   * @returns {string}
   */
  getName() {
    throw new Error('getName() must be implemented by subclass');
  }
}
