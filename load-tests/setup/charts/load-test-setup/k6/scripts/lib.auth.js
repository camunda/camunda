/* Contains authentication helpers. */

import http from 'k6/http';
import { sleep } from 'k6';

// Module-level code runs once per VU, in its own JS runtime, so every VU draws a different
// value. This staggers renewals that would otherwise all fire at once, because every VU starts
// out sharing the same token and expiryDate from setup().
const renewalJitterSeconds = 1 + Math.floor(Math.random() * 60);

// Returns an empty token. The first call to renew() fetches the actual token.
export function createToken() {
  return {
    accessToken: null,
    expiryDate: 0,
  };
}

export function renew(token) {
  if (token.expiryDate - renewalJitterSeconds * 1000 > Date.now()) {
    return token;
  }

  console.debug("Token will expire soon, will renew now.")
  try {
    updateAuthenticationToken(token);
  } catch (e) {
    console.error(`Error while renewing the token.`, e);
    // Known limitation: during an OAuth outage, each VU retries and sleeps 1s per call
    // until the old token expires, which can cause dropped iterations.
    sleep(1);
    // Without a previous token, callers would send "Bearer null".
    if (token.accessToken === null) {
      throw e;
    }
  }
  return token;
};

function updateAuthenticationToken(token) {
  const token_url = __ENV.CAMUNDA_OAUTH_URL;
  const client_id = __ENV.CAMUNDA_CLIENT_ID;
  const client_secret = __ENV.CAMUNDA_CLIENT_SECRET;
  const audience = __ENV.CAMUNDA_TOKEN_AUDIENCE;

  console.debug(`Fetching authentication token from: ${token_url}`);

  const payload = {
    grant_type: 'client_credentials',
    audience: audience,
    client_id: client_id,
    client_secret: client_secret,
  };

  const params = {
    tags: { name: 'token-request' },
  };

  const response = http.post(token_url, payload, params);
  if (response.status != 200) {
    throw new Error(`unable to fetch token, got HTTP status=${response.status}, body=${response.body}`);
  }

  token.accessToken = response.json('access_token');
  const expiresIn = response.json('expires_in');
  token.expiryDate = Date.now() + expiresIn * 1000;

  console.log(`Authentication token fetched successfully; will renew in ${expiresIn}s`);
};
