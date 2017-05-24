/* eslint no-console: 0 */

const shell = require('shelljs');
const path = require('path');
const chalk = require('chalk');
const exec = require('child_process').exec;

const selenium = path.resolve(__dirname, '..', 'node_modules', '.bin', 'selenium-standalone');
const wdio = path.resolve(__dirname, '..', 'node_modules', '.bin', 'wdio');
const config = path.resolve(__dirname, '..', 'wdio.conf.js');

shell.exec(selenium + ' install');

exec(selenium + ' start');

const wdioArgs = process.argv.slice(2);
const wdioProcess = shell.exec(wdio + ' ' + config + ' ' + wdioArgs.join(' '), function(code) {
  console.log('*************************************');
  console.log('Tests ' + (code === 0 ? chalk.green('passed') : chalk.red('failed')));
  console.log('*************************************');

  killSelenium();

  shell.exit(code || 0);
});

process.on('SIGINT', function() {
  console.log('Gracefully shutting down from SIGINT (Ctrl+C)');

  killSelenium();
  wdioProcess.kill();

  shell.exit(0);
});

function killSelenium() {
  if (/linux/.test(process.platform)) {
    exec('pkill -f selenium');
  } else if (/^win/.test(process.platform)) {
    // TODO: OPT-178
    // For now don't do anything on windows
    // exec('taskkill /f /im java.exe');
  }
}
