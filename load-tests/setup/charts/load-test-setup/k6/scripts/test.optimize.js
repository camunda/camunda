import { check, sleep } from "k6";

import { OptimizeClient } from "./lib.optimize.js";

// Module-level code runs once per VU, so each VU has its own client.
const client = new OptimizeClient();

// Ignore process definitions that are created by k6 itself: they never have instances.
const IGNORED_PREFIX = "k6-";

/* Opens the Optimize dashboards like the web application does and evaluates every report on them.
 * Requires Optimize. Latency is in the `http_req_duration` metric, tagged with `page`, `phase`
 * and `report_id`. */
export const options = {
  // Give enough time for Optimize to import a process definition.
  setupTimeout: "30m",
  scenarios: {
    /* Management dashboard shown after login, and its reports. */
    homepage: {
      exec: "evaluateHomepage",
      executor: "constant-arrival-rate",
      startTime: "10s",
      duration: "60d", // As long as the longest benchmark we run
      rate: 1,
      timeUnit: "1m",
      preAllocatedVUs: 1,
      maxVUs: 2,
    },
    /* Instant dashboard of one process definition, and its reports. */
    detailed: {
      exec: "evaluateDetailed",
      executor: "constant-arrival-rate",
      startTime: "10s",
      duration: "60d",
      rate: 1,
      timeUnit: "1m",
      preAllocatedVUs: 1,
      maxVUs: 2,
    },
  },
};

export function setup() {
  const configured = __ENV.OPTIMIZE_PROCESS_DEFINITION_KEY;
  if (configured) {
    console.log(`Will evaluate the process definition ${configured}.`);
    return { processDefinitionKey: configured };
  }

  const waitTime = 10; // seconds
  let processDefinitionKey;
  while (processDefinitionKey === undefined) {
    const response = client.processOverview();
    if (response.status == 200) {
      const items = response
        .json()
        .filter((i) => !i.processDefinitionKey.startsWith(IGNORED_PREFIX));
      if (items.length > 0) {
        processDefinitionKey = items[0].processDefinitionKey;
        break;
      }
      console.info(
        `No process definition found in Optimize yet (will retry in ${waitTime}s).`,
      );
    } else {
      console.error(
        `Unable to get the Optimize process overview (will retry in ${waitTime}s), got HTTP status=${response.status}`,
      );
    }
    sleep(waitTime);
  }
  console.log(`Will evaluate the process definition ${processDefinitionKey}.`);

  return { processDefinitionKey };
}

export function evaluateHomepage() {
  evaluateReports(client.managementDashboard(), "homepage");
}

export function evaluateDetailed(data) {
  evaluateReports(
    client.instantDashboard(data.processDefinitionKey),
    "detailed",
  );
}

function evaluateReports(dashboard, page) {
  const ok = check(dashboard, { "status is 200": (r) => r.status === 200 });
  if (!ok) {
    return;
  }
  // Text and image tiles have no report, so no `id`.
  const tiles = (dashboard.json("tiles") ?? []).filter((tile) => tile.id);
  for (const tile of tiles) {
    const response = client.evaluateReport(tile.id, page);
    check(response, { "status is 200": (r) => r.status === 200 });
  }
}
