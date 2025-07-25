/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Main flaky test analyzer that orchestrates the entire process
 */

const helpers = require('./helpers');
const githubApi = require('./github-api');

/**
 * Searches for flaky tests that match files modified in the current PR
 * @param {string} flakyTests - The flaky tests string
 * @param {object} prData - PR data containing files and commits
 * @param {object} githubConfig - GitHub configuration object containing API client, owner, and repo
 * @param {object} githubConfig.github - GitHub API client
 * @param {string} githubConfig.owner - Repository owner
 * @param {string} githubConfig.repo - Repository name
 * @returns {object} - Analysis results containing touched files, authors, and inline targets
 */
async function searchFlakyTestsInModifiedFiles(flakyTests, prData, githubConfig) {

  const flakyTestLines = flakyTests.trim().split('\n').filter(line => line.trim());
  const changedFiles = prData.files.map(file => file.filename);

  // Centralized data structure to avoid duplication
  const analysisResults = {
    touchedFiles: [],
    mentionedAuthors: new Set(),
    inlineTargets: new Map(), // Use Map to prevent duplicates by filename
    processedTestClasses: new Set()
  };

  for (let i = 0; i < flakyTestLines.length; i++) {
    const testLine = flakyTestLines[i];
    console.log(`\n Processing test ${i + 1}/${flakyTestLines.length}: ${testLine}`);

    const testClass = helpers.extractTestClass(testLine);
    if (!testClass) {
      console.log(`Skipping - no test class found`);
      continue;
    }

    // Skip if we've already processed this test class
    if (analysisResults.processedTestClasses.has(testClass)) {
      console.log(`Skipping - already processed test class: ${testClass}`);
      continue;
    }
    analysisResults.processedTestClasses.add(testClass);

    const matchingFile = helpers.findMatchingFile(testClass, changedFiles);
    if (!matchingFile) {
      console.log(`Skipping - no matching file found for test class: ${testClass}`);
      continue;
    }

    // Find the author using the commits data we already fetched
    const { author: fileAuthor, commit: fileCommit } = await githubApi.findLastAuthor(
      githubConfig, prData.commits, matchingFile
    );

    if (fileAuthor && fileCommit) {
      const fileInfo = {
        file: matchingFile,
        testClass,
        author: fileAuthor,
        commit: fileCommit
      };

      analysisResults.touchedFiles.push(fileInfo);
      analysisResults.mentionedAuthors.add(fileAuthor);

      // Store for inline comments (Map prevents duplicates by filename)
      if (!analysisResults.inlineTargets.has(matchingFile)) {
        analysisResults.inlineTargets.set(matchingFile, fileInfo);
        console.log(`Added to inline targets: ${matchingFile} → @${fileAuthor}`);
      } else {
        console.log(`File already in inline targets: ${matchingFile}`);
      }
    } else {
      console.log(`Could not find author for file: ${matchingFile}`);
    }
  }

  console.log(`\n Analysis Results Summary:`);
  console.log(`   📁 ${analysisResults.touchedFiles.length} files with flaky tests`);
  console.log(`   👥 ${analysisResults.mentionedAuthors.size} unique authors mentioned`);
  console.log(`   💬 ${analysisResults.inlineTargets.size} inline comments to create`);
  console.log(`   🧪 ${analysisResults.processedTestClasses.size} unique test classes processed`);

  return analysisResults;
}

/**
 * Main entry point for flaky test analysis
 * @param {object} context - GitHub Actions context
 * @param {object} github - GitHub API client
 * @param {string} flakyTests - The flaky tests string
 */
async function main(context, github, flakyTests) {
  const owner = context.repo.owner;
  const repo = context.repo.repo;
  const prNumber = Number(context.payload.pull_request?.number || context.issue?.number);
  const githubConfig = { github, owner, repo, prNumber };

  if (!githubConfig.prNumber || isNaN(githubConfig.prNumber)) {
    console.log("No PR context detected, skipping PR-based flaky test analysis.");
    return;
  }

  console.log('🚀 Starting flaky test analysis...');
  console.log(`🧪 Flaky tests found:\n${flakyTests}`);

  try {
    // Step 1: Fetch all required data upfront to minimize API calls
    console.log('\n📡 Step 1: Fetching all PR data...');
    const prData = await githubApi.fetchPRData(githubConfig);

    // Step 2: Process flaky tests and map to files/authors
    console.log('\n 🔍 Step 2: Analyzing flaky tests in modified files...');
    const analysisResults = await searchFlakyTestsInModifiedFiles(flakyTests, prData, githubConfig);

    // Step 3: Execute all GitHub API interactions
    console.log('\n🚀 Step 3: Creating GitHub interactions...');

    // 3a. Create inline review comments
    await githubApi.createInlineComments(
        githubConfig, prData.pullRequest.head.sha,
      analysisResults.inlineTargets, helpers.createInlineCommentBody, flakyTests
    );

    // 3b. Build and post/update the main PR comment
    await githubApi.createOrUpdateMainComment(
        githubConfig, flakyTests, helpers.createMainCommentBody
    );

    console.log('\n🎉 Flaky test analysis completed successfully!');

  } catch (error) {
    console.log(`❌ Failed to complete flaky test analysis: ${error.message}`);
    console.log('🛑 Aborting flaky test analysis');
    throw error;
  }
}

module.exports = { main, searchFlakyTestsInModifiedFiles };
