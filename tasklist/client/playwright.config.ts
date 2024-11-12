/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {devices, PlaywrightTestConfig} from '@playwright/test';

const IS_CI = Boolean(process.env.CI);
const IS_E2E = Boolean(process.env.IS_E2E);

/**
 * See https://playwright.dev/docs/test-configuration.
 * when using the base path '/tasklist' to the baseURL, we need to add a slash '/' at the end as expected by playwright https://playwright.dev/docs/api/class-browser#browser-new-context-option-base-url
 *
 */
const config: PlaywrightTestConfig = {
  testDir: './e2e',
  expect: {
    timeout: 10000,
    toHaveScreenshot: {threshold: 0.1},
  },
  fullyParallel: !IS_E2E,
  forbidOnly: IS_CI,
  retries: IS_CI ? 2 : 0,
  workers: IS_CI || IS_E2E ? 1 : undefined,
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
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
      },
    },
  ],
  outputDir: 'test-results/',
  use: {
    actionTimeout: 0,
    baseURL: `http://localhost:${IS_CI && IS_E2E ? 8080 : 8081}/tasklist/`,
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
  },
};

export default config;
