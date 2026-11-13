/* Client for the internal Optimize REST API used by the Optimize web application.
 *
 * Authentication uses the `OPTIMIZE_*` environment variables, see `Client`.
 */
import { Client } from "./lib.camunda.js";

const HEADERS = {
  "X-Optimize-Client-Timezone": "UTC",
  "X-Optimize-Client-Locale": "en",
};

export class OptimizeClient {
  #client = new Client("OPTIMIZE");

  #get(endpoint, params) {
    return this.#client.get(endpoint, { ...params, headers: HEADERS });
  }

  #post(endpoint, payload, params) {
    return this.#client.post(endpoint, payload, {
      ...params,
      headers: HEADERS,
    });
  }

  // Internal API, not part of the public Optimize API specification.
  managementDashboard() {
    return this.#get("/api/dashboard/management", {
      tags: { page: "homepage", phase: "dashboard" },
    });
  }

  instantDashboard(processDefinitionKey) {
    return this.#get(`/api/dashboard/instant/${processDefinitionKey}`, {
      tags: {
        name: "/api/dashboard/instant/{key}",
        page: "detailed",
        phase: "dashboard",
      },
    });
  }

  processOverview() {
    return this.#get("/api/process/overview");
  }

  evaluateReport(reportId, page) {
    return this.#post(
      `/api/report/${reportId}/evaluate`,
      {},
      {
        tags: {
          name: "/api/report/{id}/evaluate",
          page,
          phase: "report_evaluate",
          report_id: reportId,
        },
      },
    );
  }
}
