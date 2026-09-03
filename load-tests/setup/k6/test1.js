import http from 'k6/http';
import { sleep } from 'k6';
import { Counter } from 'k6/metrics';

const http_response = new Counter('http_response');

export const options = {
  vus: 2,
  duration: '15m',
};

// Authenticate
export function setup() {
  const token_url = __ENV.ZEEBE_AUTHORIZATION_SERVER_URL;
  const client_id = __ENV.ZEEBE_CLIENT_ID;
  const client_secret = __ENV.ZEEBE_CLIENT_SECRET;
  const audience = __ENV.ZEEBE_TOKEN_AUDIENCE;

  console.log(`Fetching authentication token from ${token_url}`);
  const params = {
    headers: {},
  };

  const payload = {
    grant_type: 'client_credentials',
    audience: audience,
    client_id: client_id,
    client_secret: client_secret,
  };

  const response = http.post(token_url, payload, params);
    const result = response.body;
    if (response.status != 200) {
        throw new Error(`unable to fetch token: ${result}`);
    }
  const token = response.json('access_token');
  console.log("Authentication token fetched.");
  http_response.add(1, { status: response.status, endpoint: 'token-request' });

  return token;
}


// Check zeebe status
export default (token) => {
  const url = __ENV.ZEEBE_REST_ADDRESS + '/v1/topology';

  const headers = {
    'Content-Type': 'application/json',
    'Authorization': "Bearer " + token,
  };


  const params = {
    headers: headers
  };

  const response = http.get(url, params);

  http_response.add(1, { status: response.status, endpoint: 'topology' });
  sleep(1);
};
