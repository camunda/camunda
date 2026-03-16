/**
 * Combined Strategy (Composite Pattern)
 *
 * Coordinates multiple independent classifiers to arrive at a final classification.
 * Implements the TestClassifierStrategy interface and contains a list of strategies.
 *
 * Uses an enrichment pattern where all strategies are consulted in order.
 * Each classifier returns its independent opinion, and later strategies can
 * enrich or override earlier opinions.
 *
 * Decision logic:
 * - Collect opinions from all strategies (no early exit)
 * - For type: use last non-null value
 * - For testType: use last non-null value
 * - This allows later strategies to enrich/override earlier classifications
 */

import { TestClassifierStrategy } from './classifier-strategy.js';

/**
 * Combined classifier that orchestrates multiple strategies using composite pattern
 */
export class CombinedStrategy extends TestClassifierStrategy {
  /**
   * @param {Array<TestClassifierStrategy>} strategies - Array of classifier strategies
   */
  constructor(strategies) {
    super();
    this.strategies = strategies;
  }

  getName() {
    return 'combined';
  }

  /**
   * Classify a file by consulting all strategies
   *
   * @param {Object} file - File information
   * @returns {Promise<Object>} Classification result {type, testType, opinions}
   */
  async classify(file) {
    const opinions = [];

    // Collect all opinions without early exit to allow enrichment
    for (const strategy of this.strategies) {
      try {
        const opinion = await strategy.classify(file);
        opinions.push({ strategy: strategy.getName(), ...opinion });
      } catch (error) {
        // Continue with other classifiers if one fails
        console.error(`Error in ${strategy.getName()}: ${error.message}`);
      }
    }

    // Combine all opinions and return both combined result and individual opinions
    const combined = this.combineOpinions(opinions);
    return {
      ...combined,
      opinions // Include individual strategy opinions for metrics
    };
  }

  /**
   * Combine multiple opinions into a final classification
   * Uses last non-null value to allow later strategies to enrich/override earlier ones
   */
  combineOpinions(opinions) {
    // For type: pick last non-null opinion (allows enrichment)
    let finalType = null;

    for (const op of opinions) {
      if (op.type !== null) {
        finalType = op.type; // Keep updating with each valid opinion
      }
    }

    // For testType: pick last non-null opinion (allows enrichment/override)
    let finalTestType = null;

    for (const op of opinions) {
      if (op.testType !== null) {
        finalTestType = op.testType; // Keep updating with each valid opinion
      }
    }

    return {
      type: finalType,
      testType: finalTestType
    };
  }
}
