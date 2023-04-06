/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const fs = require('fs');

const blacklistedFiles = ['setupTests.ts'];
const blacklistedDependencies = ['@lexical/'];

function getAllFilesInDirectory(dir, filelist) {
  var files = fs.readdirSync(dir);
  filelist = filelist || [];
  files.forEach(function (file) {
    if (fs.statSync(dir + file).isDirectory()) {
      filelist = getAllFilesInDirectory(dir + file + '/', filelist);
    } else {
      filelist.push(dir + file);
    }
  });
  return filelist;
}

function getInternalModules(dir) {
  return new Set(fs.readdirSync(dir).map((entry) => entry.split('.')[0]));
}

function isJavascriptFile(filename) {
  return /^(?!.*test\.(js|ts(x)?)).*\.(js|ts(x)?)$/g.test(filename);
}

function isStyleFile(filename) {
  return /^.*\.(css|scss)$/g.test(filename);
}

function isFileNotBlacklisted(filename) {
  return !blacklistedFiles.some((blacklistEntry) => filename.includes(blacklistEntry));
}

function getImportedModules(content, fileType) {
  let regex;
  if (fileType === 'javascript') {
    regex = RegExp('import\\s[^"\'`]*["\'`]([^.][^"\'`\\s]*)', 'g');
  } else if (fileType === 'style') {
    regex = RegExp('import\\s*["\'`][~]([^.][^"\'`\\s]*)', 'g');
  }

  const matches = [];
  let result = regex.exec(content);

  while (result !== null) {
    if (result[1] && !result[1].includes('!')) {
      if (result[1].charAt(0) === '@') {
        matches.push(result[1].split('/').slice(0, 2).join('/'));
      } else {
        matches.push(result[1].split('/')[0]);
      }
    }
    result = regex.exec(content);
  }

  return matches;
}

function getDeclaredDependencies() {
  return Object.keys(
    JSON.parse(fs.readFileSync(__dirname + '/../package.json', 'utf8')).dependencies
  );
}

const allFiles = getAllFilesInDirectory(__dirname + '/').filter(isFileNotBlacklisted);
const javascriptFiles = allFiles.filter(isJavascriptFile);
const styleFiles = allFiles.filter(isStyleFile);
const usedModules = new Set();
addUsedModules(javascriptFiles, 'javascript', usedModules);
addUsedModules(styleFiles, 'style', usedModules);

function addUsedModules(filesToCheck, fileType, usedModules) {
  filesToCheck.forEach((filename) => {
    const fileContent = fs.readFileSync(filename, 'utf8');
    const imports = getImportedModules(fileContent, fileType);

    imports.forEach((entry) => usedModules.add(entry));
  });
}

getInternalModules(__dirname + '/modules/').forEach((internalModule) => {
  usedModules.delete(internalModule);
});

const declaredDependencies = getDeclaredDependencies();

it('should use all declared dependencies in production code', () => {
  const unusedDependencies = new Set(declaredDependencies);
  usedModules.forEach((entry) => unusedDependencies.delete(entry));

  expect([...unusedDependencies]).toHaveLength(0);
});

it('should declare all used dependencies', () => {
  const undeclaredDependencies = new Set();
  usedModules.forEach((entry) => {
    if (
      !declaredDependencies.includes(entry) &&
      !blacklistedDependencies.some((dependency) => entry.includes(dependency))
    ) {
      undeclaredDependencies.add(entry);
    }
  });

  expect([...undeclaredDependencies]).toHaveLength(0);
});

it('should not allow api requests to start with slash', () => {
  const serviceFiles = javascriptFiles.filter((file) => {
    const fileContent = fs.readFileSync(file, 'utf8');
    return fileContent.match(/(post|get|del|put)\(['`]\/api/g);
  });

  expect(serviceFiles).toHaveLength(0);
});
