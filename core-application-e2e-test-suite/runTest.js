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

const browser = await select({
  message: 'Select the browser to run the tests on:',

  choices: [
    {name: 'Chrome', value: 'chromium'},

    {name: 'Firefox', value: 'firefox'},

    {name: 'Edge', value: 'msedge'},
  ],
});

const baseURL = getBaseURL();

const runMode = await select({
  message: 'Which mode would like to run Playwright:',

  choices: [
    {name: 'Headless', value: 'headless'},

    {name: 'Headed', value: 'headed'},
  ],
});

let args = ['run', 'test', '--', `--project=${browser}`];

if (runMode === 'headed') {
  args.push('--headed');
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
  return (
    process.env.PLAYWRIGHT_BASE_URL ||
    (IS_LOCAL
      ? 'http://localhost:8080'
      : 'https://integration-stage.example.com')
  );
}
