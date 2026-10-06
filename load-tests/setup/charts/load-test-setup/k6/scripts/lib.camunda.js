/* Client for the Camunda API, including its authentication.
 *
 * Expose the Camunda API endpoints via convenient functions to be used during tests.
 * Create the client in the test script at module level: that code runs once per VU, so each VU
 * has its own instance.
 */
import http from 'k6/http';
import { sleep } from 'k6';

export class Client {
  constructor() {
    this.#ensureEnvVars();
    this.baseURL = __ENV.CAMUNDA_BASE_URL;
    // Empty token. The first request fetches the actual token.
    this.token = { accessToken: null, renewalDate: 0 };
  }

  get(endpoint, params) {
    return this.request('GET', endpoint, undefined, params);
  }

  post(endpoint, payload, params) {
    return this.request('POST', endpoint, payload, params);
  }

  /* Send an authenticated JSON request to a Camunda endpoint, renewing the token if needed.
   *
   * The endpoint is also used as the `name` tag, so that metrics are grouped per endpoint.
   * `payload` is optional and is serialized as JSON.
   * `params` is optional: these k6 request params are merged into the default ones, and its
   * `headers` and `tags` take precedence over the default headers and tags. */
  request(method, endpoint, payload, params = {}) {
    this.#renewToken();
    const mergedParams = {
      ...params,
      headers: {
        'Content-Type': 'application/json',
        'Authorization': "Bearer " + this.token.accessToken,
        ...params.headers,
      },
      tags: { name: endpoint, ...params.tags },
    };
    const body = payload === undefined ? null : JSON.stringify(payload);
    return http.request(method, this.baseURL + endpoint, body, mergedParams);
  }

  // https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/specifications/search-process-definitions/
  searchProcessDefinitions() {
    return this.post('/v2/process-definitions/search', {
      sort: [
        {
          field: "processDefinitionKey",
          order: "ASC",
        },
      ],
      page: {
        limit: 10,
      },
    });
  }

  // https://docs.camunda.io/docs/next/apis-tools/orchestration-cluster-api-rest/specifications/search-process-instances/
  searchProcessInstances(processDefinitionId) {
    return this.post('/v2/process-instances/search', {
      sort: [
        {field: "startDate", order: "DESC"},
      ],
      filter: {
        processDefinitionId: processDefinitionId,
        $or: [
          { state: "ACTIVE" },
          { hasIncident: true },
        ],
      },
      page: {
        limit: 100,
      },
    });
  }

  // https://docs.camunda.io/docs/next/apis-tools/orchestration-cluster-api-rest/specifications/get-topology/
  topology() {
    return this.get('/v2/topology');
  }

  /* Wait for Camunda to be ready. Intended to be called from `setup()`. */
  waitUntilReady() {
    const waitTime = 10; // seconds
    while (true) {
      try {
        const response = this.topology();
        if (response.status !== 200) {
          throw new Error(`Camunda topology endpoint returned status: ${response.status}`);
        }
        break;
      } catch (e) {
        console.error(`Error waiting for Camunda to be ready (will retry in ${waitTime}s).`, e);
        sleep(waitTime);
      }
    }
    console.log("Camunda seems to be ready ✅");
  }

  #ensureEnvVars() {
    const vars = [
      "CAMUNDA_BASE_URL",
      "CAMUNDA_CLIENT_ID",
      "CAMUNDA_CLIENT_SECRET",
      "CAMUNDA_OAUTH_URL",
      "CAMUNDA_TOKEN_AUDIENCE",
    ];

    const missing = vars.filter((name) => {
      const value = __ENV[name];
      if (value === undefined || value === '') {
        console.error(`Environment variable ${name} is not defined or empty.`);
        return true;
      }
      return false;
    });

    if (missing.length > 0) {
      throw Error(`Missing environment variables: ${missing.join(', ')}`);
    }
  }

  #fetchToken() {
    const tokenURL = __ENV.CAMUNDA_OAUTH_URL;
    console.debug(`Fetching authentication token from: ${tokenURL}`);

    const payload = {
      grant_type: 'client_credentials',
      audience: __ENV.CAMUNDA_TOKEN_AUDIENCE,
      client_id: __ENV.CAMUNDA_CLIENT_ID,
      client_secret: __ENV.CAMUNDA_CLIENT_SECRET,
    };

    const params = {
      tags: { name: 'token-request' },
    };

    console.debug(`Requesting token from ${tokenURL} using client ID=${payload.client_id}`);
    const response = http.post(tokenURL, payload, params);
    if (response.status != 200) {
      throw new Error(`unable to fetch token, got HTTP status=${response.status}, body=${response.body}`);
    }

    const accessToken = response.json('access_token');

    // Stagger token renewal.
    const expiresIn = response.json('expires_in');
    const renewalJitterSeconds = 1 + Math.floor(Math.random() * 60);
    const renewIn = expiresIn - renewalJitterSeconds;

    this.token.accessToken = accessToken;
    this.token.renewalDate = Date.now() + renewIn * 1000;

    console.log(`Authentication token fetched successfully; expire in ${expiresIn}s, will renew in ${renewIn}s`);
  }

  #renewToken() {
    if (this.token.renewalDate > Date.now()) {
      return;
    }

    console.debug("Token will expire soon, will renew now.")
    try {
      this.#fetchToken();
    } catch (e) {
      console.error(`Error while renewing the token.`, e);
      // Known limitation: during an OAuth outage, each VU retries and sleeps 1s per call
      // until the old token expires, which can cause dropped iterations.
      sleep(1);
      // Without a previous token, requests would send "Bearer null".
      if (this.token.accessToken === null) {
        throw e;
      }
    }
  }
}
