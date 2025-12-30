// NOTE: If you get the message connection error: desc = "error reading server preface: http2: frame too large"
// this is likely due to --insecure on the zbctl call while the endpoint is TLS enabled.

/// <reference types="node" />

//TODO: this test should be run first
import { config as dotenv } from "dotenv";
dotenv(); // ← loads .env before anything else

import { test, expect, APIRequestContext } from "@playwright/test";
import { execFileSync } from "child_process";
import { authHeader, fetchToken, requireEnv } from "./helper";

// ---------- config & helpers ----------

// Grouped config for base URLs
const config = {
  authURL: requireEnv("AUTH_URL"),
  authType: requireEnv("TEST_AUTH_TYPE"),
  testBasePath: requireEnv("TEST_BASE_PATH"),
  base: {
    console: requireEnv("CONSOLE_BASE_URL"),
    keycloak: requireEnv("KEYCLOAK_BASE_URL"),
    identity: requireEnv("IDENTITY_BASE_URL"),
    orchestrationcoreTasklist: requireEnv("ORCHESTRATION_TASKLIST_BASE_URL"),
    orchestrationOperate: requireEnv("ORCHESTRATION_OPERATE_BASE_URL"),
    optimize: requireEnv("OPTIMIZE_BASE_URL"),
    webModeler: requireEnv("WEBMODELER_BASE_URL"),
    connectors: requireEnv("CONNECTORS_BASE_URL"),
    zeebeGRPC: requireEnv("ZEEBE_GATEWAY_GRPC"),
    zeebeREST: requireEnv("ZEEBE_GATEWAY_REST"),
  },
  loginPath: {
    Console: requireEnv("CONSOLE_LOGIN_PATH"),
    Keycloak: requireEnv("KEYCLOAK_LOGIN_PATH"),
    Identity: process.env["IDENTITY_LOGIN_PATH"],
    OrchestrationOperate: requireEnv("ORCHESTRATION_OPERATE_LOGIN_PATH"),
    Optimize: requireEnv("OPTIMIZE_LOGIN_PATH"),
    OrchestrationTasklist: requireEnv("ORCHESTRATION_TASKLIST_LOGIN_PATH"),
    WebModeler: requireEnv("WEBMODELER_LOGIN_PATH"),
    connectors: requireEnv("CONNECTORS_LOGIN_PATH"),
    zeebeGRPC: requireEnv("ZEEBE_GATEWAY_GRPC"),
    zeebeREST: requireEnv("ZEEBE_GATEWAY_REST"),
  },
  secrets: {
    connectors: requireEnv("PLAYWRIGHT_VAR_CONNECTORS_CLIENT_SECRET"),
    optimize: requireEnv("PLAYWRIGHT_VAR_OPTIMIZE_CLIENT_SECRET"),
    orchestration: requireEnv("PLAYWRIGHT_VAR_ORCHESTRATION_CLIENT_SECRET"),
  },
  venomID: process.env.TEST_CLIENT_ID ?? "venom",
  venomSec: requireEnv("PLAYWRIGHT_VAR_ADMIN_CLIENT_SECRET"),
  demoUser: "demo",
  demoPass: "demo",
};

// ---------- tests ----------
test.describe("orchestration-rest", () => {
  let api: APIRequestContext;
  let venomJWT: string;

  test.beforeAll(async ({ playwright }) => {
    api = await playwright.request.newContext();
    if (config.authType !== "basic") {
      venomJWT = await fetchToken(config.venomID, config.venomSec, api, config);
    } else {
      venomJWT = "";
    }
  });

  for (const [label, url, method, body] of [
    [
      "Operate defs",
      `${config.base.orchestrationOperate}/v2/process-definitions/search`,
      "POST",
      "{}",
    ],
  ] as const) {
    test(`API: ${label}`, async () => {
      const r = await api.fetch(url, {
        method,
        data: body || undefined,
        headers: {
          Authorization: await authHeader(api, config),
          "Content-Type": "application/json",
        },
      });
      expect(
        r.ok(),
        `API call failed for ${label}: ${r.status()}`,
      ).toBeTruthy();
    });
  }
  // TODO: use REST endpoint to deploy
  // for (const [bpmnId, label, file] of [
  //   ["it-test-process", "Basic", "test-process.bpmn"],
  //   ["test-inbound-process", "Inbound", "test-inbound-process.bpmn"],
  // ] as const) {
  //   test(`Deploy and check model: ${label}`, async () => {
  //     const extra =
  //       process.env.ZBCTL_EXTRA_ARGS?.trim().split(/\s+/).filter(Boolean) ?? [];
  //     execFileSync(
  //       "zbctl",
  //       [
  //         "deploy",
  //         `${config.testBasePath}/${file}`,
  //         "--clientCache",
  //         "/tmp/zeebe",
  //         "--clientId",
  //         config.venomID,
  //         "--clientSecret",
  //         config.venomSec,
  //         "--authzUrl",
  //         config.authURL,
  //         "--address",
  //         config.base.zeebeGRPC,
  //         ...extra,
  //       ],
  //       { stdio: "inherit" },
  //     );
  //     await new Promise((resolve) => setTimeout(resolve, 15000));
  //
  //     const r = await api.post(
  //       `${config.base.orchestrationOperate}/v2/process-definitions/search`,
  //       {
  //         data: "{}",
  //         headers: {
  //           Authorization: authHeader(api, config),
  //           "Content-Type": "application/json",
  //         },
  //       },
  //     );
  //     expect(
  //       r.ok(),
  //       `Process visibility check failed for ${label}: ${r.status()}`,
  //     ).toBeTruthy();
  //     const data = await r.json();
  //     const ids = (data.items as Array<{ processDefinitionId: string }>).map(
  //       (i) => i.processDefinitionId,
  //     );
  //     expect(ids, `Process ${bpmnId} not found in Operate`).toContain(bpmnId);
  //   });
  // }

  test.afterAll(async ({}, testInfo) => {
    // If the test outcome is different from what was expected (i.e. the test failed),
    // dump the resolved configuration so that it is visible in the Playwright output.
    if (testInfo.status !== testInfo.expectedStatus) {
      // Secrets are dumped as-is because the surrounding CI already treats logs as sensitive.
      // If this becomes a concern, mask the values here before logging.
      console.error(
        "\n===== CONFIG DUMP (test failed) =====\n" +
          JSON.stringify(config, null, 2),
      );
    }
  });
});
