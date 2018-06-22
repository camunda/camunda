
const utils = require('./utils');
const chalk = require('chalk');

const runWithColor = utils.runWithColor;

console.log(chalk.red('\nStopping docker with Camunda Platform and Elasticsearch'));
const docker = runWithColor('docker-compose rm -sfv', 'docker', chalk.red);
docker.on('close', () => {
  console.log(chalk.red('\nDocker successfully stopped!'));
})

