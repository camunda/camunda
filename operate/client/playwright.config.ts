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

const BASE_URL = 'http://localhost:3003/operate/';
const IS_CI = Boolean(process.env.CI);
const USE_CONTAINERIZED_BROWSER =
  !IS_CI && Boolean(process.env.CONTAINERIZED_BROWSER);

const webServer: PlaywrightTestConfig['webServer'] = [
  {
    name: 'SPA Server',
    command: 'npx vite preview',
    stdout: 'pipe',
    stderr: 'pipe',
    url: BASE_URL,
  },
];

if (USE_CONTAINERIZED_BROWSER) {
  const version = playwrightPkg.version;
  webServer.push({
    name: 'Playwright Server',
    command: `docker run --rm --init --network host mcr.microsoft.com/playwright:v${version} /bin/sh -c "npx -y playwright@${version} run-server --host 0.0.0.0"`,
    stdout: 'pipe',
    stderr: 'pipe',
    wait: {
      stdout: /Listening on ws:\/\/0\.0\.0\.0:(?<PLAYWRIGHT_SERVER_PORT>\d+)\//,
    },
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
  fullyParallel: true,
  forbidOnly: IS_CI,
  retries: IS_CI ? 2 : 0,
  workers: IS_CI ? 1 : undefined,
  webServer,
  reporter: IS_CI
    ? [
        ['blob'],
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
  ],
  outputDir: 'test-results/',
  use: {
    baseURL: BASE_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    ...(USE_CONTAINERIZED_BROWSER && {
      connectOptions: {
        wsEndpoint: `ws://127.0.0.1:${process.env.PLAYWRIGHT_SERVER_PORT}/`,
      },
    }),
  },
});

export default config;
