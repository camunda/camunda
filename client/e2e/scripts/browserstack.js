/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const createTestCafe = require('testcafe');
const browsers = ['browserstack:edge@85.0:Windows 10'];

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
process.env.BROWSERSTACK_CONSOLE = 'errors';
process.env.BROWSERSTACK_NETWORK_LOGS = true;

async function start() {
  const testCafe = await createTestCafe('localhost');
  let hasFailures = false;

  try {
    for (let i = 0; i < browsers.length; i++) {
      if (
        await testCafe
          .createRunner()
          .src('./tests/*.js')
          .browsers(browsers[i])
          .run()
      ) {
        hasFailures = true;
      }
    }
  } catch (ex) {
    console.error(ex);

    process.exit(1);
  } finally {
    await testCafe.close();

    process.exit(hasFailures ? 1 : 0);
  }
}

start();
