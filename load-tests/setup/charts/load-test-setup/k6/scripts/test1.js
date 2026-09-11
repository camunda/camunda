import http from 'k6/http';
import { sleep } from 'k6';
import { Counter } from 'k6/metrics';

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

    const params = {
      tags: { name: 'token-request' },
    };

    const response = http.post(token_url, payload, params);
    if (response.status != 200) {
      const result = response.body;
      throw new Error(`unable to fetch token: ${result}`);
    }

    this.accessToken = response.json('access_token');
    this.expiryDate = Date.now() + response.json('expires_in') * 1000;

    console.log(`Authentication token fetched successfully.`);
  },
  renew: function() {
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
      vus: 2,
    },
    searchInstances: {
      exec: 'checkForProcessInstances',
      executor: 'constant-vus',
      duration: '1d',
      vus: 2,
    },
  },
};

export async function setup() {
  console.log('Setting up the test...');
  const context = {};
  return context;
}

export async function checkTopology(context) {
  const renewedToken = token.renew();
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': "Bearer " + renewedToken.accessToken,
    },
    tags: { name: '/v1/topology' },
  };

  console.log(`Checking topology of the cluster`);
  const url = __ENV.ZEEBE_REST_ADDRESS + '/v1/topology';
  http.get(url, params);
  sleep(1);
};


// https://docs.camunda.io/docs/next/apis-tools/orchestration-cluster-api-rest/specifications/search-process-instances/
export async function checkForProcessInstances(context) {
  const renewedToken = token.renew();
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'Authorization': "Bearer " + renewedToken.accessToken,
    },
    tags: { name: '/v2/process-instances/search' },
  };

  const url = __ENV.ZEEBE_REST_ADDRESS + '/v2/process-instances/search';

  console.log(`Searching for process instances with filter`);
  http.post(url, JSON.stringify({
    "sort": [
      {
        "field": "startDate",
        "order": "DESC"
      }
    ], "filter": {
      "processDefinitionId": "benchmark"
    }
  }), params);
  sleep(1);
};
