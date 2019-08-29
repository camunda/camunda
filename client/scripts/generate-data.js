/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const {spawn} = require('child_process');
const path = require('path');

// argument to determine if we are in CI mode
const ciMode = process.argv.indexOf('ci') > -1;

console.debug('executing generate-data script in [ci=' + ciMode + ']');

// ~~~ Generator Configuration ~~~
// adjust for number of process instances to generate
const numberOfProcessInstances = 5000;

const generateDataProcess = spawn(
  'mvn',
  [
    'exec:java',
    '-f ./qa/data-generation/pom.xml',
    `-Dexec.args="--numberOfProcessInstances ${numberOfProcessInstances} --removeDeployments false"`,
    ciMode ? '-s settings.xml' : undefined
  ],
  {
    cwd: path.resolve(__dirname, '..', '..'),
    stdio: 'inherit',
    shell: true
  }
);

process.on('SIGINT', () => generateDataProcess.kill('SIGINT'));
process.on('SIGTERM', () => generateDataProcess.kill('SIGTERM'));

generateDataProcess.on('error', () => process.exit(1));
generateDataProcess.on('close', code => process.exit(code));
