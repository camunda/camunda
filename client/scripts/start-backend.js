/* eslint no-console: 0 */

const path = require('path');
const chalk = require('chalk');
const shell = require('shelljs');
const utils = require('./utils');
const engine = require('./engine');
const {c7ports} = require('./config');

const isWindows = utils.isWindows;
const runWithColor = utils.runWithColor;

const mvnCwd = path.resolve(__dirname, '..', '..');

if (!process.env.FAST_BUILD) {
  const mavenCmd = 'mvn clean package -DskipTests -Dskip.docker -Pskip.fe.build';

  console.log();
  console.log(chalk.green('###########################################################################'));
  console.log(chalk.green('####  Building Camunda Optimize...                                      ###'));
  console.log(chalk.green('###########################################################################'));
  console.log();

  const mvnCleanPackage = runWithColor(mavenCmd, 'maven', chalk.green, {
    cwd: mvnCwd
  });

  mvnCleanPackage.on('close', startDocker);
} else {
  startBackend();
}

function startDocker() {
  console.log();
  console.log(chalk.green('###########################################################################'));
  console.log(chalk.green('####  Camunda Optimize has been built!                                 ###'));
  console.log(chalk.green('###########################################################################'));
  console.log();

  console.log();
  console.log(chalk.blue('###########################################################################'));
  console.log(chalk.blue('####  Starting docker with Camunda Platform Engine and Elasticsearch... ###'));
  console.log(chalk.blue('###########################################################################'));
  console.log();

  const docker = runWithColor('docker-compose up -d', 'docker', chalk.blue);
  docker.on('close', deployEngineData);
}

function deployEngineData() {
  console.log();
  console.log(chalk.blue('###########################################################################'));
  console.log(chalk.blue('####    Camunda Platform Engine and Elasticsearch have been started!   ###'));
  console.log(chalk.blue('###########################################################################'));
  console.log();

  console.log();
  console.log(chalk.magenta('###########################################################################'));
  console.log(chalk.magenta('####  Deploying engine data...                                          ###'));
  console.log(chalk.magenta('###########################################################################'));
  console.log();
  const deployDataPromise =
    engine.deployEngineData()
        .then(startBackend)
        .catch(console.error);
}

function startBackend() {
  console.log();
  console.log(chalk.magenta('###########################################################################'));
  console.log(chalk.magenta('####  Engine data has been deployed!                                   ###'));
  console.log(chalk.magenta('###########################################################################'));
  console.log();

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
    console.log();
    console.log(chalk.cyan('###########################################################################'));
    console.log(chalk.cyan('####  Starting Optimize Backend...                                      ###'));
    console.log(chalk.cyan('###########################################################################'));
    console.log();

    const backendJar = utils.findFile(extractDir, '.jar');
    const environmentDir = path.resolve(extractDir, 'environment');
    const classpathSeparator = isWindows ? ';' : ':' ;
    const classpath = environmentDir + classpathSeparator + backendJar;

    console.log("Classpath: " + classpath);

    runWithColor('java -cp ' + classpath + ' org.camunda.optimize.Main', 'BE', chalk.cyan);

    return utils.waitForServer('http://localhost:8090');
  }
}
