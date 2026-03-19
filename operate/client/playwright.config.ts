/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  devices,
  defineConfig,
  type PlaywrightTestConfig,
} from '@playwright/test';
import playwrightPkg from '@playwright/test/package.json' with {type: 'json'};

const IS_CI = Boolean(process.env.CI);
const IS_E2E = Boolean(process.env.IS_E2E);
const BASE_URL = `http://localhost:${IS_E2E ? 8080 : 3003}/operate`;
const USE_CONTAINERIZED_BROWSER =
  !IS_CI && Boolean(process.env.CONTAINERIZED_BROWSER);

const webServer: PlaywrightTestConfig['webServer'] = [];

if (!IS_E2E) {
  webServer.push({
    name: 'SPA Server',
    command: 'npx vite preview --port 3003',
    stdout: 'pipe',
    stderr: 'pipe',
    url: BASE_URL,
  });
}

if (USE_CONTAINERIZED_BROWSER) {
  const version = playwrightPkg.version;
  webServer.push({
    name: 'Playwright Server',
    command: `docker run --rm --init --network host mcr.microsoft.com/playwright:v${version} /bin/sh -c "npx -y playwright@${version} run-server --port 7777 --host 0.0.0.0"`,
    stdout: 'pipe',
    stderr: 'pipe',
    port: 7777,
    timeout: 180_000, // 3min timeout in case the image has to be pulled first.
    gracefulShutdown: {signal: 'SIGTERM', timeout: 5000},
  });
}

/**
 * See https://playwright.dev/docs/test-configuration.
 */
const config = defineConfig({
  testDir: './e2e-playwright',
  timeout: 30 * 1000,
  expect: {
    timeout: 15 * 1000,
  },
  fullyParallel: !IS_E2E,
  forbidOnly: IS_CI,
  retries: IS_CI ? 2 : 0,
  workers: IS_CI || IS_E2E ? 1 : undefined,
  webServer,
  reporter: IS_CI
    ? [
        ['github'],
        ['html'],
        [
          'junit',
          {
            outputFile: 'results.xml',
          },
        ],
      ]
    : 'html',
  snapshotPathTemplate:
    '{testDir}/{testFileDir}/{testFileName}-snapshots/{arg}-{projectName}-linux{ext}',
  projects: [
    {
      name: 'setup',
      testMatch: /e2e.setup\.ts/,
    },
    {
      name: 'visual-light',
      testMatch: 'visual/**/*.spec.ts',
      use: {...devices['Desktop Chrome'], colorScheme: 'light'},
    },
    {
      name: 'visual-dark',
      testMatch: 'visual/**/*.spec.ts',
      use: {...devices['Desktop Chrome'], colorScheme: 'dark'},
    },
    {
      name: 'a11y',
      testMatch: 'a11y/**/*.spec.ts',
      use: {...devices['Desktop Chrome']},
    },
    {
      name: 'screenshot-generator',
      testMatch: 'docs-screenshots/**/*.spec.ts',
      use: {...devices['Desktop Firefox']},
    },
    {
      name: 'e2e',
      testMatch: 'tests/**/*.spec.ts',
      use: {...devices['Desktop Chrome']},
      dependencies: ['setup'],
    },
  ],
  outputDir: 'test-results/',
  use: {
    baseURL: BASE_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    ...(USE_CONTAINERIZED_BROWSER && {
      connectOptions: {
        wsEndpoint: `ws://127.0.0.1:7777/`,
      },
    }),
  },
});

export default config;
