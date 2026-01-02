// NOTE: If you get the message connection error: desc = "error reading server preface: http2: frame too large"
// this is likely due to --insecure on the zbctl call while the endpoint is TLS enabled.

/// <reference types="node" />

import { config as dotenv } from "dotenv";
dotenv(); // ← loads .env before anything else

import { test, expect, APIRequestContext } from "@playwright/test";
import { execFileSync } from "child_process";
import { authHeader, fetchToken, requireEnv } from "./helper";

// ---------- config & helpers ----------

// Grouped config for base URLs
const config = {
  authType: requireEnv("TEST_AUTH_TYPE"),
  authURL: requireEnv("AUTH_URL"),
  testBasePath: requireEnv("TEST_BASE_PATH"),
  base: {
    identity: requireEnv("IDENTITY_BASE_URL"),
  },
  loginPath: {
    Identity: process.env["IDENTITY_LOGIN_PATH"],
  },
  venomID: process.env.TEST_CLIENT_ID ?? "venom",
  venomSec: requireEnv("PLAYWRIGHT_VAR_ADMIN_CLIENT_SECRET"),
};

// ---------- tests ----------
test.describe("identity", () => {
  let api: APIRequestContext;
  let venomJWT: string;

  test.beforeAll(async ({ playwright }) => {
    api = await playwright.request.newContext();
    venomJWT = await fetchToken(config.venomID, config.venomSec, api, config);
  });

  // Parameterized API endpoint tests
  test("API: Identity users", async ({ request }) => {
    const url = `${config.base.identity}/api/users`;
    const method = "GET";
    const body = "";

    const r = await request.fetch(url, {
      method,
      data: body || undefined,
      headers: {
        Authorization: await authHeader(request, config),
        "Content-Type": "application/json",
      },
    });
    expect(
      r.ok(),
      `API call failed for Identity users: ${r.status()}`,
    ).toBeTruthy();
  });

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
