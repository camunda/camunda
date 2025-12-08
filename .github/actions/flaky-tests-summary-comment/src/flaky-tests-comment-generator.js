const helpers = require('./helpers');
const githubApi = require('./github-api');

async function createOrUpdateComment(context, github, currentData, prNumber, branchName) {
  console.log(`[flaky-tests] Processing flaky test comment for PR #${prNumber}`);

  const { owner, repo } = context.repo;

  // Step 1: Fetch any existing flaky test comment
  const existingComment = await githubApi.getExistingComment(github, owner, repo, prNumber);

  // Step 2: Parse existing comment to extract historical data (including URLs)
  const historicalData = existingComment ? helpers.parseComment(existingComment.body) : null;
  console.log('Historical data:', historicalData);

  // Step 3: Merge current and historical data, or generate first run data
  const mergedData = (!historicalData || historicalData.length === 0) ? helpers.prepareFirstRunData(currentData) : helpers.mergeFlakyData(currentData, historicalData);
  console.log('Merged data:', JSON.stringify(mergedData, null, 2));

  // Step 4: Generate comment content (URLs from historical data will be reused)
  const comment = await buildComment(mergedData, github, branchName);

  // Step 5: Create or update comment
  if (existingComment) {
    console.log('[flaky-tests] Updating existing flaky test comment...');
    await githubApi.updateComment(github, owner, repo, existingComment.id, comment);
  } else {
    console.log('[flaky-tests] Creating new flaky test comment...');
    await githubApi.createComment(github, owner, repo, prNumber, comment);
  }

  console.log('[flaky-tests] Flaky test comment processed.');
}

async function buildComment(mergedData, github, branchName) {
  const lines = [
    `# 🧪 Flaky Tests Summary`,
    `_👻 Haunted Tests — They Fail When No One's Watching_`,
    ``
  ];

  mergedData.sort((a, b) => b.overallRetries - a.overallRetries);

  for (const test of mergedData) {
    // Reuse URL from previous comment if available, otherwise search GitHub
    let url = test.url || '';
    if (!url) {
      url = await generateTestSourceUrl(test, github, branchName);
    } else {
      console.log(`[flaky-tests] Reusing URL from previous comment for test: ${test.methodName || test.fullName}`);
    }

    const testName = test.methodName || test.fullName;
    const formattedName = url ? `[**${testName}**](${url})` : `**${testName}**`;
    const failuresHistoryStr = test.failuresHistory.join(', ');
    
    lines.push(`- ${formattedName}`);
    lines.push(`  - Jobs: \`${test.jobs.join(', ')}\``);
    lines.push(`  - Package: \`${test.packageName}\``);
    lines.push(`  - Class: \`${test.className ? test.className : "-"}\``);
    lines.push(`  - Pipeline runs: ${test.totalRuns || 1}`);
    lines.push(`  - **Overall retries: ${test.overallRetries || 0} (per run: [${failuresHistoryStr}])**`);
    lines.push('');
  }

  lines.push(
      `If the changes affect this area, **please check and fix before merging**.`,
      `If not, **leave a quick comment** explaining why it's likely unrelated.`
  );

  return lines.join('\n');
}

async function generateTestSourceUrl(test, github, branchName) {
    try {
        const originalUrl = await githubApi.getTestSourceUrl(test, github);
        const testName = test.methodName || test.fullName;
        console.log(`[flaky-tests] Original URL for test ${testName}: ${originalUrl}`);

        if (!originalUrl || typeof originalUrl !== 'string') {
            console.warn(`[flaky-tests] No valid source URL found for test: ${testName}`);
            return '';
        }

        // Safely encode branch name and ensure URL replacement works
        const encodedBranch = encodeURIComponent(branchName);
        if (originalUrl.includes('/blob/')) {
            return originalUrl.replace(/\/blob\/[^/]+/, `/tree/${encodedBranch}`);
        } else {
            // Fallback: return original URL if pattern doesn't match expected format
            console.warn(`[flaky-tests] Unexpected URL format, returning original: ${originalUrl}`);
            return originalUrl;
        }
    } catch (error) {
        const testName = test.methodName || test.fullName;
        console.error(`[flaky-tests] Failed to generate URL for test ${testName}:`, error.message);
        return '';
    }
}

module.exports = { createOrUpdateComment };
