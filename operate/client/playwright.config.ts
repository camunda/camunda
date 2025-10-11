/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {devices, defineConfig} from '@playwright/test';

const IS_CI = Boolean(process.env.CI);
const IS_E2E = Boolean(process.env.IS_E2E);

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
    actionTimeout: 0,
    baseURL: `http://localhost:${IS_CI && IS_E2E ? 8080 : 8081}/operate`,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
});

export default config;
