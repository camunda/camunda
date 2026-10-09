// Unit tests for `scripts/lib.camunda.js`. Run with `make test` in the `k6` directory.
//
// k6 modules (`k6`, `k6/http`) only exist inside the k6 runtime, so they are replaced with mocks.
import { afterEach, beforeEach, describe, it, mock } from "node:test";
import assert from "node:assert/strict";
import { loadK6Module, okResponse, tokenResponse } from "./helpers.js";

const {
  script: { Client },
  http,
  sleep,
} = await loadK6Module("../scripts/lib.camunda.js");

let now;

beforeEach(() => {
  now = 1_000_000;
  mock.method(Date, "now", () => now);
  // Renewal jitter is `1 + floor(random * 60)` seconds: 0 gives exactly 1s.
  mock.method(Math, "random", () => 0);
});

afterEach(() => {
  mock.restoreAll();
});

describe("Client constructor", () => {
  it("should list missing and empty variables", () => {
    // given
    delete globalThis.__ENV.CAMUNDA_BASE_URL;
    globalThis.__ENV.CAMUNDA_CLIENT_ID = "";

    // when / then
    assert.throws(
      () => new Client(),
      /Missing environment variables: CAMUNDA_BASE_URL, CAMUNDA_CLIENT_ID$/,
    );
  });
});

describe("Client.request", () => {
  it("should fetch a token then send an authenticated request", () => {
    // given
    const client = new Client();

    // when
    const response = client.get("/v2/topology");

    // then
    assert.equal(response, okResponse);
    const [tokenUrl, tokenPayload, tokenParams] =
      http.post.mock.calls[0].arguments;
    assert.equal(tokenUrl, "http://keycloak/token");
    assert.deepEqual(tokenPayload, {
      grant_type: "client_credentials",
      audience: "my-audience",
      client_id: "my-client",
      client_secret: "my-secret",
    });
    assert.deepEqual(tokenParams, { tags: { name: "token-request" } });
    const [method, url, body, params] = http.request.mock.calls[0].arguments;
    assert.equal(method, "GET");
    assert.equal(url, "http://camunda:8080/v2/topology");
    assert.equal(body, null);
    assert.deepEqual(params.headers, {
      "Content-Type": "application/json",
      Authorization: "Bearer token-1",
    });
    assert.deepEqual(params.tags, { name: "/v2/topology" });
  });

  it("should serialize the payload and let caller params override defaults", () => {
    // given
    const client = new Client();
    const params = {
      timeout: "5s",
      headers: { "Content-Type": "text/plain" },
      tags: { name: "custom", extra: "tag" },
    };

    // when
    client.post("/v2/things", { a: 1 }, params);

    // then
    const [method, , body, actual] = http.request.mock.calls[0].arguments;
    assert.equal(method, "POST");
    assert.equal(body, '{"a":1}');
    assert.equal(actual.timeout, "5s");
    assert.deepEqual(actual.headers, {
      "Content-Type": "text/plain",
      Authorization: "Bearer token-1",
    });
    assert.deepEqual(actual.tags, { name: "custom", extra: "tag" });
  });
});

describe("token renewal", () => {
  it("should reuse the token until the renewal date then renew it", () => {
    // given: renewal date is expires_in (300s) minus the 1s jitter.
    const client = new Client();
    client.get("/a");

    // when
    now += 298_000;
    client.get("/b");

    // then
    assert.equal(http.post.mock.callCount(), 1);

    // when
    http.post.mock.mockImplementation(() => tokenResponse("token-2"));
    now += 1_001;
    client.get("/c");

    // then
    assert.equal(http.post.mock.callCount(), 2);
    assert.equal(
      http.request.mock.calls[2].arguments[3].headers.Authorization,
      "Bearer token-2",
    );
  });

  it("should fail the first request when the token cannot be fetched", () => {
    // given
    http.post.mock.mockImplementation(() => ({ status: 401, body: "denied" }));

    // when / then
    assert.throws(
      () => new Client().get("/a"),
      /unable to fetch token, got HTTP status=401, body=denied/,
    );
    assert.equal(http.request.mock.callCount(), 0);
    assert.deepEqual(sleep.mock.calls[0].arguments, [1]);
  });

  it("should keep the previous token when renewal fails", () => {
    // given
    const client = new Client();
    client.get("/a");
    http.post.mock.mockImplementation(() => ({ status: 503, body: "down" }));

    // when
    now += 299_001;
    client.get("/b");

    // then
    assert.equal(
      http.request.mock.calls[1].arguments[3].headers.Authorization,
      "Bearer token-1",
    );
    assert.deepEqual(sleep.mock.calls[0].arguments, [1]);
  });
});

describe("endpoint helpers", () => {
  it("should call the Camunda search and topology endpoints", () => {
    // given
    const client = new Client();

    // when
    client.topology();
    client.searchProcessDefinitions();
    client.searchProcessInstances("my-process");

    // then
    const calls = http.request.mock.calls.map((c) => c.arguments);
    assert.deepEqual(
      calls.map(([method, url]) => [method, url]),
      [
        ["GET", "http://camunda:8080/v2/topology"],
        ["POST", "http://camunda:8080/v2/process-definitions/search"],
        ["POST", "http://camunda:8080/v2/process-instances/search"],
      ],
    );
    assert.equal(JSON.parse(calls[1][2]).sort[0].field, "processDefinitionKey");
    assert.equal(
      JSON.parse(calls[2][2]).filter.processDefinitionId,
      "my-process",
    );
  });
});

describe("Client.waitUntilReady", () => {
  it("should retry every 10 seconds until topology returns 200", () => {
    // given: a non-200 response and a thrown error both trigger a retry.
    const results = [
      { status: 503 },
      new Error("connection refused"),
      okResponse,
    ];
    http.request.mock.mockImplementation(() => {
      const result = results.shift();
      if (result instanceof Error) {
        throw result;
      }
      return result;
    });

    // when
    new Client().waitUntilReady();

    // then
    assert.equal(http.request.mock.callCount(), 3);
    assert.deepEqual(
      sleep.mock.calls.map((c) => c.arguments[0]),
      [10, 10],
    );
  });
});
