/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * GitHub API interactions for flaky test analysis
 */

/**
 * Fetches all PR data in parallel to minimize API calls
 * @param {object} github - GitHub API client
 * @param {string} owner - Repository owner
 * @param {string} repo - Repository name
 * @param {number} prNumber - Pull request number
 * @returns {object} - Object containing files, commits, and PR data
 */
async function fetchPRData(github, owner, repo, prNumber) {
  console.log('🔄 Making parallel API calls for PR files, commits, and details...');

  const [prFilesResponse, commitsResponse, pullRequestResponse] = await Promise.all([
    github.rest.pulls.listFiles({ owner, repo, pull_number: prNumber }),
    github.rest.pulls.listCommits({ owner, repo, pull_number: prNumber }),
    github.rest.pulls.get({ owner, repo, pull_number: prNumber })
  ]);

  const prData = {
    files: prFilesResponse.data,
    commits: commitsResponse.data,
    pullRequest: pullRequestResponse.data
  };

  console.log(`Successfully fetched:`);
  console.log(`   📄 ${prData.files.length} changed files`);
  console.log(`   📝 ${prData.commits.length} commits`);
  console.log(`   🔗 PR head SHA: ${prData.pullRequest.head.sha.substring(0, 7)}`);

  const changedFiles = prData.files.map(file => file.filename);
  console.log(`Changed files in this PR:`, changedFiles);

  return prData;
}

/**
 * Finds the last author who modified a specific file
 * @param {object} github - GitHub API client
 * @param {string} owner - Repository owner
 * @param {string} repo - Repository name
 * @param {array} commits - Array of commit objects
 * @param {string} filename - Name of the file to check
 * @returns {object} - Object containing author and commit info
 */
async function findLastAuthor(github, owner, repo, commits, filename) {
  console.log(`Finding last author for file: ${filename}`);

  for (const commit of [...commits].reverse()) {
    try {
      const { data: commitFiles } = await github.rest.repos.getCommit({
        owner,
        repo,
        ref: commit.sha,
      });

      const modifiedFile = commitFiles.files?.find(file => file.filename === filename);
      if (modifiedFile) {
        const author = commit.author?.login;
        console.log(`Found last author for ${filename}: @${author} in commit ${commit.sha.substring(0, 7)}`);
        return { author, commit };
      }
    } catch (error) {
      console.log(`Could not fetch commit ${commit.sha.substring(0, 7)}: ${error.message}`);
      continue;
    }
  }

  console.log(`No author found for file: ${filename}`);
  return { author: null, commit: null };
}

/**
 * Creates inline review comments for flaky tests
 * @param {object} github - GitHub API client
 * @param {string} owner - Repository owner
 * @param {string} repo - Repository name
 * @param {number} prNumber - Pull request number
 * @param {string} headSha - Head commit SHA
 * @param {Map} inlineTargets - Map of filename to target info
 * @param {function} createInlineCommentBody - Function to create comment body
 * @param {string} flakyTests - Flaky tests string
 * @returns {object} - Summary of success/failure counts
 */
async function createInlineComments(github, owner, repo, prNumber, headSha, inlineTargets, createInlineCommentBody, flakyTests) {
  if (inlineTargets.size === 0) {
    console.log(`No inline comments to create`);
    return { successCount: 0, failCount: 0 };
  }

  let successCount = 0;
  let failCount = 0;

  for (const [filename, target] of inlineTargets.entries()) {
    console.log(`Creating inline comment ${successCount + failCount + 1}/${inlineTargets.size} for: ${filename}`);

    const inlineBody = createInlineCommentBody(target.author, flakyTests);

    try {
      await github.rest.pulls.createReviewComment({
        owner,
        repo,
        pull_number: prNumber,
        commit_id: headSha,
        path: target.file,
        line: 1,
        body: inlineBody
      });
      successCount++;
      console.log(`Successfully created inline comment for ${filename}`);
    } catch (reviewError) {
      failCount++;
      console.log(`Failed to create inline comment for ${filename}: ${reviewError.message}`);
    }
  }

  console.log(`Inline comments summary: ${successCount} successful, ${failCount} failed`);
  return { successCount, failCount };
}

/**
 * Creates or updates the main PR comment about flaky tests
 * @param {object} github - GitHub API client
 * @param {string} owner - Repository owner
 * @param {string} repo - Repository name
 * @param {number} prNumber - Pull request number
 * @param {string} flakyTests - Flaky tests string
 * @param {function} createMainCommentBody - Function to create comment body
 */
async function createOrUpdateMainComment(github, owner, repo, prNumber, flakyTests, createMainCommentBody) {
  console.log(`\n Creating main PR comment...`);

  const body = createMainCommentBody(flakyTests);

  const comments = await github.rest.issues.listComments({
    owner,
    repo,
    issue_number: prNumber,
  });

  console.log(`Found ${comments.data.length} existing comments`);

  const marker = `${flakyTests}`;
  const botComment = comments.data.find(comment =>
    comment.user.type === "Bot" && comment.body.includes(marker)
  );

  if (botComment) {
    console.log(`Updating existing bot comment (ID: ${botComment.id})`);
    await github.rest.issues.updateComment({
      owner,
      repo,
      comment_id: botComment.id,
      body: body
    });
  } else {
    console.log(`Creating new bot comment`);
    await github.rest.issues.createComment({
      owner,
      repo,
      issue_number: prNumber,
      body: body
    });
  }
}

module.exports = {
  fetchPRData,
  findLastAuthor,
  createInlineComments,
  createOrUpdateMainComment
};
