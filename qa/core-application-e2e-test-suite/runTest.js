/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {select} from '@inquirer/prompts';
import {spawnSync} from 'node:child_process';

const IS_LOCAL = process.env.LOCAL_TEST === 'true';

const browser = IS_LOCAL
  ? await select({
      message: 'Select the browser to run the tests on:',
      choices: [
        {name: 'Chrome', value: 'chromium'},
        {name: 'Firefox', value: 'firefox'},
        {name: 'Edge', value: 'msedge'},
      ],
    })
  : 'chromium';

const baseURL = getBaseURL();

const runMode = IS_LOCAL
  ? await select({
      message: 'Which mode would you like to run Playwright in:',
      choices: [
        {name: 'Headless', value: 'headless'},
        {name: 'Headed', value: 'headed'},
      ],
    })
  : 'headless';

function getCommonArgs() {
  const args = ['--project=' + browser];
  if (runMode === 'headed') {
    args.push('--headed');
  }
  return args;
}

function runPlaywrightTest(extraArgs, label) {
  console.log(`\nRunning ${label} tests...`);
  const result = spawnSync(
    'npx',
    ['playwright', 'test', ...getCommonArgs(), ...extraArgs],
    {
      cwd: process.cwd(),
      env: {
        ...process.env,
        PLAYWRIGHT_BASE_URL: baseURL,
      },
      stdio: 'inherit',
    },
  );
  return result.status ?? 1; // Treat undefined as failure
}

// Run tests excluding @subset
const nonSubsetExitCode = runPlaywrightTest(
  ['--grep-invert', '@subset'],
  'non-@subset',
);

// Run tests including @subset
const subsetExitCode = runPlaywrightTest(['--grep', '@subset'], '@subset');

// Exit with failure if either failed
if (nonSubsetExitCode !== 0 || subsetExitCode !== 0) {
  process.exit(1);
}

function getBaseURL() {
  return process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:8080';
}
