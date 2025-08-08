const helpers = require('./helpers');
const githubApi = require('./github-api');

async function main(context, github, currentData, prNumber, branchName) {
  console.log(`📌 Processing flaky test comment for PR #${prNumber}`);

  const { owner, repo } = context.repo;

  // Step 1: Fetch any existing flaky test comment
  const existingComment = await githubApi.getExistingComment(github, owner, repo, prNumber);

  // Step 2: Parse existing comment to extract historical data
  const historicalData = existingComment ? helpers.parseComment(existingComment.body) : null;

  // Step 3: Merge current and historical data
  const mergedData = helpers.mergeFlakyData(currentData, historicalData);

  // Step 4: Generate comment content
  const comment = await buildComment(mergedData, github, branchName);

  if (!comment) {
    console.log('✅ No flaky tests found. Skipping comment.');
    return;
  }

  // Step 5: Create or update comment
  if (existingComment) {
    console.log('✏️ Updating existing flaky test comment...');
    await githubApi.updateComment(github, owner, repo, existingComment.id, comment);
  } else {
    console.log('📝 Creating new flaky test comment...');
    await githubApi.createComment(github, owner, repo, prNumber, comment);
  }

  console.log('✅ Flaky test comment processed.');
}

async function buildComment(mergedData, github, branchName) {
  if (!mergedData?.flakyTests?.length) return null;

  const lines = [
    `# 🧪 Flaky Tests Summary`,
    `_👻 Haunted Tests — They Fail When No One's Watching_`,
    ``
  ];

  const allTests = mergedData.flakyTests.map(test => ({
    ...test,
    fullName: test.className ? `${test.className}.${test.methodName}` : test.methodName
  }));

  if (!allTests.length) return null;

  allTests.sort((a, b) => b.flakiness - a.flakiness);

  for (const test of allTests) {
    const icon = getFlakyIcon(test.flakiness);
    const url = await generateTestSourceUrl(test, github, branchName);

    lines.push(`- [**${test.methodName}**](${url}) – ${icon} **${test.flakiness}% flakiness**`);
    lines.push(`  - Package: \`${test.packageName}\``);
    lines.push(`  - Class: \`${test.className ? `${test.className}` : ''}\``);
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
    console.log(`🔗 Original URL for test ${test.fullName}: ${originalUrl}`);

    if (!originalUrl) {
        console.warn(`No source URL found for test: ${test.fullName}`);
        return '';
    }

    return originalUrl.replace(/blob\/[^/]+/, `tree/${branchName}`);
}

module.exports = { main };
