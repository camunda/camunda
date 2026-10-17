// Helpers shared by the unit tests of the k6 scripts and libraries.
import assert from "node:assert/strict";
import { beforeEach, mock } from "node:test";

export const okResponse = { status: 200 };

export const tokenResponse = (accessToken = "token-1", expiresIn = 300) => ({
  status: 200,
  json: (key) => ({ access_token: accessToken, expires_in: expiresIn })[key],
});

const camundaEnv = () => ({
  CAMUNDA_BASE_URL: "http://camunda:8080",
  CAMUNDA_CLIENT_ID: "my-client",
  CAMUNDA_CLIENT_SECRET: "my-secret",
  CAMUNDA_OAUTH_URL: "http://keycloak/token",
  CAMUNDA_TOKEN_AUDIENCE: "my-audience",
});

// Mocks the k6 runtime modules, sets the Camunda environment, imports the module and registers the
// `beforeEach` hook that resets the mocks. Call it once at the top level of a test file.
export async function loadK6Module(path, { data } = {}) {
  const http = { request: mock.fn(), post: mock.fn(), file: mock.fn() };
  const check = mock.fn();
  const sleep = mock.fn();
  mock.module("k6/http", { exports: { default: http } });
  mock.module("k6", { exports: { check, sleep } });
  if (data) {
    mock.module(new URL("../scripts/lib.data.js", import.meta.url).href, {
      exports: { loadData: data },
    });
  }

  globalThis.__ENV = camundaEnv();

  const script = await import(path);

  beforeEach(() => {
    globalThis.__ENV = camundaEnv();
    http.request.mock.resetCalls();
    http.post.mock.resetCalls();
    http.file.mock.resetCalls();
    check.mock.resetCalls();
    sleep.mock.resetCalls();
    http.request.mock.mockImplementation(() => okResponse);
    http.post.mock.mockImplementation(() => tokenResponse());
    http.file.mock.mockImplementation((content, name) => ({ content, name }));
    for (const method of ["log", "debug", "info", "error"]) {
      mock.method(console, method, () => {});
    }
  });

  return {
    script,
    http,
    sleep,
    check,
    respondWith: (...responses) =>
      http.request.mock.mockImplementation(() => responses.shift()),
    checkResults: () => statusCheckResults(check),
  };
}

export function assertScenariosExecExportedFunctions(script) {
  for (const [name, scenario] of Object.entries(script.options.scenarios)) {
    assert.equal(
      typeof script[scenario.exec],
      "function",
      `scenario ${name} exec=${scenario.exec}`,
    );
  }
}

export function statusCheckResults(check) {
  return check.mock.calls.map(({ arguments: [response, checks] }) =>
    checks["status is 200"](response),
  );
}
