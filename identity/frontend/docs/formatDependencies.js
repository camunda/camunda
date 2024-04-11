/*
 * @license Identity
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license
 * agreements. Licensed under a proprietary license. See the License.txt file for more information. You may not use this
 * file except in compliance with the proprietary license.
 */

/* eslint-disable */

const fs = require('fs');
const path = require('path');
const url = require('url');
const toPairs = require('lodash/toPairs');

const depJsonFile = path.resolve(__dirname, 'dependencies.json');
const depMdFile = path.resolve(__dirname, 'dependencies.md');

const npmLinkPrefix = 'https://www.npmjs.com/package/';

const dependencies = JSON.parse(fs.readFileSync(depJsonFile));

const lines = toPairs(dependencies).reduce(
  (lines, [name, { url: rawUrl, licenses, repository }]) => {
    const libLink = getLibraryLink(name, rawUrl, repository);

    return lines.concat(`* [${name}](${libLink}) (${licenses})`);
  },
  []
);

function getLibraryLink(name, rawUrl, repository) {
  try {
    // Check if URL is valid
    new url.URL(rawUrl);
    return rawUrl;
  } catch (e) {
    if (repository) return repository;
  }

  // removes version from name if any
  // for example: indexof@0.0.1 -> indexof, some-name -> some-name
  const matched = name.match(/^[^@]+/);
  const packageName = matched ? matched[0] : name;

  return npmLinkPrefix + packageName;
}

fs.writeFileSync(depMdFile, lines.join('\n'));
