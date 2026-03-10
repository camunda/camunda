/**
 * Annotation Test Classifier Strategy
 *
 * Identifies tests and utilities based on JUnit test annotations and Spring test configuration.
 * Does NOT use filename patterns - scans file content for annotations.
 */

import { TestClassifierStrategy } from './classifier-strategy.js';
import { readFileSync } from 'fs';

/**
 * Check if a file is an interface
 * @param {string} filePath - Full path to the Java file
 * @returns {boolean}
 */
function isInterface(filePath) {
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

      // Check for interface keyword (e.g., "public interface Foo" or "interface Foo")
      if (/\binterface\s+\w+/.test(trimmed)) {
        return true;
      }
    }
  } catch (error) {
    return false;
  }

  return false;
}

/**
 * Check if a file has @Test annotations
 * @param {string} filePath - Full path to the Java file
 * @returns {boolean}
 */
function hasTestAnnotations(filePath) {
  const JUNIT_ANNOTATIONS = [
    '@Test',
    '@ParameterizedTest',
    '@RepeatedTest',
    '@TestFactory',
    '@TestTemplate'
  ];

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

      // Check for test annotations
      for (const annotation of JUNIT_ANNOTATIONS) {
        if (trimmed.startsWith(annotation) ||
            trimmed.includes(' ' + annotation) ||
            trimmed.includes('\t' + annotation)) {
          return true;
        }
      }
    }
  } catch (error) {
    return false;
  }

  return false;
}

/**
 * Check if a file has @TestConfiguration annotation
 * @param {string} filePath - Full path to the Java file
 * @returns {boolean}
 */
function hasTestConfiguration(filePath) {
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
        return true;
      }
    }
  } catch (e) {
    return false;
  }

  return false;
}

/**
 * Annotation Test Classifier Strategy
 *
 * Identifies tests based on JUnit annotations (@Test, @ParameterizedTest, etc.).
 * Identifies utilities based on @TestConfiguration annotation.
 * Scans file content for annotations.
 * Slower but more comprehensive - finds tests regardless of naming convention.
 */
export class AnnotationStrategy extends TestClassifierStrategy {
  /**
   * Classify file based on presence of test annotations
   * Returns independent opinion based on annotations found
   */
  async classify(file) {
    // Check for @TestConfiguration (utility class)
    if (hasTestConfiguration(file.fullPath)) {
      return {
        type: 'utility',
        testType: null
      };
    }

    // Check for interfaces (utility - test fixtures)
    if (isInterface(file.fullPath)) {
      return {
        type: 'utility',
        testType: null
      };
    }

    // Check for test annotations
    if (hasTestAnnotations(file.fullPath)) {
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
