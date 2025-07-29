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

  // Generate the comment content
  const commentContent = generateCommentContent(processedData);

  if (!commentContent) {
    console.log('No flaky tests to report - skipping comment creation');
    return;
  }

  console.log('Generated comment content:', commentContent);

  // Find existing flaky test comment
  const existingComment = await findExistingFlakyTestComment(github, context.repo.owner, context.repo.repo, prNumber);

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
 * @param {Object} processedData - Processed flaky test data
 * @returns {string|null} - Markdown content or null if no tests
 */
function generateCommentContent(processedData) {
  if (!processedData || !processedData.tests || processedData.tests.length === 0) {
    return null;
  }

  // Flatten all flaky tests into a single array
  const allFlakyTests = [];
  processedData.tests.forEach(packageData => {
    packageData.flakys.forEach(flakyTest => {
      allFlakyTests.push({
        ...flakyTest,
        package: packageData.package,
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

  // Generate test entries
  allFlakyTests.forEach(test => {
    const totalRuns = 1; // For Step 3.1, always 1 since no memoization yet
    const flakiness = Math.round((test.occurrences / totalRuns) * 100);
    const icon = getFlakinessIcon(flakiness);

    comment += `- **${test.fullTestName}** – ${icon} **${flakiness}% flakiness**\n`;
    comment += `  - Location: \`${test.package}\`\n`;
    comment += `  - Occurrences: ${test.occurrences} / ${totalRuns}\n\n`; //TODO actually we can't use test.occurences alone itself, if should be a combination of test occurence + previous occurences based on existing comment
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

module.exports = { main };
