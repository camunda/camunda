/* eslint no-console: 0 */

const path = require('path');
const chalk = require('chalk');
const shell = require('shelljs');
const utils = require('./utils');
const engine = require('./engine');
const {c7ports} = require('./config');

const isWindows = utils.isWindows;
const runWithColor = utils.runWithColor;

let offlineMode = false;
process.argv.forEach(function (val, index, array) {
  if (val === '--o') {
    offlineMode = true;
  }
});

const mvnCwd = path.resolve(__dirname, '..', '..');

if (!process.env.FAST_BUILD) {
  const onlineModeMavenCmd = 'mvn clean package -DskipTests -Pit,skip.fe.build,package-engine';

  const offlineModeMavenCmd = 'mvn clean package -o -DskipTests -Pit,skip.fe.build,package-engine';
  const mvnCleanPackageCmd = offlineMode ? offlineModeMavenCmd : onlineModeMavenCmd;

  const mvnCleanPackage = runWithColor(mvnCleanPackageCmd, 'maven', chalk.green, {
    cwd: mvnCwd
  });

  mvnCleanPackage.on('close', startBackend);
} else {
  startBackend(0);
}

function startBackend(code) {
  const distro = path.resolve(mvnCwd, 'distro', 'target');
  const extractDir = path.resolve(distro, 'distro');
  const archive = utils.findFile(distro, '.tar.gz');

  const engineInitPromise = engine.init().catch(error => {
    console.error(chalk.red(JSON.stringify(error, null, 2)));
    shell.exit(0);
  });

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

        const elasticSearchJvmOptions = utils.findPath(extractDir, [
          'server',
          /^elastic/,
          'config',
          'jvm.options'
        ]);

        utils.changeFile(elasticSearchJvmOptions, [
          {
            regexp: /-Xms2g/g,
            replacement: '-Xms256m'
          },
          {
            regexp: /-Xmx2g/g,
            replacement: '-Xmx256m'
          }
        ]);

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
              'environment-config.yaml'
            ]);
            const environmentDir = path.resolve(extractDir, 'environment');
            const classpathSeparator = isWindows ? ';' : ':' ;
            const classpath = environmentDir + classpathSeparator + backendJar;

            utils.changeYAMLFile(config, content => {
              return Object.assign(
                content,
                {
                  engines: c7ports.reduce((engines, port) => {
                    return Object.assign(
                      engines,
                      {
                        [`engine_${port}`]: {
                          name: 'default',
                          rest: `http://localhost:${port}/engine-rest`,
                          authentication: {
                            accessGroup: '',
                            enabled: false,
                            password: '',
                            user: ''
                          },
                          enabled: true
                        }
                      }
                    );
                  }, {})
                }
              );
            });

            runWithColor('java -cp ' + classpath + ' org.camunda.optimize.Main', 'BE', chalk.green);

            return utils.waitForServer('http://localhost:8090');
          });
      });
  }
}
