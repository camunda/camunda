/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import fetch from 'node-fetch';
import createTestCafe from 'testcafe';
import chalk from 'chalk';

import {spawn} from 'child_process';
import kill from 'tree-kill';
import {createWriteStream, chmodSync} from 'fs';
import {pipeline} from 'stream';
import {resolve as _resolve, dirname} from 'path';
import {fileURLToPath} from 'url';

// argument to determine if we are in CI mode
const ciMode = process.argv.indexOf('ci') > -1;

// argument to determine if we want to use headlessChrome instead of default Browserstack
const chromeheadlessMode = process.argv.indexOf('chromeheadless') > -1;

console.debug(
  'executing e2e script in [ci=' + ciMode + ', chromeheadlessMode=' + chromeheadlessMode + ']'
);

function spawnWithArgs(commandString, options) {
  const args = commandString.split(' ');
  const command = args.splice(0, 1)[0];
  return spawn(command, args, options);
}

if (!ciMode) {
  // credentials for local testing, in CI we get credentials from jenkins
  process.env.BROWSERSTACK_USERNAME = 'optimize@camunda.com';
  process.env.BROWSERSTACK_ACCESS_KEY = 'QDQfPYkTYy8SQBYYt1zB';
}
process.env.BROWSERSTACK_USE_AUTOMATE = '1';
process.env.BROWSERSTACK_DISPLAY_RESOLUTION = '1920x1080';
process.env.BROWSERSTACK_PARALLEL_RUNS = '3';
process.env.BROWSERSTACK_PROJECT_NAME = 'Optimize E2E Tests';
process.env.BROWSERSTACK_LOCAL_IDENTIFIER = 'TestCafe';

// download browserstack local binary
if (ciMode && !chromeheadlessMode) {
  console.log('downloading browserstack local binary');

  fetch('https://bstack-local-prod.s3.amazonaws.com/BrowserStackLocal-linux-x64').then(
    (response) => {
      if (!response.ok) {
        throw new Error(`unexpected response ${response.statusText}`);
      }

      pipeline(response.body, createWriteStream('BrowserStackLocal'), (err) => {
        if (err) {
          console.error('Pipeline failed.', err);
        } else {
          console.log('BrowserStackLocal file downloaded. Starting daemon.');
          chmodSync('BrowserStackLocal', 0o755);
          spawnWithArgs(
            `./BrowserStackLocal --key ${process.env.BROWSERSTACK_ACCESS_KEY} --local-identifier TestCafe --daemon start --parallel-runs 3`,
            {
              cwd: _resolve(getDirName(), '..'),
            }
          );
        }
      });
    }
  );
}

const browsers = chromeheadlessMode
  ? ['chrome:headless']
  : ['browserstack:Edge', 'browserstack:Firefox', 'browserstack:Chrome'];

const backendProcess = spawn('yarn', ['run', 'start-backend', ciMode ? 'ci' : undefined]);
const frontendProcess = spawn('yarn', ['start']);

if (ciMode) {
  backendProcess.stderr.on('data', (data) => console.error(data.toString()));

  const logStream = createWriteStream('./build/backendLogs.log', {flags: 'a'});
  backendProcess.stdout.pipe(logStream);
  backendProcess.stderr.pipe(logStream);
}

let dataInterval;
const connectionInterval = setInterval(async () => {
  const backendDone = await checkHttpPort(8090);
  const frontendDone = await checkHttpPort(3000);

  console.log(
    `waiting for servers to be started: backend = ${
      backendDone ? 'started' : 'not started'
    } , frontend = ${frontendDone ? 'started' : 'not started'}`
  );

  if (backendDone && frontendDone) {
    console.log(chalk.green.bold('Servers Started!'));
    clearInterval(connectionInterval);
    dataInterval = setInterval(waitForData, 1000);
  }
}, 5000);

function checkHttpPort(number) {
  return new Promise(async (resolve) => {
    try {
      await fetch('http://localhost:' + number, {timeout: 1000});
      resolve(true);
    } catch (e) {
      resolve(false);
    }
  });
}

async function waitForData() {
  const generatorResponse = await fetch('http://localhost:8100/api/dataGenerationComplete');
  const status = await generatorResponse.text();

  if (status === 'false') {
    console.log('Still generating data');
  } else {
    const resp = await fetch('http://localhost:8090/api/status');
    const status = await resp.json();

    const {engineStatus, connectedToElasticsearch} = status;

    if (
      connectedToElasticsearch &&
      engineStatus['camunda-bpm'] &&
      engineStatus['camunda-bpm'].isConnected &&
      !engineStatus['camunda-bpm'].isImporting
    ) {
      console.log(chalk.green.bold('Data Available! Starting tests.'));
      clearInterval(dataInterval);
      startTest();
    } else {
      console.log('Waiting for data import');
      console.log(JSON.stringify(status));
    }
  }
}

async function startTest() {
  const testCafe = await createTestCafe('localhost');
  let hasFailures = false;
  try {
    for (let i = 0; i < browsers.length; i++) {
      if (
        await testCafe
          .createRunner()
          .src('e2e/tests/*.js')
          .browsers(browsers[i])
          .run({
            skipJsErrors: true,
            disableScreenshots: true,
            concurrency: 3,
            assertionTimeout: 40000,
            pageLoadTimeout: 40000,
            quarantineMode: chromeheadlessMode ? undefined : {successThreshold: 1, attemptLimit: 3},
          })
      ) {
        hasFailures = true;
      }
    }
  } catch (err) {
    hasFailures = true;
    console.error(err);
  } finally {
    await testCafe.close();
    if (ciMode && !chromeheadlessMode) {
      spawnWithArgs(
        `BrowserStackLocal --key ${process.env.BROWSERSTACK_ACCESS_KEY} --local-identifier TestCafe --daemon stop`,
        {
          cwd: _resolve(getDirName(), '..'),
        }
      );
    }
    kill(frontendProcess.pid, () => {
      kill(backendProcess.pid, () => {
        process.exit(hasFailures ? 3 : 0);
      });
    });
  }
}

function getDirName() {
  const __filename = fileURLToPath(import.meta.url);
  return dirname(__filename);
}
