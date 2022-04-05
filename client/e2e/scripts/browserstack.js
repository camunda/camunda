/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const createTestCafe = require('testcafe');
const fetch = require('node-fetch');

const OPERATE_CHECK_INTERVAL = 5000;
const browser =
  process.env.OPERATE_BROWSERSTACK_BROWSER || 'browserstack:Chrome';

if (!process.env.BROWSERSTACK_USERNAME) {
  console.log(`
    You are trying to run the e2e tests without valid BrowserStack credentials.
    Credentials are only provided on CI by default. If you want to run the tests locally,
    you need to provide the username and access key. Find them here:

    https://automate.browserstack.com/dashboard/v2/

    Then run the test command like so:
    BROWSERSTACK_USERNAME={username} BROWSERSTACK_ACCESS_KEY={access_key} yarn test:browserstack
  `);
  process.exit(1);
}

process.env.BROWSERSTACK_PROJECT_NAME = 'Operate';
process.env.BROWSERSTACK_BUILD_ID = 'Operate E2E Tests';
process.env.BROWSERSTACK_USE_AUTOMATE = '1';
process.env.BROWSERSTACK_DISPLAY_RESOLUTION = '1920x1080';
process.env.BROWSERSTACK_CONSOLE = 'debug';
process.env.BROWSERSTACK_NETWORK_LOGS = true;
process.env.BROWSERSTACK_DEBUG = true;

const waitForOperate = new Promise((resolve, reject) => {
  const intervalId = setInterval(async () => {
    try {
      const response = await fetch(
        `http://localhost:${process.env.PORT}/actuator/health/readiness`
      );
      if (response.ok) {
        clearInterval(intervalId);
        resolve();
      } else {
        throw new Error();
      }
    } catch {
      console.log(
        `Operate is not ready yet, retrying in ${OPERATE_CHECK_INTERVAL}ms...`
      );
    }
  }, OPERATE_CHECK_INTERVAL);
});

async function start() {
  const testCafe = await createTestCafe('localhost');
  let hasFailedTests = false;

  await waitForOperate.then(() => {
    console.log('Operate is ready');
  });

  try {
    const runner = await testCafe.createRunner();
    runner.compilerOptions({
      typescript: {
        configPath: './e2e/tsconfig.json',
      },
    });
    const failedTestsCount = await runner
      .src('./e2e/tests/*.ts')
      .browsers(browser)
      .run();

    if (failedTestsCount > 0) {
      hasFailedTests = true;
    }
  } catch (ex) {
    console.error(ex);

    process.exit(1);
  } finally {
    await testCafe.close();

    process.exit(hasFailedTests ? 1 : 0);
  }
}

start();
