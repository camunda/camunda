/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Flaky Tests Comment Generator
 *
 * Generates and updates PR comments with flaky test summaries
 */

/**
 * Main function to generate or update flaky tests comment
 * @param {Object} context - GitHub Actions context
 * @param {Object} github - GitHub API client
 * @param {Object} processedData - Processed flaky test data
 * @param {number} prNumber - Pull request number
 */
async function main(context, github, processedData, prNumber) {
  console.log('Starting comment generation for PR:', prNumber);

  // Find existing flaky test comment first
  const existingComment = await findExistingFlakyTestComment(github, context.repo.owner, context.repo.repo, prNumber);

  // Step 3.2: Parse existing comment to get historical data
  const historicalData = existingComment ? parseExistingComment(existingComment.body) : null;
  console.log('Historical data:', JSON.stringify(historicalData, null, 2));

  // Step 3.2: Merge current data with historical data
  const mergedData = mergeTestData(processedData, historicalData);
  console.log('Merged data:', JSON.stringify(mergedData, null, 2));

  // Generate the comment content with merged data
  const commentContent = generateCommentContent(mergedData);

  if (!commentContent) {
    console.log('No flaky tests to report - skipping comment creation');
    return;
  }

  console.log('Generated comment content:', commentContent);

  if (existingComment) {
    console.log('Found existing flaky test comment, updating...');
    await updateComment(github, context.repo.owner, context.repo.repo, existingComment.id, commentContent);
  } else {
    console.log('No existing flaky test comment found, creating new one...');
    await createComment(github, context.repo.owner, context.repo.repo, prNumber, commentContent);
  }

  console.log('Flaky test comment processed successfully');
}

/**
 * Generate the markdown content for the flaky tests comment
 * @param {Object} mergedData - Merged flaky test data with historical information
 * @returns {string|null} - Markdown content or null if no tests
 */
function generateCommentContent(mergedData) {
  if (!mergedData || !mergedData.tests || mergedData.tests.length === 0) {
    return null;
  }

  // Flatten all flaky tests into a single array
  const allFlakyTests = [];
  mergedData.tests.forEach(packageData => {
    packageData.flakys.forEach(flakyTest => {
      allFlakyTests.push({
        ...flakyTest,
        packageName: packageData.packageName,
        fullTestName: flakyTest.className ? `${flakyTest.className}.${flakyTest.methodName}` : flakyTest.methodName
      });
    });
  });

  if (allFlakyTests.length === 0) {
    return null;
  }

  // Generate comment header
  let comment = `# 🧪 Flaky Tests Summary\n`;
  comment += `_👻 Haunted Tests — They Fail When No One's Watching_\n\n`;

  // Generate test entries using merged data
  allFlakyTests.forEach(test => {
    const flakiness = Math.round((test.occurrences / test.totalRuns) * 100);
    const icon = getFlakinessIcon(flakiness);

    comment += `- **${test.fullTestName}** – ${icon} **${flakiness}% flakiness**\n`;
    comment += `  - Location: \`${test.packageName}\`\n`;
    comment += `  - Occurrences: ${test.occurrences} / ${test.totalRuns}\n\n`;
  });

  // Add footer
  comment += `If the changes affect this area, **please check and fix before merging**.\n\n`;
  comment += `If not, **leave a quick comment** explaining why it's likely unrelated.\n\n`;
  comment += `Just doing some light ghostbusting. 👻`;

  return comment;
}

/**
 * Get the appropriate icon based on flakiness percentage
 * @param {number} flakiness - Flakiness percentage (0-100)
 * @returns {string} - Emoji icon
 */
function getFlakinessIcon(flakiness) {
  if (flakiness < 30) {
    return '👀'; // Eyes for low flakiness
  } else if (flakiness <= 80) {
    return '⚠️'; // Warning for medium flakiness
  } else {
    return '💀'; // Skull for high flakiness
  }
}

