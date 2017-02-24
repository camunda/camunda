var shell = require('shelljs');
var path = require('path');
var chalk = require('chalk');
var exec = require('child_process').exec;

var selenium = path.resolve(__dirname, '..', 'node_modules', '.bin', 'selenium-standalone');
var wdio = path.resolve(__dirname, '..', 'node_modules', '.bin', 'wdio');
var config = path.resolve(__dirname, '..', 'wdio.conf.js')

shell.exec(selenium + ' install');
var selniumProcess = exec(selenium + ' start');

//
var wdioProcess = shell.exec(wdio + ' ' + config , function(code) {
  console.log('*************************************');
  console.log('Tests ' + (code === 0 ? chalk.green('passed') : chalk.red('failed')));
  console.log('*************************************');

  selniumProcess.kill('SIGINT');

  shell.exit(code || 0);
});

process.on('SIGINT', function() {
    console.log('Gracefully shutting down from SIGINT (Ctrl+C)');

    selniumProcess.kill('SIGINT');
    wdioProcess.kill('SIGINT');

    shell.exit(0);
});
