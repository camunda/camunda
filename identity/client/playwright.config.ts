import { defineConfig, devices } from "@playwright/test";

const IS_CI = Boolean(process.env.CI);

export default defineConfig({
  testDir: "./e2e",
  expect: {
    timeout: 10000,
  },
  fullyParallel: true,
  forbidOnly: IS_CI,
  retries: IS_CI ? 2 : 0,
  workers: IS_CI ? 1 : undefined,
  reporter: IS_CI ? [["github"], ["html"]] : "html",
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  outputDir: "test-results/",
  use: {
    actionTimeout: 0,
    baseURL: `http://localhost:8080/identity/`,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
});