/**
 * Find existing flaky test comment in the PR
 * @param {Object} github - GitHub API client
 * @param {string} owner - Repository owner
 * @param {string} repo - Repository name
 * @param {number} prNumber - Pull request number
 * @returns {Object|null} - Existing comment or null
 */
async function findExistingFlakyTestComment(github, owner, repo, prNumber) {
  try {
    const { data: comments } = await github.rest.issues.listComments({
      owner,
      repo,
      issue_number: prNumber,
    });

    // Look for comment with our specific header
    const flakyTestComment = comments.find(comment =>
      comment.body && comment.body.includes('🧪 Flaky Tests Summary')
    );

    return flakyTestComment || null;
  } catch (error) {
    console.error('Error finding existing comment:', error);
    return null;
  }
}

/**
 * Create a new comment on the PR
 * @param {Object} github - GitHub API client
 * @param {string} owner - Repository owner
 * @param {string} repo - Repository name
 * @param {number} prNumber - Pull request number
 * @param {string} body - Comment body
 */
async function createComment(github, owner, repo, prNumber, body) {
  try {
    await github.rest.issues.createComment({
      owner,
      repo,
      issue_number: prNumber,
      body,
    });
    console.log('Successfully created flaky test comment');
  } catch (error) {
    console.error('Error creating comment:', error);
    throw error;
  }
}

/**
 * Update an existing comment
 * @param {Object} github - GitHub API client
 * @param {string} owner - Repository owner
 * @param {string} repo - Repository name
 * @param {number} commentId - Comment ID to update
 * @param {string} body - New comment body
 */
async function updateComment(github, owner, repo, commentId, body) {
  try {
    await github.rest.issues.updateComment({
      owner,
      repo,
      comment_id: commentId,
      body,
    });
    console.log('Successfully updated flaky test comment');
  } catch (error) {
    console.error('Error updating comment:', error);
    throw error;
  }
}

/**
 * Parse existing comment to extract historical test data
 * @param {string} commentBody - The existing comment body
 * @returns {Object|null} - Historical data structure or null if parsing fails
 */
function parseExistingComment(commentBody) {
  try {
    console.log('Parsing existing comment for historical data...');

    // Extract the test section between the header and footer
    const testSectionRegex = /_👻 Haunted Tests — They Fail When No One's Watching_\n\n([\s\S]*?)\n\nIf the changes affect this area/;
    const testSectionMatch = commentBody.match(testSectionRegex);

    if (!testSectionMatch) {
      console.log('Could not find test section in existing comment');
      return null;
    }

    const testSection = testSectionMatch[1];
    console.log('Extracted test section:', testSection);

    // Parse individual test entries
    const testEntryRegex = /- \*\*(.*?)\*\* – .* \*\*\d+% flakiness\*\*\n  - Location: `(.*?)`\n  - Occurrences: (\d+) \/ (\d+)/g;

    const tests = [];
    let totalRuns = 1; // Default fallback
    let match;

    while ((match = testEntryRegex.exec(testSection)) !== null) {
      const [, fullTestName, packageName, occurrences, runs] = match;

      // Parse className and methodName from fullTestName
      const lastDotIndex = fullTestName.lastIndexOf('.');
      let className = '';
      let methodName = fullTestName;

      if (lastDotIndex > 0) {
        className = fullTestName.substring(0, lastDotIndex);
        methodName = fullTestName.substring(lastDotIndex + 1);
      }

      tests.push({
        packageName: packageName,
        className: className,
        methodName: methodName,
        jobs: [], // Historical jobs not stored in comment, will be empty
        occurrences: parseInt(occurrences, 10),
        totalRuns: parseInt(runs, 10)
      });

      // Use the totalRuns from any test (they should all be the same)
      totalRuns = parseInt(runs, 10);
    }

    console.log(`Parsed ${tests.length} historical tests with totalRuns: ${totalRuns}`);

    return {
      totalRuns: totalRuns,
      tests: tests
    };

  } catch (error) {
    console.error('Error parsing existing comment:', error);
    console.log('Falling back to treating as first run');
    return null;
  }
}

