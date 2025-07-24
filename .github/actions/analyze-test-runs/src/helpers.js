/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const path = require('path');

/**
 * Helper functions for flaky test analysis
 */

/**
 * Extracts the test class name from a test line
 * @param {string} testLine - The test line to parse
 * @returns {string|null} - The extracted test class name or null if not found
 */
function extractTestClass(testLine) {
  // Matches the test class path
  const TEST_CLASS_PATH_REGEX = /([a-zA-Z][a-zA-Z0-9]*\.)+[a-zA-Z][a-zA-Z0-9]*\.[A-Z][a-zA-Z0-9]*(?:Test|IT)/;

  const match = testLine.match(TEST_CLASS_PATH_REGEX);
  const result = match ? match[0] : null;
  console.log(`Extracting test class from: "${testLine}" → ${result || 'NO MATCH'}`);
  return result;
}

/**
 * Generates possible file paths for a given test class
 * @param {string} testClass - The test class name
 * @returns {string[]} - Array of possible file paths
 */
function generatePossiblePaths(testClass) {
  const classPath = testClass.replace(/\./g, '/');
  const paths = [
    `${classPath}.java`,
    `src/test/java/${classPath}.java`,
    `src/main/java/${classPath}.java`
  ];
  console.log(`Generated possible paths for ${testClass}:`, paths);
  return paths;
}

/**
 * Finds a matching file for a test class among changed files
 * @param {string} testClass - The test class name
 * @param {string[]} changedFiles - Array of changed file paths
 * @returns {string|null} - The matching file path or null if not found
 */
function findMatchingFile(testClass, changedFiles) {
  const possiblePaths = generatePossiblePaths(testClass);
  const className = testClass.split('.').pop();

  for (const possiblePath of possiblePaths) {
    const matchingFile = changedFiles.find(file =>
      file.endsWith(possiblePath) || path.basename(file) === `${className}.java`
    );
    if (matchingFile) {
      console.log(`Found matching file: ${matchingFile} for test class ${testClass}`);
      return matchingFile;
    }
  }
  console.log(`No matching file found for test class ${testClass}`);
  return null;
}

/**
 * Creates the inline comment body for a flaky test
 * @param {string} author - The author to mention
 * @param {string} flakyTests - The flaky tests string
 * @returns {string} - The formatted comment body
 */
function createInlineCommentBody(author, flakyTests) {
  return `### Oh! We've got ourselves flaky ones!

Hey @${author}! 👋

This file contains the flaky test:
\`\`\`
${flakyTests}
\`\`\`

Since you recently modified this file, would you mind taking a quick look?
You might have insights into what could be causing the test instability.

**What to check:**
- Recent changes that might affect test timing
- New dependencies or configurations
- Race conditions or timing-sensitive code

No pressure if you're busy, just thought you'd be the best person to investigate! 🕵️‍♂️`;
}

/**
 * Creates the main PR comment body
 * @param {string} flakyTests - The flaky tests string
 * @returns {string} - The formatted comment body
 */
function createMainCommentBody(flakyTests) {
  return `### Oh! We've got ourselves flaky ones!

\`\`\`
${flakyTests}
\`\`\`

Might be related, might be a ghost.

If the changes affect this area, **please check and fix before merging**.

If not, **leave a quick comment** explaining why it’s likely unrelated.

Just doing some light ghostbusting. 👻`;
}

module.exports = {
  extractTestClass,
  generatePossiblePaths,
  findMatchingFile,
  createInlineCommentBody,
  createMainCommentBody
};
