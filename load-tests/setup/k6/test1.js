import http from 'k6/http';
import { sleep } from 'k6';
import { Counter } from 'k6/metrics';

const http_response = new Counter('http_response');

export const options = {
  scenarios: {
    topology: {
      exec: 'checkTopology',
      executor: 'constant-vus',
      duration: '1d',
      vus: 100,
    },
  },
};

export function setup() {
  const token_url = __ENV.ZEEBE_AUTHORIZATION_SERVER_URL;
  const client_id = __ENV.ZEEBE_CLIENT_ID;
  const client_secret = __ENV.ZEEBE_CLIENT_SECRET;
  const audience = __ENV.ZEEBE_TOKEN_AUDIENCE;

  console.log(`Fetching authentication token from ${token_url}`);

  const payload = {
    grant_type: 'client_credentials',
    audience: audience,
    client_id: client_id,
    client_secret: client_secret,
  };

  const response = http.post(token_url, payload);
  if (response.status != 200) {
    const result = response.body;
    throw new Error(`unable to fetch token: ${result}`);
  }

  const token = response.json('access_token');
  console.log(`Authentication token fetched successfully.`);
  http_response.add(1, { status: response.status, endpoint: 'token-request' });

  const context = {
    token: token,
    params: {
      headers: {
        'Content-Type': 'application/json',
        'Authorization': "Bearer " + token,
      }
    },
  };
  return context;
}

export function checkTopology (context) {
  const url = __ENV.ZEEBE_REST_ADDRESS + '/v1/topology';
  http.get(url, context.params);
  sleep(1);
};
