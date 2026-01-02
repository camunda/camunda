import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  projects: [
    {
      name: "full-suite",
      testMatch: ["**/*.spec.{ts,js}"],
    },
  ],
  fullyParallel: true,
  retries: 3,
  workers: process.env.CI == "true" ? 1 : "25%",
  reporter: [
    ["html", { open: "never" }],
    ["list"],
    ["junit", { outputFile: "test-results/results.xml" }],
  ],
  use: { baseURL: "https://camunda.local", trace: "on-first-retry" },
});
