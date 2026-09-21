import http from 'k6/http';
import { check } from 'k6';

import { createToken, renew, ensureEnvVars } from './lib.helpers.js';

// DMN decision-evaluation load test.
//
// Fires evaluations against POST /v2/decision-definitions/evaluation at a fixed
// arrival rate (requests/second), independent of response latency, so the target
// throughput is what you asked for and latency degradation shows up as a growing
// VU count rather than a slowing request rate.
//
// Tunables (all via env, with sane defaults):
//   DMN_DECISION_ID   decisionDefinitionId to evaluate      (default: small_decision)
//   DMN_VARIABLES     JSON object of input variables        (default: {})
//   DMN_RATE          evaluations per second                (default: 100)
//   DMN_DURATION      how long to sustain the rate          (default: 60s)
//   DMN_MAX_VUS       upper bound on concurrent requests    (default: 200)
//
// The thresholds encode the S-34406 SLO: p95 < 200ms, p99 < 350ms, and 99.9% of
// calls succeed. k6 exits non-zero if any threshold is breached.

const RATE = Number(__ENV.DMN_RATE || 100);
const DURATION = __ENV.DMN_DURATION || '60s';
const MAX_VUS = Number(__ENV.DMN_MAX_VUS || 200);
const DECISION_ID = __ENV.DMN_DECISION_ID || 'small_decision';
const VARIABLES = JSON.parse(__ENV.DMN_VARIABLES || '{}');

export const options = {
  scenarios: {
    dmnEvaluation: {
      exec: 'evaluateDecision',
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.min(RATE, MAX_VUS),
      maxVUs: MAX_VUS,
    },
  },
  thresholds: {
    'http_req_duration{name:/v2/decision-definitions/evaluation}': ['p(95)<200', 'p(99)<350'],
    'checks': ['rate>0.999'],
  },
};

export async function setup() {
  ensureEnvVars();
  const token = createToken();
  // The returned context object must be JSON serializable.
  return { token: token };
}

export function evaluateDecision(context) {
  const token = renew(context.token);
  const endpoint = '/v2/decision-definitions/evaluation';
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ' + token.accessToken,
    },
    tags: { name: endpoint },
  };
  const payload = {
    decisionDefinitionId: DECISION_ID,
    variables: VARIABLES,
  };

  const res = http.post(__ENV.CAMUNDA_BASE_URL + endpoint, JSON.stringify(payload), params);
  check(res, {
    'status is 200': (r) => r.status === 200,
  });
};
