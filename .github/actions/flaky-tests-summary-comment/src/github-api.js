// Cache for test source URLs to avoid duplicate API calls
const urlCache = new Map();

async function getTestSourceUrl(test, github) {
  const repo = 'camunda/camunda';
  const query = `repo:${repo} ${test.className?.replace(/\$/g, ' ') || ''} ${test.methodName || test.fullName}`;

  // Check cache first to avoid duplicate API calls
  // Use same key logic as getTestKey() in helpers.js
  const cacheKey = test.fullName
    ? test.fullName
    : `${test.packageName}.${test.className}.${test.methodName || ''}`;
  if (urlCache.has(cacheKey)) {
    console.log(`[flaky-tests] Using cached URL for test: ${cacheKey}`);
    return urlCache.get(cacheKey);
  }

  console.log(`[flaky-tests] Searching for test source with query: ${query}`);

  try {
    const { data } = await github.rest.search.code({
      q: `${query}`,
    });

    if (!data.items || data.items.length === 0) {
      console.warn(`[flaky-tests] No match found for test: ${query}`);
      urlCache.set(cacheKey, null);
      return null;
    }

    const url = data.items[0].html_url;
    urlCache.set(cacheKey, url);
    return url;
  } catch (error) {
    // Handle rate limit errors gracefully
    // Check for rate limit by status code and headers (primary check)
    const rateLimitRemaining = error.response?.headers?.['x-ratelimit-remaining'];
    const isRateLimitedByHeader = error.status === 403 && (rateLimitRemaining === '0' || rateLimitRemaining === 0);
    // Check error message as secondary fallback
    const isRateLimitedByMessage = error.status === 403 && error.message?.toLowerCase().includes('rate limit');
    
    if (isRateLimitedByHeader || isRateLimitedByMessage) {
      console.warn(`[flaky-tests] GitHub API rate limit exceeded while searching for test: ${query}`);
      if (error.response?.headers) {
        console.warn(`[flaky-tests] Rate limit info:`, {
          remaining: rateLimitRemaining,
          reset: error.response.headers['x-ratelimit-reset']
        });
      }
      // Cache the failure to avoid retrying immediately
      urlCache.set(cacheKey, null);
      return null;
    }
    // For other errors, log and return null
    console.error(`[flaky-tests] Error searching for test source: ${error.message}`);
    urlCache.set(cacheKey, null);
    return null;
  }
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
