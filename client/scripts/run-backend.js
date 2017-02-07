var backendConfig = require('./be-config');
var spawn = require('child_process').spawn;
var path = require('path');
var exec = require('child_process').exec;
var chalk = require('chalk');
var elasticClient = require('./elasticsearch');

var runElastic = spawn(backendConfig.elastic);

runElastic.stdout.on('data', function(data) {
  console.log('ElasticSearch: ', chalk.blue(data.toString()));
});

runElastic.stderr.on('data', function(data) {
  console.log('ElasticSearch: ', chalk.blue(data.toString()));
});

runElastic.on('close', function(code) {
  console.log(chalk.black.bgGreen('ElasticSearch process exited with code ' + code));
});

var mvnCwd = path.resolve(__dirname, '..', '..');

var mvnCleanPackage = exec('mvn clean package -DskipTests', {
  cwd: mvnCwd
});

mvnCleanPackage.stdout.on('data', function(data) {
  console.log('mvn clean package: ', chalk.yellow(data.toString()));
});

mvnCleanPackage.stderr.on('data', function(data) {
  console.log('mvn clean package: ', chalk.red(data.toString()));
});

mvnCleanPackage.on('close', function(code) {
  console.log(chalk.black.bgGreen('mvn clean package process exited with code ' + code));

  elasticClient
    .removeIndex()
    .then(function() {
      console.log(chalk.black.bgYellow('removed index!'));

      startBackend();
      addDemoData();
    })
    .catch(function() {
      console.log(chalk.red('Could not remove index'));

      startBackend();
      addDemoData();
    });

  function addDemoData() {
    setTimeout(function() {
      console.log(chalk.black.bgYellow('Generating demo data'));

      elasticClient
        .populateData()
        .then(function() {
          console.log(chalk.black.bgYellow('added demo data'));
        })
        .catch(function(error) {
          console.log(chalk.red('Could not add demo data'), error);
        });
    }, 8000);
  }

  function startBackend() {
    var backendServerPath = path.resolve(
      mvnCwd,
      'backend',
      'target',
      'optimize-backend-' + backendConfig.version + '-SNAPSHOT.jar'
    );
    var backendServer = exec('java -jar ' + backendServerPath);

    backendServer.stdout.on('data', function(data) {
      console.log('be: ', chalk.green(data.toString()));
    });

    backendServer.stderr.on('data', function(data) {
      console.log('be: ', chalk.red(data.toString()));
    });

    backendServer.on('close', function(code) {
      console.log(chalk.black.bgGreen('backend server process exited with code ' + code));
    });
  }
});
