/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {select} from '@inquirer/prompts';
import {spawn} from 'node:child_process';

const IS_LOCAL = Boolean(process.env.LOCAL_TEST);

const category = await select({
  message: 'Which tests would you like to run?',
  choices: [
    {name: 'E2E tests', value: 'e2e'},
    {name: 'API tests', value: 'api'},
  ],
});

const baseURL = getBaseURL();

let args = ['run', 'test', '--'];

if (category === 'api') {
  args.push('--project=api-tests');
} else {
  const browser = await select({
    message: 'Select the browser to run the E2E tests on:',
    choices: [
      {name: 'Chrome', value: 'chromium'},
      {name: 'Firefox', value: 'firefox'},
      {name: 'Edge', value: 'msedge'},
    ],
  });
  const runMode = await select({
    message: 'Which mode would like to run Playwright:',
    choices: [
      {name: 'Headless', value: 'headless'},
      {name: 'Headed', value: 'headed'},
    ],
  });

  if (runMode === 'headed') {
    args.push('--headed');
  }

  if (browser === 'chromium') {
    args.push('--project=chromium');
  } else if (browser === 'firefox') {
    args.push('--project=firefox');
  } else if (browser === 'msedge') {
    args.push('--project=msedge');
  } else if (browser === 'api-tests') {
    args.push('--project=api-tests');
  }
}

spawn('npm', args, {
  cwd: process.cwd(),
  env: {
    ...process.env,
    PLAYWRIGHT_BASE_URL: baseURL,
  },
  stdio: 'inherit',
});

function getBaseURL() {
  return process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:8080';
}
