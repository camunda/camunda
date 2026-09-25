/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {defineConfig, devices} from '@playwright/test';
import {config as loadDotenv} from 'dotenv';

import {env} from './e2e/env';

const isCI = !!process.env.CI;
if (!isCI) {
  loadDotenv({quiet: true});
}

export default defineConfig({
  testDir: './e2e',
  outputDir: './e2e/test-results',
  fullyParallel: true,
  forbidOnly: isCI,
  retries: isCI ? 2 : 0,
  workers: 2,
  timeout: 60_000,
  expect: {timeout: 10_000},
  reporter: isCI
    ? [['list'], ['github'], ['html', {open: 'never', outputFolder: 'e2e/playwright-report'}]]
    : [['list'], ['html', {open: 'never', outputFolder: 'e2e/playwright-report'}]],
  use: {
    ...devices['Desktop Chrome'],
    baseURL: env.optimizeUrl,
    viewport: {width: 1920, height: 1080},
    locale: 'en-US',
    timezoneId: 'Europe/Berlin',
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: {
    command: 'yarn start',
    url: env.optimizeUrl,
    reuseExistingServer: true,
    timeout: 120_000,
    env: {BROWSER: 'none'},
  },
  projects: [
    {
      name: 'setup',
      testMatch: 'setup/*.setup.ts',
    },
    {
      name: 'e2e',
      testMatch: 'tests/**/*.spec.ts',
      dependencies: ['setup'],
    },
    {
      name: 'visual',
      testMatch: 'visual/**/*.spec.ts',
      dependencies: ['setup'],
      // Baselines are rendered on the Linux CI runner; other platforms only exercise the flow.
      ignoreSnapshots: !isCI && !process.env.E2E_VISUAL,
      snapshotPathTemplate: '{testDir}/{testFileDir}/__screenshots__/{testFileName}/{arg}{ext}',
    },
    {
      name: 'cloud',
      testMatch: 'cloud/**/*.spec.ts',
      workers: 1,
    },
  ],
});
