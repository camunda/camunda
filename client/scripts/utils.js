/* eslint no-console: 0 */

const fs = require('fs');
const path = require('path');
const request = promisify(require('request'));
const tarball = require('tarball-extract');
const chalk = require('chalk');
const exec = require('child_process').exec;

const readdir = promisify(fs.readdir, fs);

exports.waitForServer = waitForServer;
exports.isServerUp = isServerUp;
exports.readdir = readdir;
exports.findPath = findPath;
exports.findFile = findFile;
exports.promisify = promisify;
exports.extract = promisify(tarball.extractTarball);
exports.runWithColor = runWithColor;
exports.isWindows = /^win/.test(process.platform);
exports.runInSequence = runInSequence;
exports.changeFile = changeFile;

function runWithColor(command, name, color, options = {}) {
  console.log(chalk.yellow(command), options);

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

function waitForServer(address, retries = 5, waitTime = 5) {
  return delay(waitTime * 1000)
    .then(request.bind(null, address))
    .catch(error => {
      if (retries > 1) {
        return waitForServer(address, retries - 1, waitTime);
      }

      return Promise.reject(error);
    });
}

function isServerUp(address) {
  return request(address)
    .then(() => true)
    .catch(() => false);
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

function findFile(directory, predicate, multiple) {
  if (typeof predicate === 'string') {
    return findFile(directory, function(file) {
      return file.slice(-1 * predicate.length) === predicate;
    }, multiple);
  } else if (predicate instanceof RegExp) {
    return findFile(directory, function(file) {
      return predicate.test(file);
    }, multiple);
  }

  const files = fs.readdirSync(directory)
    .filter(predicate);

  if (multiple) {
    return files.map(file => path.resolve(directory, file));
  }

  return path.resolve(
    directory,
    files[0]
  );
}

function promisify(original, thisArg) {
  return function() {
    const args = Array.prototype.slice.call(arguments);

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

function runInSequence(values, taskFn, chunkSize = 5) {
  return splitIntoChunks(values, chunkSize)
    .reduce(
      (listPromise, chunkValues, mainIndex) => {
        return listPromise.then(list => {
          return Promise.all(
            chunkValues.map(
              (value, minorIndex) => taskFn(value, mainIndex + minorIndex)
            )
          ).then(result => list.concat(result));
        });
      },
      Promise.resolve([])
    );
}

function splitIntoChunks(data, chunkSize) {
  const result = [];
  let currentChunk = [];
  let chunkIndex = 0;

  for (let i = 0; i < data.length; i++) {
    currentChunk.push(data[i]);
    chunkIndex++;

    if (chunkIndex === chunkIndex || i + 1 === data.length) {
      result.push(currentChunk);
      chunkIndex = 0;
      currentChunk = [];
    }
  }

  return result;
}

function changeFile(file, change) {
  if (change.regexp instanceof RegExp) {
    return changeFile(file, (content) => {
      return content.replace(change.regexp, change.replacement);
    });
  }

  const content = fs.readFileSync(file);

  fs.writeFileSync(
    file,
    change(content.toString())
  );
}
