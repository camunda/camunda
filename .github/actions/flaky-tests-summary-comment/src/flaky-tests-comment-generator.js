const helpers = require('./helpers');
const githubApi = require('./github-api');

async function createOrUpdateComment(context, github, currentData, prNumber, branchName) {
  console.log(`[flaky-tests] Processing flaky test comment for PR #${prNumber}`);

  const { owner, repo } = context.repo;

  // Step 1: Fetch any existing flaky test comment
  const existingComment = await githubApi.getExistingComment(github, owner, repo, prNumber);

  // Step 2: Parse existing comment to extract historical data
  const historicalData = existingComment ? helpers.parseComment(existingComment.body) : null;

  console.log("Existing comment:", existingComment);
  console.log('Historical data:', historicalData);

  // Step 3: Merge current and historical data, or generate first run data
  const mergedData = !historicalData ? helpers.prepareFirstRunData(currentData) : helpers.mergeFlakyData(currentData, historicalData);
  console.log('Merged data:', JSON.stringify(mergedData, null, 2));

  // Step 4: Generate comment content
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

  mergedData.sort((a, b) => b.flakiness - a.flakiness);

  for (const test of mergedData) {
    const icon = getFlakyIcon(test.flakiness);
    const url = await generateTestSourceUrl(test, github, branchName);

    const testName = test.methodName || test.fullName;
    const formattedName = url ? `[**${testName}**](${url})` : `**${testName}**`;
    lines.push(`- ${formattedName} – ${icon} **${test.flakiness}% flakiness**`);
    lines.push(`  - Jobs: \`${test.jobs.join(', ')}\``);
    lines.push(`  - Package: \`${test.packageName}\``);
    lines.push(`  - Class: \`${test.className ? test.className : "-"}\``);
    lines.push(`  - Occurrences: ${test.occurrences} / ${test.totalRuns}`);
    lines.push('');
  }

  lines.push(
      `If the changes affect this area, **please check and fix before merging**.`,
      `If not, **leave a quick comment** explaining why it's likely unrelated.`
  );

  return lines.join('\n');
}

function getFlakyIcon(percent) {
  if (percent < 30) return '👀';
  if (percent <= 80) return '⚠️';
  return '💀';
}

async function generateTestSourceUrl(test, github, branchName) {
    const originalUrl = await githubApi.getTestSourceUrl(test, github);
    const testName = test.methodName || test.fullName;
    console.log(`[flaky-tests] Original URL for test ${testName}: ${originalUrl}`);

    if (!originalUrl || typeof originalUrl !== 'string') {
        console.warn(`[flaky-tests] No valid source URL found for test: ${testName}`);
        return '';
    }

    return originalUrl.replace(/blob\/[^/]+/, `tree/${branchName}`);
}

module.exports = { createOrUpdateComment };
