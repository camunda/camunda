/**
 * Test File Finder
 *
 * Simple directory walker that finds all .java files in test directories.
 * Does NOT filter by naming patterns or annotations - that's the job of classifiers.
 */

import { readdirSync, statSync } from 'fs';
import { join } from 'path';

// Directories to skip during traversal
const SKIP_DIRS = new Set([
  'node_modules',
  'target',
  '.git',
  '.idea',
  'dist',
  'build'
]);

/**
 * Find all Java files in test directories
 *
 * @param {string} rootDir - Root directory to search from
 * @returns {Object} Object with testFiles array and warnings array
 */
export function findTestFiles(rootDir) {
  const testFiles = [];
  const warnings = [];

  function walk(dir, relativePath = '') {
    try {
      const entries = readdirSync(dir);

      for (const entry of entries) {
        const fullPath = join(dir, entry);
        const relPath = relativePath ? join(relativePath, entry) : entry;

        // Skip common non-test directories
        if (SKIP_DIRS.has(entry)) {
          continue;
        }

        const stat = statSync(fullPath);

        if (stat.isDirectory()) {
          walk(fullPath, relPath);
        } else if (stat.isFile() && entry.endsWith('.java')) {
          // Check if it's in a test directory
          if (!relPath.includes('/src/test/java/')) {
            continue;
          }

          testFiles.push({ path: relPath, fullPath });
        }
      }
    } catch (error) {
      warnings.push({
        type: 'scan-error',
        path: relativePath || dir,
        message: `Error scanning directory: ${error.message}`
      });
    }
  }

  walk(rootDir);
  return { testFiles, warnings };
}
