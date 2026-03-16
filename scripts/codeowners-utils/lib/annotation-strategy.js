/**
 * Annotation Test Classifier Strategy
 *
 * Identifies tests and utilities based on JUnit test annotations and Spring test configuration.
 * Does NOT use filename patterns - scans file content for annotations.
 */

import { TestClassifierStrategy } from './classifier-strategy.js';
import { readFileSync } from 'fs';

/**
 * Analyze file content for annotations and interface declaration
 * Reads file once and returns all relevant information
 * @param {string} filePath - Full path to the Java file
 * @returns {Object} Analysis result with hasTestConfig, isInterface, hasTestAnnotations flags
 */
function analyzeFileContent(filePath) {
  const JUNIT_ANNOTATIONS = [
    '@Test',
    '@ParameterizedTest',
    '@RepeatedTest',
    '@TestFactory',
    '@TestTemplate'
  ];

  const result = {
    hasTestConfiguration: false,
    isInterface: false,
    hasTestAnnotations: false
  };

  try {
    const content = readFileSync(filePath, 'utf-8');
    const lines = content.split('\n');
    let inBlockComment = false;

    for (const line of lines) {
      const trimmed = line.trim();

      // Track block comments
      if (trimmed.includes('/*')) {
        inBlockComment = true;
      }
      if (trimmed.includes('*/')) {
        inBlockComment = false;
        continue;
      }

      // Skip comments
      if (inBlockComment || trimmed.startsWith('//')) {
        continue;
      }

      // Check for @TestConfiguration annotation
      if (trimmed.startsWith('@TestConfiguration') ||
          trimmed.includes(' @TestConfiguration') ||
          trimmed.includes('\t@TestConfiguration')) {
        result.hasTestConfiguration = true;
      }

      // Check for interface keyword (e.g., "public interface Foo" or "interface Foo")
      if (/\binterface\s+\w+/.test(trimmed)) {
        result.isInterface = true;
      }

      // Check for test annotations
      if (!result.hasTestAnnotations) {
        for (const annotation of JUNIT_ANNOTATIONS) {
          if (trimmed.startsWith(annotation) ||
              trimmed.includes(' ' + annotation) ||
              trimmed.includes('\t' + annotation)) {
            result.hasTestAnnotations = true;
            break;
          }
        }
      }
    }
  } catch (error) {
    // Return default values on error
  }

  return result;
}

/**
 * Annotation Test Classifier Strategy
 *
 * Identifies tests based on JUnit annotations (@Test, @ParameterizedTest, etc.).
 * Identifies utilities based on @TestConfiguration annotation and interfaces.
 * Scans file content for annotations.
 * Slower but more comprehensive - finds tests regardless of naming convention.
 */
export class AnnotationStrategy extends TestClassifierStrategy {
  /**
   * Classify file based on presence of test annotations
   * Returns independent opinion based on annotations found
   */
  async classify(file) {
    // Read file once and analyze all aspects
    const analysis = analyzeFileContent(file.fullPath);

    // Check for @TestConfiguration (utility class)
    if (analysis.hasTestConfiguration) {
      return {
        type: 'utility',
        testType: null
      };
    }

    // Check for interfaces (utility - test fixtures)
    if (analysis.isInterface) {
      return {
        type: 'utility',
        testType: null
      };
    }

    // Check for test annotations
    if (analysis.hasTestAnnotations) {
      return {
        type: 'test',
        testType: null  // Annotations don't tell us unit vs integration
      };
    }

    // No opinion
    return {
      type: null,
      testType: null
    };
  }

  getName() {
    return 'annotation';
  }
}
