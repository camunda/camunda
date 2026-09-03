import http from 'k6/http';
import { sleep } from 'k6';
import { Counter } from 'k6/metrics';

// Metric in Prometheus will be: k6_topology_total
const topology_requests = new Counter('topology');
// Metric in Prometheus will be: k6_token_request_total
const token_request = new Counter('token_request');

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
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
  };

  // https://docs.camunda.io/docs/apis-clients/console-api-reference/
  const payload = `grant_type=client_credentials&audience=${audience}&client_id=${client_id}&client_secret=${client_secret}`;

  const response = http.post(token_url, payload, params);
    const result = response.body;
    if (response.status != 200) {
        throw new Error(`unable to fetch token: ${result}`);
    }
  const token = response.json('access_token');
  console.log("Authentication token fetched.");
  token_request.add(1, { status: response.status });

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

  var status = response.status;
  topology_requests.add(1, { status: response.status });
  sleep(1);
};
