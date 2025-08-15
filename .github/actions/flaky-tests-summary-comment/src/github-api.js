async function getTestSourceUrl(test, github) {
  const repo = 'camunda/camunda';
  const query = `repo:${repo} ${test.className.replace(/\$/g, ' ')} ${test.methodName || test.fullName}`;

  console.log(`[flaky-tests] Searching for test source with query: ${query}`);

  const { data } = await github.rest.search.code({
    q: `${query}`,
  });

  if (!data.items || data.items.length === 0) {
    console.warn(`[flaky-tests] No match found for test: ${query}`);
    return null;
  }

  return data.items[0].html_url;
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
    console.error('[flaky-tests] Error finding existing comment:', error);
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
    console.error('[flaky-tests] Error creating comment:', error);
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
    console.log('[flaky-tests] Successfully updated flaky test comment');
  } catch (error) {
    console.error('[flaky-tests] Error updating comment:', error);
    throw error;
  }
}

module.exports = {
  getTestSourceUrl,
  getExistingComment,
  createComment,
  updateComment
};
