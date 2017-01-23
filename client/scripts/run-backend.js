var backendConfig = require('./be-config');
var spawn = require('child_process').spawn;
var path = require('path');
var exec = require('child_process').exec;

var runElastic = spawn(backendConfig.elastic);

runElastic.stdout.on('data', function(data) {
  console.log('ElasticSearch: ', '\x1b[36m', data.toString(), '\x1b[0m');
});

runElastic.stderr.on('data', function(data) {
  console.log('ElasticSearch: ', '\x1b[31m', data.toString(), '\x1b[0m');
});

runElastic.on('close', function(code) {
  console.log('\x1b[40m', 'ElasticSearch process exited with code ' + code, '\x1b[0m');
});

var mvnCwd = path.resolve(__dirname, '..', '..');

var mvnCleanPackage = exec('mvn clean package -DskipTests', {
  cwd: mvnCwd
});

mvnCleanPackage.stdout.on('data', function(data) {
  console.log('mvn clean package: ', '\x1b[33m', data.toString(), '\x1b[0m');
});

mvnCleanPackage.stderr.on('data', function(data) {
  console.log('mvn clean package: ', '\x1b[31m', data.toString(), '\x1b[0m');
});

mvnCleanPackage.on('close', function(code) {
  console.log('\x1b[40m', 'mvn clean package process exited with code ' + code, '\x1b[0m');

  var backendServerPath = path.resolve(
    mvnCwd,
    'optimize-backend',
    'target',
    'optimize-backend-' + backendConfig.version + '-SNAPSHOT-jar-with-dependencies.jar'
  );
  var backendServer = exec('java -jar ' + backendServerPath);

  backendServer.stdout.on('data', function(data) {
    console.log('be: ', '\x1b[33m', data.toString(), '\x1b[0m');
  });

  backendServer.stderr.on('data', function(data) {
    console.log('be: ', '\x1b[31m', data.toString(), '\x1b[0m');
  });

  backendServer.on('close', function(code) {
    console.log('\x1b[40m', 'backend server process exited with code ' + code, '\x1b[0m');
  });
});