/**
 * Merge current test data with historical data
 * @param {Object} currentData - Current run test data
 * @param {Object|null} historicalData - Historical test data from existing comment
 * @returns {Object} - Merged data structure
 */
function mergeTestData(currentData, historicalData) {
  console.log('Merging current data with historical data...');

  // If no historical data, treat as first run
  if (!historicalData) {
    console.log('No historical data found, treating as first run');
    return transformCurrentDataForComment(currentData, 1);
  }

  // Increment total runs for this pipeline run
  const newTotalRuns = historicalData.totalRuns + 1;
  console.log(`Incrementing total runs from ${historicalData.totalRuns} to ${newTotalRuns}`);

  // Create a map of historical tests for easy lookup
  const historicalTestMap = new Map();
  historicalData.tests.forEach(test => {
    const testKey = `${test.packageName}.${test.className}.${test.methodName}`;
    historicalTestMap.set(testKey, test);
  });

  // Create merged tests array starting with all historical tests
  const mergedTests = new Map();

  // Add all historical tests with updated totalRuns
  historicalData.tests.forEach(test => {
    const testKey = `${test.packageName}.${test.className}.${test.methodName}`;
    mergedTests.set(testKey, {
      ...test,
      totalRuns: newTotalRuns // Update total runs for all tests
    });
  });

  // Process current run tests
  if (currentData && currentData.tests) {
    currentData.tests.forEach(packageData => {
      packageData.flakys.forEach(currentTest => {
        const testKey = `${packageData.packageName}.${currentTest.className}.${currentTest.methodName}`;

        if (mergedTests.has(testKey)) {
          // Test exists in historical data - increment occurrences and merge jobs
          const existingTest = mergedTests.get(testKey);
          const mergedJobs = [...new Set([...existingTest.jobs, ...currentTest.jobs])]; // Remove duplicates

          mergedTests.set(testKey, {
            ...existingTest,
            jobs: mergedJobs,
            occurrences: existingTest.occurrences + 1, // Increment occurrences
            totalRuns: newTotalRuns
          });

          console.log(`Updated existing test: ${testKey}, new occurrences: ${existingTest.occurrences + 1}`);
        } else {
          // New test - add with occurrences = 1
          mergedTests.set(testKey, {
            packageName: packageData.packageName,
            className: currentTest.className,
            methodName: currentTest.methodName,
            jobs: currentTest.jobs,
            occurrences: 1,
            totalRuns: newTotalRuns
          });

          console.log(`Added new test: ${testKey}`);
        }
      });
    });
  }

  // Convert back to the expected structure grouped by packageName
  const packageMap = new Map();

  mergedTests.forEach(test => {
    if (!packageMap.has(test.packageName)) {
      packageMap.set(test.packageName, []);
    }

    packageMap.get(test.packageName).push({
      className: test.className,
      methodName: test.methodName,
      jobs: test.jobs,
      occurrences: test.occurrences,
      totalRuns: test.totalRuns
    });
  });

  const result = {
    tests: Array.from(packageMap.entries()).map(([packageName, flakys]) => ({
      packageName: packageName,
      flakys: flakys
    }))
  };

  console.log(`Merged data contains ${mergedTests.size} total tests with ${newTotalRuns} total runs`);
  return result;
}

/**
 * Transform current data for comment generation (first run case)
 * @param {Object} currentData - Current run test data
 * @param {number} totalRuns - Total runs (1 for first run)
 * @returns {Object} - Transformed data structure
 */
function transformCurrentDataForComment(currentData, totalRuns) {
  if (!currentData || !currentData.tests) {
    return { tests: [] };
  }

  return {
    tests: currentData.tests.map(packageData => ({
      packageName: packageData.packageName,
      flakys: packageData.flakys.map(test => ({
        className: test.className,
        methodName: test.methodName,
        jobs: test.jobs,
        occurrences: test.occurrences,
        totalRuns: totalRuns
      }))
    }))
  };
}

module.exports = { main };
