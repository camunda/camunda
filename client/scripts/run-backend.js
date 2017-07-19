/* eslint no-console: 0 */

const path = require('path');
const chalk = require('chalk');
const shell = require('shelljs');
const utils = require('./utils');
const engine = require('./engine');
const {c7port} = require('./config');

const isWindows = utils.isWindows;
const runWithColor = utils.runWithColor;

const engineInitPromise = engine.init().catch(error => {
  console.error(error);
  shell.exit(0);
});

const mvnCwd = path.resolve(__dirname, '..', '..');
const mvnCleanPackage = runWithColor('mvn -Pproduction -DskipTests clean package', 'maven', chalk.green, {
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
      shell.exit(0);
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
            console.log('Elastic search has been started!');
            console.log('Starting backend...');
            const backendJar = utils.findFile(extractDir, '.jar');
            const config = utils.findPath(extractDir, [
              'environment',
              'environment.properties'
            ]);
            const environmentDir = path.resolve(extractDir, 'environment');
            const classpathSeparator = isWindows ? ';' : ':' ;
            const classpath = environmentDir + classpathSeparator + backendJar;

            utils.changeFile(config, {
              regexp: /http:\/\/localhost:8080\/engine-rest/g,
              replacement: `http://localhost:${c7port}/engine-rest`
            });

            runWithColor('java -cp ' + classpath + ' org.camunda.optimize.Main', 'BE', chalk.green);

            return utils.waitForServer('http://localhost:8090');
          });
      });
  }
});
