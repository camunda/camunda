const fs = require('fs');
const path = require('path');
const shell = require('shelljs');
const url = require('url');

const licenseChecker = path.resolve(
  __dirname,
  '..',
  'node_modules',
  '.bin',
  'license-checker'
);
const depJsonFile = path.resolve(
  __dirname,
  '..',
  'dependencies.json'
);
const depMdFile = path.resolve(
  __dirname,
  '..',
  'frontend-dependencies.md'
);

shell.exec(`${licenseChecker} --out ${depJsonFile} --production --json --relativeLicensePath`);

const dependencies = JSON.parse(
  fs.readFileSync(depJsonFile)
);

const lines = Object
  .entries(dependencies)
  // no need to list optimize source code as dependency
  .filter(([name]) => !name.startsWith('optimize-client'))
  .reduce((lines, [name, {url: rawUrl, licenses, repository}]) => {
    const parsedUrl = rawUrl ? url.parse(rawUrl) : {};
    const libLink = parsedUrl.protocol ? rawUrl : repository;

    return lines.concat(`* [${name}](${libLink}) (${licenses})`);
  }, []);

const fileHeader = `
---
title: 'Front-End Dependencies'
weight: 90

menu:
  main:
    identifier: "user-guide-introduction-front-end-third-party-libraries"
    parent: "user-guide-introduction"

---
`;

fs.writeFileSync(depMdFile, fileHeader + lines.join('\n'));

shell.rm(depJsonFile); // remove json file
