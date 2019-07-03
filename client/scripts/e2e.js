/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const fetch = require('node-fetch');
const createTestCafe = require('testcafe');
const chalk = require('chalk');

const {spawn} = require('child_process');
const net = require('net');
const kill = require('tree-kill');

process.env.BROWSERSTACK_USERNAME = 'optimize@camunda.com';
process.env.BROWSERSTACK_ACCESS_KEY = 'QDQfPYkTYy8SQBYYt1zB';
process.env.BROWSERSTACK_USE_AUTOMATE = '1';

const browsers = [
  'browserstack:IE@11.0:Windows 8.1',
  'browserstack:Edge',
  'browserstack:Firefox',
  'browserstack:Chrome'
];

const backendProcess = spawn('yarn', ['run', 'start-backend']);
const frontendProcess = spawn('yarn', ['start']);

let dataInterval;
const connectionInterval = setInterval(async () => {
  const backendDone = await checkPort(8090);
  const frontendDone = await checkPort(3000);

  console.log(
    `waiting for servers to be started: ${(backendDone ? chalk.green : chalk.red)(
      'backend'
    )} ${(frontendDone ? chalk.green : chalk.red)('frontend')}`
  );

  if (backendDone && frontendDone) {
    console.log(chalk.green.bold('Servers Started!'));
    clearInterval(connectionInterval);
    dataInterval = setInterval(waitForData, 1000);
  }
}, 5000);

function checkPort(number) {
  return new Promise(resolve => {
    const socket = new net.Socket();

    const destroy = () => {
      socket.destroy();
      resolve(false);
    };

    socket.setTimeout(1000);
    socket.once('error', destroy);
    socket.once('timeout', destroy);

    socket.connect(number, 'localhost', () => {
      socket.end();
      resolve(true);
    });
  });
}

async function waitForData() {
  const resp = await fetch('http://localhost:8090/api/status');
  const status = await resp.json();

  const {
    connectionStatus: {engineConnections, connectedToElasticsearch},
    isImporting
  } = status;

  if (connectedToElasticsearch && engineConnections['camunda-bpm'] && !isImporting['camunda-bpm']) {
    console.log(chalk.green.bold('Data Available! Starting tests.'));
    clearInterval(dataInterval);
    startTest();
  } else {
    console.log('Waiting for data');
  }
}

async function startTest() {
  const testCafe = await createTestCafe('localhost');
  try {
    for (let i = 0; i < browsers.length; i++) {
      await testCafe
        .createRunner()
        .src('e2e/tests/*.js')
        .browsers(browsers[i])
        .run({skipJsErrors: true, assertionTimeout: 10000, pageLoadTimeout: 10000});
    }
  } finally {
    testCafe.close();
    kill(backendProcess.pid);
    kill(frontendProcess.pid);
  }
}
