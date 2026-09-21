import http from 'k6/http';
import { check } from 'k6';

import { createToken, renew, ensureEnvVars } from './lib.helpers.js';

// Mixed DMN decision-evaluation load test.
//
// Same as test.dmn-evaluation.js, but each request picks a decision from a
// WEIGHTED MIX of different table sizes (number of rules), so the load reflects a
// realistic blend rather than one decision. Defaults mirror the S-34406 shape:
// ~42% of calls are the tiny 2-rule table, the rest hit large decision graphs.
//
// Tunables (env):
//   DMN_MIX   JSON array of { "id": <decisionDefinitionId>, "weight": <number>,
//             "variables": <object, optional> }. Weights are relative.
//   DMN_RATE      evaluations per second               (default: 100)
//   DMN_DURATION  how long to sustain the rate          (default: 60s)
//   DMN_MAX_VUS   cap on concurrent in-flight requests  (default: 200)
//
// Thresholds encode the S-34406 SLO: p95 < 200ms, p99 < 350ms, >99.9% success.

const RATE = Number(__ENV.DMN_RATE || 100);
const DURATION = __ENV.DMN_DURATION || '60s';
const MAX_VUS = Number(__ENV.DMN_MAX_VUS || 200);

const DEFAULT_MIX = [
  { id: 'small_decision', weight: 42, variables: {} }, // 2 rules
  { id: 'graph_a_d10', weight: 29, variables: {} }, // large graph (~3.7k rules)
  { id: 'graph_b_d10', weight: 29, variables: {} }, // large graph (~3.3k rules)
];

const MIX = JSON.parse(__ENV.DMN_MIX || JSON.stringify(DEFAULT_MIX));

// Precompute cumulative weights once for O(1)-ish weighted pick per iteration.
const TOTAL_WEIGHT = MIX.reduce((sum, e) => sum + e.weight, 0);

function pickDecision() {
  let r = Math.random() * TOTAL_WEIGHT;
  for (let i = 0; i < MIX.length; i++) {
    r -= MIX[i].weight;
    if (r <= 0) {
      return MIX[i];
    }
  }
  return MIX[MIX.length - 1];
}

export const options = {
  scenarios: {
    dmnEvaluationMixed: {
      exec: 'evaluateMixed',
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

export function evaluateMixed(context) {
  const token = renew(context.token);
  const decision = pickDecision();
  const endpoint = '/v2/decision-definitions/evaluation';
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ' + token.accessToken,
    },
    // Tag by decision so latency can be broken down per table size in Prometheus.
    tags: { name: endpoint, decision: decision.id },
  };
  const payload = {
    decisionDefinitionId: decision.id,
    variables: decision.variables || {},
  };

  const res = http.post(__ENV.CAMUNDA_BASE_URL + endpoint, JSON.stringify(payload), params);
  check(res, {
    'status is 200': (r) => r.status === 200,
  });
};
