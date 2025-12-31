import { defineConfig } from "@playwright/test";
import * as dotenv from "dotenv";

dotenv.config();

export default defineConfig({
  testDir: "./node_modules/@camunda/e2e-test-suite/dist/tests/SM-8.6",
  projects: [
    {
      name: "smoke-tests",
      testMatch: ["**/smoke-tests.spec.{ts,js}"],
    },
    {
      name: "full-suite",
      testMatch: ["**/*.spec.{ts,js}"],
    },
  ],
  fullyParallel: true,
  retries: 3,
  timeout: 5 * 60 * 1000, // no test should take more than 3 minutes (failing fast is important so that we can run our tests on each PR)
  workers: "100%",
  //workers: process.env.CI == "true" ? 1 : "50%",
  use: {
    baseURL: getBaseURL(),
    actionTimeout: 10000,
    // Also applies to flaky tests
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    trace: "on-first-retry",
  },
});

function getBaseURL(): string {
  if (process.env.IS_PROD === "true") {
    return "https://console.camunda.io";
  }

  if (typeof process.env.PLAYWRIGHT_BASE_URL === "string") {
    return process.env.PLAYWRIGHT_BASE_URL;
  }

  if (process.env.MINOR_VERSION?.includes("SM")) {
    return "https://gke-" + process.env.BASE_URL + ".ci.distro.ultrawombat.com";
  }

  if (process.env.MINOR_VERSION?.includes("Run")) {
    return "http://localhost:8080";
  }

  return "https://console.cloud.ultrawombat.com";
}
