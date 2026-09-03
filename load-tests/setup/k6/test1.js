import http from 'k6/http';
import { sleep } from 'k6';
import json from 'k6/json';
import { Counter } from 'k6/metrics';

const http_response = new Counter('http_response');
let token = {
  accessToken: null,
  expiryDate: 0,
  fetchAuthToken: function() {
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

    this.accessToken = response.json('access_token');
    this.expiryDate = Date.now() + response.json('expires_in') * 1000;

    console.log(`Authentication token fetched successfully.`);
    http_response.add(1, { status: response.status, endpoint: 'token-request' });
  },
  renew: function () {
    if (this.expiryDate > Date.now()) {
      return this;
    }
    this.fetchAuthToken();
    return this;
  },
};

export const options = {
  scenarios: {
    topology: {
      exec: 'checkTopology',
      executor: 'constant-vus',
      duration: '1d',
      vus: 100,
    },
    searchInstances: {
      exec: 'checkForProcessInstances',
      executor: 'constant-vus',
      duration: '1d',
      vus: 5,
    },
  },
};

export function setup() {

}

export function checkTopology (context) {
  const renewedToken = token.renew();
  const params = {
      headers: {
        'Content-Type': 'application/json',
        'Authorization': "Bearer " + renewedToken.accessToken,
      }
    };

  const url = __ENV.ZEEBE_REST_ADDRESS + '/v1/topology';
  http.get(url, params);
  sleep(1);
};


# https://docs.camunda.io/docs/next/apis-tools/orchestration-cluster-api-rest/specifications/search-process-instances/
export function checkForProcessInstances (context) {
  const renewedToken = token.renew();
  const params = {
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        'Authorization': "Bearer " + renewedToken.accessToken,
      }
    };

  const url = __ENV.ZEEBE_REST_ADDRESS + '/v1/process-instances/search';
  http.post(url, json.stringify({
    "sort": [
      {
        "field": "startTime",
        "direction": "DESC"
      }
    ], "filter": {
      "processDefinitionId": "benchmark"
    }
  }), params);
  sleep(1);
};