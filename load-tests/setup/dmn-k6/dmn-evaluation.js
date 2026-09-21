import http from 'k6/http';
import { check } from 'k6';
import encoding from 'k6/encoding';

// Self-contained DMN decision-evaluation load test.
//
// Drives POST /v2/decision-definitions/evaluation at a FIXED arrival rate
// (requests/second) so the target throughput is held independent of latency:
// latency degradation shows up as a growing VU count, not a slowing rate.
//
// No external imports and no dependency on any chart/operator — it runs as-is
// with `k6 run`, or inside the k6 Job in job.yaml.
//
// Auth (pick whichever your cluster uses; checked in this order):
//   CAMUNDA_BASIC_AUTH = "user:pass"          -> Basic auth
//   CAMUNDA_CLIENT_ID (+SECRET/OAUTH_URL/...) -> OAuth2 client-credentials Bearer
//   (neither set)                             -> no Authorization header
//
// Single vs. mixed:
//   default            -> one decision, DMN_DECISION_ID (default: small_decision)
//   DMN_MIX set        -> weighted mix, JSON array of {id, weight, variables?}
//
// Load knobs:
//   DMN_RATE      evaluations per second               (default: 100)
//   DMN_DURATION  how long to sustain the rate          (default: 60s)
//   DMN_MAX_VUS   cap on concurrent in-flight requests  (default: 200)
//   DMN_VARIABLES JSON input variables for single mode  (default: {})

const RATE = Number(__ENV.DMN_RATE || 100);
const DURATION = __ENV.DMN_DURATION || '60s';
const MAX_VUS = Number(__ENV.DMN_MAX_VUS || 200);
const BASE_URL = __ENV.CAMUNDA_BASE_URL || 'http://camunda-gateway:8080';

const MIX = __ENV.DMN_MIX
  ? JSON.parse(__ENV.DMN_MIX)
  : [
      {
        id: __ENV.DMN_DECISION_ID || 'small_decision',
        weight: 1,
        variables: JSON.parse(__ENV.DMN_VARIABLES || '{}'),
      },
    ];

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

// --- Auth --------------------------------------------------------------------
// Token is cached at module scope and refreshed shortly before it expires.
let cachedToken = { accessToken: null, expiryDate: 0 };

function bearerToken() {
  if (cachedToken.expiryDate > Date.now() + 5000) {
    return cachedToken.accessToken;
  }
  const res = http.post(
    __ENV.CAMUNDA_OAUTH_URL,
    {
      grant_type: 'client_credentials',
      audience: __ENV.CAMUNDA_TOKEN_AUDIENCE,
      client_id: __ENV.CAMUNDA_CLIENT_ID,
      client_secret: __ENV.CAMUNDA_CLIENT_SECRET,
    },
    { tags: { name: 'token-request' } },
  );
  if (res.status !== 200) {
    throw new Error(`unable to fetch token: ${res.status} ${res.body}`);
  }
  cachedToken.accessToken = res.json('access_token');
  cachedToken.expiryDate = Date.now() + res.json('expires_in') * 1000;
  return cachedToken.accessToken;
}

function authHeader() {
  if (__ENV.CAMUNDA_BASIC_AUTH) {
    return 'Basic ' + encoding.b64encode(__ENV.CAMUNDA_BASIC_AUTH);
  }
  if (__ENV.CAMUNDA_CLIENT_ID) {
    return 'Bearer ' + bearerToken();
  }
  return null;
}

// --- Scenario ----------------------------------------------------------------
export const options = {
  scenarios: {
    dmnEvaluation: {
      exec: 'evaluate',
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.min(RATE, MAX_VUS),
      maxVUs: MAX_VUS,
    },
  },
  thresholds: {
    'http_req_duration{name:evaluation}': ['p(95)<200', 'p(99)<350'],
    checks: ['rate>0.999'],
  },
};

export function evaluate() {
  const decision = pickDecision();
  const headers = { 'Content-Type': 'application/json' };
  const auth = authHeader();
  if (auth) {
    headers['Authorization'] = auth;
  }
  const params = {
    headers: headers,
    // Constant tag so per-URL cardinality stays low; break down by decision.
    tags: { name: 'evaluation', decision: decision.id },
  };
  const payload = {
    decisionDefinitionId: decision.id,
    variables: decision.variables || {},
  };

  const res = http.post(
    BASE_URL + '/v2/decision-definitions/evaluation',
    JSON.stringify(payload),
    params,
  );
  check(res, { 'status is 200': (r) => r.status === 200 });
}
