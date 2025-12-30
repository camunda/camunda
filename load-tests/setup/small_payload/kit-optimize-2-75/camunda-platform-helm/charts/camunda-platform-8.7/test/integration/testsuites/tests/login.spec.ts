// NOTE: If you get the message connection error: desc = "error reading server preface: http2: frame too large"
// this is likely due to --insecure on the zbctl call while the endpoint is TLS enabled.

/// <reference types="node" />

import { config as dotenv } from "dotenv";
dotenv(); // ← loads .env before anything else

import { test, expect, APIRequestContext } from "@playwright/test";
import { execFileSync } from "child_process";

// ---------- config & helpers ----------
function requireEnv(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`Missing required env var: ${name}`);
  return value;
}

// Grouped config for base URLs
const config = {
  authURL: requireEnv("AUTH_URL"),
  base: {
    console: requireEnv("CONSOLE_BASE_URL"),
    keycloak: requireEnv("KEYCLOAK_BASE_URL"),
    identity: requireEnv("IDENTITY_BASE_URL"),
    operate: requireEnv("OPERATE_BASE_URL"),
    optimize: requireEnv("OPTIMIZE_BASE_URL"),
    tasklist: requireEnv("TASKLIST_BASE_URL"),
    webModeler: requireEnv("WEBMODELER_BASE_URL"),
    connectors: requireEnv("CONNECTORS_BASE_URL"),
    zeebeGRPC: requireEnv("ZEEBE_GATEWAY_GRPC"),
    zeebeREST: requireEnv("ZEEBE_GATEWAY_REST"),
  },
  loginPath: {
    Console: requireEnv("CONSOLE_LOGIN_PATH"),
    Keycloak: requireEnv("KEYCLOAK_LOGIN_PATH"),
    Identity: process.env["IDENTITY_LOGIN_PATH"],
    Operate: requireEnv("OPERATE_LOGIN_PATH"),
    Optimize: requireEnv("OPTIMIZE_LOGIN_PATH"),
    Tasklist: requireEnv("TASKLIST_LOGIN_PATH"),
    WebModeler: requireEnv("WEBMODELER_LOGIN_PATH"),
    connectors: requireEnv("CONNECTORS_LOGIN_PATH"),
    zeebeGRPC: requireEnv("ZEEBE_GATEWAY_GRPC"),
    zeebeREST: requireEnv("ZEEBE_GATEWAY_REST"),
  },
  secrets: {
    connectors: requireEnv("PLAYWRIGHT_VAR_CONNECTORS_CLIENT_SECRET"),
    tasklist: requireEnv("PLAYWRIGHT_VAR_TASKLIST_CLIENT_SECRET"),
    operate: requireEnv("PLAYWRIGHT_VAR_OPERATE_CLIENT_SECRET"),
    optimize: requireEnv("PLAYWRIGHT_VAR_OPTIMIZE_CLIENT_SECRET"),
    zeebe: requireEnv("PLAYWRIGHT_VAR_ZEEBE_CLIENT_SECRET"),
  },
  venomID: process.env.TEST_CLIENT_ID ?? "venom",
  venomSec: requireEnv("PLAYWRIGHT_VAR_ADMIN_CLIENT_SECRET"),
  fixturesDir: process.env.FIXTURES_DIR || "/mnt/fixtures",
};

// Helper to fetch a token
async function fetchToken(id: string, sec: string, api: APIRequestContext) {
  const r = await api.post(config.authURL, {
    form: {
      client_id: id,
      client_secret: sec,
      grant_type: "client_credentials",
    },
  });
  expect(
    r.ok(),
    `Failed to get token for client_id=${id}: ${r.status()}`,
  ).toBeTruthy();
  return (await r.json()).access_token as string;
}

