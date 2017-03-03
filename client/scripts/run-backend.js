var path = require('path');
var chalk = require('chalk');
var elasticClient = require('./elasticsearch');
var utils = require('./utils');
var isWindows = /^win/.test(process.platform);
var runWithColor = utils.runWithColor;
var shell = require('shelljs');

var mvnCwd = path.resolve(__dirname, '..', '..');
var mvnCleanPackage = runWithColor('mvn clean package -Pproduction -DskipTests', 'maven', chalk.green, {
  cwd: mvnCwd
});

mvnCleanPackage.on('close', function(code) {
  var distro = path.resolve(mvnCwd, 'distro', 'target');
  var extractDir = path.resolve(distro, 'distro');
  var archive = utils.findFile(distro, '.tar.gz');

  console.log('Start extracting', archive);

  utils.extract(archive, extractDir)
    .then(startBackend)
    .catch(function(error) {
      console.error(chalk.red('Start up failed', error));
      shell.exit(1);
    });

  function startBackend() {
    console.log('Starting elastic search');

    var elasticSearch = utils.findPath(extractDir, [
      'server',
      /^elastic/,
      'bin',
      isWindows ? 'elasticsearch.bat' : 'elasticsearch'
    ]);

    runWithColor(elasticSearch, 'Elastic Search', chalk.blue);

    return utils.waitForServer('http://localhost:9200')
      .then(function() {
        var backendJar = utils.findFile(extractDir, '.jar');

        runWithColor('java -jar ' + backendJar, 'BE', chalk.green);

        return utils.waitForServer('http://localhost:8090');
      })
      .then(function() {
        return addDemoData();
      });
  }

  function addDemoData() {
    console.log(chalk.black.bgYellow('Generating demo data'));

    return elasticClient
      .populateData()
      .then(function() {
        console.log(chalk.black.bgYellow('added demo data'));
      })
      .catch(function(error) {
        console.log(chalk.red('Could not add demo data'), error);
      });
  }
});
