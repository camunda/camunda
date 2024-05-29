/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const fs = require('fs');
const path = require('path');
const shell = require('shelljs');
const url = require('url');
const toPairs = require('lodash/toPairs');

const licenseChecker = path.resolve(__dirname, '..', 'node_modules', '.bin', 'license-checker');
const depJsonFile = path.resolve(__dirname, '..', 'dependencies.json');
const depMdFile = path.resolve(__dirname, '..', 'frontend-dependencies.md');

const npmLinkPrefix = 'https://www.npmjs.com/package/';

shell.exec(`${licenseChecker} --out ${depJsonFile} --production --json --relativeLicensePath`);

const dependencies = JSON.parse(fs.readFileSync(depJsonFile));

const lines = toPairs(dependencies)
  // no need to list optimize source code as dependency
  .filter(([name]) => name !== 'client@0.1.0')
  .reduce((lines, [name, {url: rawUrl, licenses, repository}]) => {
    const libLink = getLibraryLink(name, rawUrl, repository);

    return lines.concat(`* [${name}](${libLink}) (${licenses})`);
  }, []);

function getLibraryLink(name, rawUrl, repository) {
  const parsedUrl = rawUrl ? url.parse(rawUrl) : {};
  const libLink = parsedUrl.protocol ? rawUrl : repository;

  if (libLink) {
    return libLink;
  }

  // removes version from name if any
  // for example: indexof@0.0.1 -> indexof, some-name -> some-name
  const matched = name.match(/^[^@]+/);
  const packageName = matched ? matched[0] : name;

  return npmLinkPrefix + packageName;
}

const fileHeader = `
---
title: 'Front-End Dependencies'
weight: 90

menu:
  main:
    identifier: "technical-guide-front-end-third-party-libraries"
    parent: "technical-guide-third-party-libraries"

---
`;

fs.writeFileSync(depMdFile, fileHeader + lines.join('\n'));
