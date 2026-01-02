/// <reference types="node" />
import { config as dotenv } from "dotenv";
dotenv(); // ← loads .env before anything else

import { test, expect, APIRequestContext } from "@playwright/test";
import { execFileSync } from "child_process";
import { authHeader, fetchToken, requireEnv } from "./helper";

// ---------- config & helpers ----------

// Helper to require environment variables

// Grouped config for base URLs
const config = {
  authURL: requireEnv("AUTH_URL"),
  authType: requireEnv("TEST_AUTH_TYPE"),
  testBasePath: requireEnv("TEST_BASE_PATH"),
  base: {
    connectors: requireEnv("CONNECTORS_BASE_URL"),
  },
  loginPath: {
    connectors: requireEnv("CONNECTORS_LOGIN_PATH"),
  },
  secrets: {
    connectors: requireEnv("PLAYWRIGHT_VAR_CONNECTORS_CLIENT_SECRET"),
  },
  venomID: process.env.TEST_CLIENT_ID ?? "venom",
  venomSec: requireEnv("PLAYWRIGHT_VAR_ADMIN_CLIENT_SECRET"),
  demoUser: "demo",
  demoPass: "demo",
};

// ---------- tests ----------
test.describe("connectors", () => {
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

  test("Connectors inbound page", async () => {
    expect(
      (await api.get(config.base.connectors, { timeout: 45_000 })).ok(),
      "Connectors inbound page failed",
    ).toBeTruthy();
  });

  // this needs to be ran after the orchestration test. This test needs a model to be deployed before running or it results in a 404.
  // test(`TEST - Check Connectors webhook`, async () => {
  //   const r = await api.post(config.base.connectors + "/test-mywebhook", {
  //     data: { webhookDataKey: "webhookDataValue" },
  //     headers: {
  //       Authorization: await authHeader(api, config),
  //       //Authorization: `Basic ZGVtbzpkZW1v`,
  //       "Content-Type": "application/json",
  //     },
  //   });
  //   expect(r.ok(), `API call failed with ${r.status()}`).toBeTruthy();
  // });

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
