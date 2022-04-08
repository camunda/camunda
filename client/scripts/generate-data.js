/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const {spawn} = require('child_process');
const fs = require('fs');
const path = require('path');

console.debug('executing generate-data script in [ci=true]');

// ~~~ Generator Configuration ~~~
const e2ePresetsFile = path.resolve(__dirname, '..', 'e2e_presets.json');

const e2ePresets = JSON.parse(fs.readFileSync(e2ePresetsFile));
const dataGenerationParameters = Object.keys(e2ePresets)
  .map((key) => `--${key} ${e2ePresets[key]}`)
  // join command arguments separated by whitespace
  .join(' ');

const generateDataProcess = spawn(
  'mvn',
  ['exec:java', '-f ./qa/data-generation/pom.xml', `-Dexec.args="${dataGenerationParameters}"`],
  {
    cwd: path.resolve(__dirname, '..', '..'),
    stdio: 'inherit',
    shell: true,
  }
);

process.on('SIGINT', () => generateDataProcess.kill('SIGINT'));
process.on('SIGTERM', () => generateDataProcess.kill('SIGTERM'));

generateDataProcess.on('error', () => {
  process.exit(1);
});
generateDataProcess.on('close', (code) => {
  if (code == 0) {
    console.debug('generate-data script execution finished successfully');
  } else {
    console.error('generate-data script execution failed');
  }
  process.exit(code);
});
