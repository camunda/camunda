var fs = require('fs');
var path = require('path');
var request = promisify(require('request'));
var tarball = require('tarball-extract');
var chalk = require('chalk');
var exec = require('child_process').exec;

var readdir = promisify(fs.readdir, fs);

exports.waitForServer = waitForServer;
exports.readdir = readdir;
exports.findPath = findPath;
exports.findFile = findFile;
exports.promisify = promisify;
exports.extract = promisify(tarball.extractTarball);
exports.runWithColor = runWithColor;

function runWithColor(command, name, color, options = {}) {
  const process = exec(command, options);

  process.stdout.on('data', function(data) {
    console.log(name + ' : ' + color(data.toString()));
  });

  process.stderr.on('data', function(data) {
    console.error(name + ' : ' + chalk.red(data.toString()));
  });

  process.on('close', function(code) {
    console.log(name + ' closed with code ' + code);
  });

  return process;
}

function waitForServer(address, retries = 5, waitTime = 10) {
  return delay(waitTime * 1000)
    .then(request.bind(null, address))
    .catch(function (error) {
      if (retries > 1) {
        return waitForServer(address, retries - 1, waitTime);
      }

      return Promise.reject(error);
    });
}

function delay(time) {
  return new Promise(function(resolve) {
    setTimeout(function() {
      resolve(time);
    }, time);
  });
}

function findPath(start, predicates) {
  return predicates.reduce(function(directory, predicate) {
    return findFile(directory, predicate);
  }, start);
}

function findFile(directory, predicate) {
  if (typeof predicate === 'string') {
    return findFile(directory, function(file) {
      return file.slice(-1 * predicate.length) === predicate;
    });
  } else if (predicate instanceof RegExp) {
    return findFile(directory, function(file) {
      return predicate.test(file);
    });
  }

  return path.resolve(
    directory,
    fs.readdirSync(directory)
      .filter(predicate)[0]
  );
}

function promisify(original, thisArg) {
 return function() {
   var args = Array.prototype.slice.call(arguments);

   return new Promise(function(resolve, reject) {
     original.apply(
       thisArg,
       args.concat([function(error, result) {
         if (error) {
           reject(error);
         }

         resolve(result);
       }])
     );
   });
 };
}
