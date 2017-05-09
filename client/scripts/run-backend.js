const path = require('path');
const chalk = require('chalk');
const shell = require('shelljs');
const utils = require('./utils');
const engine = require('./engine');

const isWindows = utils.isWindows;
const runWithColor = utils.runWithColor;

const engineInitPromise = engine.init().catch(error => {
  console.error(error);
  shell.exit(1);
});

const mvnCwd = path.resolve(__dirname, '..', '..');
const mvnCleanPackage = runWithColor('mvn clean package -Pproduction -DskipTests', 'maven', chalk.green, {
  cwd: mvnCwd
});

mvnCleanPackage.on('close', code => {
  const distro = path.resolve(mvnCwd, 'distro', 'target');
  const extractDir = path.resolve(distro, 'distro');
  const archive = utils.findFile(distro, '.tar.gz');

  console.log('Start extracting', archive);

  utils.extract(archive, extractDir)
    .then(startBackend)
    .catch(error => {
      console.error(chalk.red('Start up failed', error));
      shell.exit(1);
    });

  function startBackend() {
    engineInitPromise
      .then(() => {
        console.log(chalk.green('ENGINE STARTED!!!'));

        console.log('Starting elastic search...');

        const elasticSearch = utils.findPath(extractDir, [
          'server',
          /^elastic/,
          'bin',
          isWindows ? 'elasticsearch.bat' : 'elasticsearch'
        ]);

        runWithColor(elasticSearch, 'Elastic Search', chalk.blue);

        return utils.waitForServer('http://localhost:9200')
          .then(() => {
            const backendJar = utils.findFile(extractDir, '.jar');

            runWithColor('java -jar ' + backendJar, 'BE', chalk.green);

            return utils.waitForServer('http://localhost:8090');
          });
      });
  }
});
