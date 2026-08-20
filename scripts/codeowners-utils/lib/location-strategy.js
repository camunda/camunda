/**
 * Location-based Test Classifier Strategy
 *
 * Classifies tests based solely on their file path location in the repository.
 * This implements the Single Responsibility Principle by separating location-based
 * classification from naming and annotation-based classification.
 *
 * This strategy enriches the result by determining the test type based on directory path only.
 */

import { TestClassifierStrategy } from './classifier-strategy.js';

// Configuration for test type classification based on location (path only, no filename)
export const LOCATION_CLASSIFICATION_RULES = [
  // 1. LOCATION-BASED OVERRIDES (highest priority)
  {
    type: 'acceptance',
    pattern: /^qa\/acceptance-tests\//,
    description: 'Acceptance tests under qa/acceptance-tests/'
  },

  // 2. QA MODULE DETECTION (surefire skipped, failsafe runs everything)
  {
    type: 'integration',
    pattern: /^qa\//,
    description: 'Integration tests under qa/'
  },
  {
    type: 'integration',
    pattern: /^zeebe\/qa\//,
    description: 'Integration tests under zeebe/qa/'
  },
  {
    type: 'integration',
    pattern: /^operate\/qa\//,
    description: 'Integration tests under operate/qa/'
  },
  {
    type: 'integration',
    pattern: /^tasklist\/qa\//,
    description: 'Integration tests under tasklist/qa/'
  }
];

/**
 * Classify test type based on file path location only
 * @param {string} relativePath - Relative path from repository root
 * @returns {{type: string, rule: string|null}} Classification result
 */
export function classifyByLocation(relativePath) {
  // Normalize path separators for consistent matching
  const normalizedPath = relativePath.replace(/\\/g, '/');

  for (const rule of LOCATION_CLASSIFICATION_RULES) {
    // Check if path matches the pattern
    if (rule.pattern.test(normalizedPath)) {
      return { type: rule.type, rule: rule.description };
    }
  }

  // No location-based classification
  return { type: null, rule: null };
}

/**
 * Location Classifier Strategy
 *
 * Enriches classification by determining test type based on directory path only.
 * Does not use filename patterns - only directory location.
 */
export class LocationStrategy extends TestClassifierStrategy {
  /**
   * Classify file based on location
   * Returns opinion about testType based on directory path
   * Does not determine if file is a test or utility - only provides testType hints
   */
  async classify(file) {
    const classification = classifyByLocation(file.path);
    if (classification.type) {
      // Strong opinion on testType based on location
      return {
        type: null,  // Don't know if it's test or utility, just know the type
        testType: classification.type
      };
    }

    // No opinion
    return {
      type: null,
      testType: null
    };
  }

  getName() {
    return 'location';
  }
}
