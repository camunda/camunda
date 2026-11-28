/* Client for the Camunda API, including its authentication.
 *
 * Expose the Camunda API endpoints via convenient functions to be used during tests.
 * Create the client in the test script at module level: that code runs once per VU, so each VU
 * has its own instance.
 */
import http from "k6/http";
import { sleep } from "k6";

export class Client {
  constructor() {
    this.#ensureEnvVars();
    this.baseURL = __ENV.CAMUNDA_BASE_URL;
    // Empty token. The first request fetches the actual token.
    this.token = { accessToken: null, renewalDate: 0 };
  }

  get(endpoint, params) {
    return this.request("GET", endpoint, undefined, params);
  }

  post(endpoint, payload, params) {
    return this.request("POST", endpoint, payload, params);
  }

  /**
   * Send an authenticated JSON request to a Camunda endpoint, renewing the token if needed.
   *
   * @param {string} method - HTTP method.
   * @param {string} endpoint - Path appended to the base URL. Also used as the `name` tag, so that
   *   metrics are grouped per endpoint.
   * @param {*} [payload] - Request body. Serialized as JSON, unless `params.multipart` is true: the
   *   payload is then passed to k6 as is, and k6 encodes the `http.file()` values as
   *   multipart/form-data.
   * @param {object} [params] - k6 request params, merged into the default ones. Its `headers` and
   *   `tags` take precedence over the default headers and tags.
   * @param {boolean} [params.multipart=false] - Send the payload as multipart/form-data.
   * @returns {import("k6/http").RefinedResponse} The k6 response.
   */
  request(method, endpoint, payload, { multipart = false, ...params } = {}) {
    this.#renewToken();

    const contentTypeHeader = multipart
      ? {}
      : { "Content-Type": "application/json" };

    // Create a new k6 params object based on the passed parameters and default values.
    const mergedParams = {
      ...params,
      headers: {
        ...contentTypeHeader,
        Authorization: `Bearer ${this.token.accessToken}`,
        ...params.headers,
      },
      tags: { name: endpoint, ...params.tags },
    };

    let body = null;
    if (payload !== undefined) {
      // Do not build a `FormData` here: it can use **way** more memory than
      // the plain object that k6 encodes.
      body = multipart ? payload : JSON.stringify(payload);
    }

    return http.request(method, this.baseURL + endpoint, body, mergedParams);
  }

  // https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/specifications/create-deployment/
  deploy(name, resourceContent) {
    return this.post(
      "/v2/deployments",
      { resources: http.file(resourceContent, name) },
      { multipart: true },
    );
  }

  // https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/specifications/evaluate-decision/
  decisionDefinitionEvaluation(decisionDefinitionId, variables = {}) {
    return this.post("/v2/decision-definitions/evaluation", {
      decisionDefinitionId: decisionDefinitionId,
      variables: variables,
    });
  }

  // https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/specifications/search-process-definitions/
  searchProcessDefinitions() {
    return this.post("/v2/process-definitions/search", {
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
    return this.post("/v2/process-instances/search", {
      sort: [{ field: "startDate", order: "DESC" }],
      filter: {
        processDefinitionId: processDefinitionId,
        $or: [{ state: "ACTIVE" }, { hasIncident: true }],
      },
      page: {
        limit: 100,
      },
    });
  }

  // https://docs.camunda.io/docs/next/apis-tools/orchestration-cluster-api-rest/specifications/get-topology/
  topology() {
    return this.get("/v2/topology");
  }

  /* Wait for Camunda to be ready. Intended to be called from `setup()`. */
  waitUntilReady() {
    const waitTime = 10; // seconds
    while (true) {
      try {
        const response = this.topology();
        if (response.status !== 200) {
          throw new Error(
            `Camunda topology endpoint returned status: ${response.status}`,
          );
        }
        break;
      } catch (e) {
        console.error(
          `Error waiting for Camunda to be ready (will retry in ${waitTime}s).`,
          e,
        );
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
      if (value === undefined || value === "") {
        console.error(`Environment variable ${name} is not defined or empty.`);
        return true;
      }
      return false;
    });

    if (missing.length > 0) {
      throw Error(`Missing environment variables: ${missing.join(", ")}`);
    }
  }

  #fetchToken() {
    const tokenURL = __ENV.CAMUNDA_OAUTH_URL;
    console.debug(`Fetching authentication token from: ${tokenURL}`);

    const payload = {
      grant_type: "client_credentials",
      audience: __ENV.CAMUNDA_TOKEN_AUDIENCE,
      client_id: __ENV.CAMUNDA_CLIENT_ID,
      client_secret: __ENV.CAMUNDA_CLIENT_SECRET,
    };

    const params = {
      tags: { name: "token-request" },
    };

    console.debug(
      `Requesting token from ${tokenURL} using client ID=${payload.client_id}`,
    );
    const response = http.post(tokenURL, payload, params);
    if (response.status != 200) {
      throw new Error(
        `unable to fetch token, got HTTP status=${response.status}, body=${response.body}`,
      );
    }

    const accessToken = response.json("access_token");

    // Stagger token renewal.
    const expiresIn = response.json("expires_in");
    const renewalJitterSeconds = 1 + Math.floor(Math.random() * 60);
    const renewIn = expiresIn - renewalJitterSeconds;

    this.token.accessToken = accessToken;
    this.token.renewalDate = Date.now() + renewIn * 1000;

    console.log(
      `Authentication token fetched successfully; expire in ${expiresIn}s, will renew in ${renewIn}s`,
    );
  }

  #renewToken() {
    if (this.token.renewalDate > Date.now()) {
      return;
    }

    console.debug("Token will expire soon, will renew now.");
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