// ---------- tests ----------
test.describe("Camunda core", () => {
  let api: APIRequestContext;
  let venomJWT: string;

  test.beforeAll(async ({ playwright }) => {
    api = await playwright.request.newContext();
    venomJWT = await fetchToken(config.venomID, config.venomSec, api);
  });

  test("M2M tokens", async () => {
    for (const [id, sec] of Object.entries(config.secrets)) {
      // ensure each call resolves and yields a non-empty JWT:
      await expect(fetchToken(id, sec, api)).resolves.toMatch(
        /^[\w-]+\.[\w-]+\.[\w-]+$/,
      );
    }
  });

  // Parameterized login page tests
  for (const [name, url] of Object.entries({
    Console: config.base.console,
    Keycloak: config.base.keycloak,
    Identity: config.base.identity,
    Operate: config.base.operate,
    Optimize: config.base.optimize,
    Tasklist: config.base.tasklist,
    WebModeler: config.base.webModeler,
  })) {
    test(`Login page: ${name}`, async () => {
      const r = await api.get(`${url}${config.loginPath[name]}`, {
        timeout: 45_000,
      });
      expect(
        r.ok(),
        `Login page failed for ${name}: ${r.status()}`,
      ).toBeTruthy();
    });
  }
  test("Connectors inbound page", async () => {
    expect(
      (await api.get(config.base.connectors, { timeout: 45_000 })).ok(),
      "Connectors inbound page failed",
    ).toBeTruthy();
  });

  // Parameterized API endpoint tests
  for (const [label, url, method, body] of [
    ["Console clusters", `${config.base.console}/api/clusters`, "GET", ""],
    ["Identity users", `${config.base.identity}api/users`, "GET", ""],
    [
      "Operate defs",
      `${config.base.operate}/v1/process-definitions/search`,
      "POST",
      "{}",
    ],
    [
      "Tasklist tasks",
      `${config.base.tasklist}/graphql`,
      "POST",
      '{"query":"{tasks(query:{}){id name}}"}',
    ],
  ] as const) {
    test(`API: ${label}`, async () => {
      const r = await api.fetch(url, {
        method,
        data: body || undefined,
        headers: {
          Authorization: `Bearer ${venomJWT}`,
          "Content-Type": "application/json",
        },
      });
      expect(
        r.ok(),
        `API call failed for ${label}: ${r.status()}`,
      ).toBeTruthy();
    });
  }

  test("WebModeler login page", async () => {
    const r = await api.get(config.base.webModeler, { timeout: 45_000 });
    expect(r.ok(), "WebModeler login page failed").toBeTruthy();
    expect(await r.text(), "WebModeler login page contains error").not.toMatch(
      /error/i,
    );
  });

  //  test("Zeebe status (gRPC)", async () => {
  //    const extra =
  //      process.env.ZBCTL_EXTRA_ARGS?.trim().split(/\s+/).filter(Boolean) ?? [];
  //    const out = execFileSync(
  //      "zbctl",
  //      [
  //        "status",
  //        "--clientCache",
  //        "/tmp/zeebe",
  //        "--clientId",
  //        config.venomID,
  //        "--clientSecret",
  //        config.venomSec,
  //        "--authzUrl",
  //        config.authURL,
  //        "--address",
  //        config.base.zeebeGRPC,
  //        ...extra,
  //      ],
  //      { encoding: "utf-8" },
  //    );
  //    expect(out, "zbctl status output missing Leader, Healthy").toMatch(
  //      /Leader, Healthy/,
  //    );
  //    expect(out, "zbctl status output contains Unhealthy").not.toMatch(
  //      /Unhealthy/,
  //    );
  //  });
  //
  //  test("Zeebe topology (REST)", async () => {
  //    const r = await api.get(`${config.base.zeebeREST}/v1/topology`, {
  //      headers: { Authorization: `Bearer ${venomJWT}` },
  //    });
  //    expect(r.ok(), "Zeebe topology REST call failed").toBeTruthy();
  //    expect(
  //      await r.json(),
  //      "Zeebe topology response missing brokers",
  //    ).toHaveProperty("brokers");
  //  });
  //
  // Parameterized BPMN deploy tests
  for (const [name, file] of [
    ["Basic", "test-process.bpmn"],
    ["Inbound", "test-inbound-process.bpmn"],
  ] as const) {
    const extra =
      process.env.ZBCTL_EXTRA_ARGS?.trim().split(/\s+/).filter(Boolean) ?? [];
    test(`Deploy BPMN: ${name}`, async () => {
      execFileSync(
        "zbctl",
        [
          "deploy",
          `${config.fixturesDir}/${file}`,
          "--clientCache",
          "/tmp/zeebe",
          "--clientId",
          config.venomID,
          "--clientSecret",
          config.venomSec,
          "--authzUrl",
          config.authURL,
          "--address",
          config.base.zeebeGRPC,
          ...extra,
        ],
        { stdio: "inherit" },
      );
    });
  }

  // Parameterized process visibility tests

  for (const [bpmnId, label, file] of [
    ["it-test-process", "Basic", "test-process.bpmn"],
    ["test-inbound-process", "Inbound", "test-inbound-process.bpmn"],
  ] as const) {
    test(`Process visible: ${label}`, async () => {
      test.setTimeout(3 * 60 * 1000); // > polling window
      const extra =
        process.env.ZBCTL_EXTRA_ARGS?.trim().split(/\s+/).filter(Boolean) ?? [];
      execFileSync(
        "zbctl",
        [
          "deploy",
          `${config.fixturesDir}/${file}`,
          "--clientCache",
          "/tmp/zeebe",
          "--clientId",
          config.venomID,
          "--clientSecret",
          config.venomSec,
          "--authzUrl",
          config.authURL,
          "--address",
          config.base.zeebeGRPC,
          ...extra,
        ],
        { stdio: "inherit" },
      );

      const timeoutMs = 2 * 60 * 1000;
      const intervalMs = 5 * 1000;
      const start = Date.now();

      let found = false;
      let lastStatus = 0;

      /* eslint-disable no-await-in-loop */
      while (Date.now() - start < timeoutMs) {
        const r = await api.post(
          `${config.base.operate}/v1/process-definitions/search`,
          {
            // Send JSON, not a string. Empty object returns all items.
            data: {},
            headers: {
              Authorization: `Bearer ${venomJWT}`,
              "Content-Type": "application/json",
            },
          },
        );

        lastStatus = r.status();

        if (r.ok()) {
          const data = await r.json();
          const ids = (data.items as Array<{ bpmnProcessId: string }> | []).map(
            (i) => i.bpmnProcessId,
          );

          if (ids.includes(bpmnId)) {
            found = true;
            break; // success
          }
        }

        await new Promise((res) => setTimeout(res, intervalMs));
      }

      expect(
        found,
        `Process ${bpmnId} not visible in Operate within ${timeoutMs / 1000}s (last status ${lastStatus})`,
      ).toBeTruthy();
    });
  }
});
