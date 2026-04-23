/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {defineConfig, type ReporterDescription} from '@playwright/test';

const CI = !!process.env['CI'];
const REPORTS_BASE_DIR = './reports';

const localReporters: ReporterDescription[] = [
  ['list'],
  ['html', {outputFolder: `${REPORTS_BASE_DIR}/html`}],
];

const ciReporters: ReporterDescription[] = [
  ['dot'],
  ['github'],
  ['html', {outputFolder: `${REPORTS_BASE_DIR}/html`}],
  ['junit', {outputFile: `${REPORTS_BASE_DIR}/results.xml`}],
];

export default defineConfig({
  name: 'smoke',
  testDir: './test/smoke',
  fullyParallel: true,
  failOnFlakyTests: CI,
  forbidOnly: CI,
  retries: 1,
  outputDir: `${REPORTS_BASE_DIR}/artifacts`,
  reporter: CI ? ciReporters : localReporters,
  use: {
    trace: 'retain-on-failure',
  },
});
