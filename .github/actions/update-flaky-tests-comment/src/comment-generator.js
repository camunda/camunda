const helpers = require('./helpers');

async function main(context, github, currentData, prNumber) {
  console.log(`📌 Processing flaky test comment for PR #${prNumber}`);

  const { owner, repo } = context.repo;

  // Step 1: Fetch any existing flaky test comment
  const existingComment = await getExistingComment(github, owner, repo, prNumber);

  // Step 2: Parse existing comment to extract historical data
  const historicalData = existingComment ? helpers.parseComment(existingComment.body) : null;

  // Step 3: Merge current and historical data
  const mergedData = helpers.mergeFlakyData(currentData, historicalData);

  // Step 4: Generate comment content
  const comment = buildComment(mergedData);

  if (!comment) {
    console.log('✅ No flaky tests found. Skipping comment.');
    return;
  }

  // Step 5: Create or update comment
  if (existingComment) {
    console.log('✏️ Updating existing flaky test comment...');
    await updateComment(github, owner, repo, existingComment.id, comment);
  } else {
    console.log('📝 Creating new flaky test comment...');
    await createComment(github, owner, repo, prNumber, comment);
  }

  console.log('✅ Flaky test comment processed.');
}

function buildComment(mergedData) {
  if (!mergedData?.tests?.length) return null;

  const lines = [
    `# 🧪 Flaky Tests Summary`,
    `_👻 Haunted Tests — They Fail When No One's Watching_`,
    ``
  ];

  const allTests = mergedData.tests.flatMap(pkg =>
      pkg.flakys.map(test => ({
        ...test,
        packageName: pkg.packageName,
        fullName: test.className ? `${test.className}.${test.methodName}` : test.methodName
      }))
  );

  if (!allTests.length) return null;

  for (const test of allTests) {
    const flakiness = Math.round((test.occurrences / test.totalRuns) * 100);
    const icon = getFlakyIcon(flakiness);

    lines.push(`- **${test.fullName}** – ${icon} **${flakiness}% flakiness**`);
    lines.push(`  - Location: \`${test.packageName}\``);
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

async function getExistingComment(github, owner, repo, prNumber) {
  try {
    const { data: comments } = await github.rest.issues.listComments({
      owner,
      repo,
      issue_number: prNumber,
    });

    return comments.find(c => c.body?.includes('🧪 Flaky Tests Summary')) || null;
  } catch (error) {
    console.error('Error finding existing comment:', error);
    return null;
  }
}

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
