/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {devices, PlaywrightTestConfig} from '@playwright/test';

const IS_CI = Boolean(process.env.CI);
const IS_E2E = Boolean(process.env.IS_E2E);
const IS_A11Y = Boolean(process.env.IS_A11Y);

/**
 * See https://playwright.dev/docs/test-configuration.
 */
const config: PlaywrightTestConfig = {
  testDir: './e2e-playwright',
  timeout: 30 * 1000,
  expect: {
    timeout: 15 * 1000,
  },
  forbidOnly: IS_CI,
  retries: 0,
  workers: IS_CI ? 1 : undefined,
  reporter: 'html',
  projects: [
    {
      name: 'setup',
      testMatch: IS_E2E ? /e2e.setup\.ts/ : /visual.setup\.ts/,
    },
    {
      name: 'chromium',
      use: {...devices['Desktop Chrome']},
      dependencies: ['setup'],
    },
  ],
  outputDir: 'test-results/',
  use: {
    actionTimeout: 0,
    baseURL: `http://localhost:${
      IS_A11Y ? 3000 : IS_E2E ? (IS_CI ? 8080 : 3001) : 8081
    }`,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
};

export default config;
