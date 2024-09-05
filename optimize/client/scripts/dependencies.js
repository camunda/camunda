/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import fs from 'fs';
import {resolve as _resolve, dirname} from 'path';
import {execFileSync} from 'child_process';
import {fileURLToPath} from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const licenseChecker = _resolve(__dirname, '..', 'node_modules', '.bin', 'license-checker');
const depJsonFile = _resolve(__dirname, '..', 'dependencies.json');
const depMdFile = _resolve(__dirname, '..', 'frontend-dependencies.md');

const npmLinkPrefix = 'https://www.npmjs.com/package/';

const args = ['--out', depJsonFile, '--production', '--json', '--relativeLicensePath'];

execFileSync(licenseChecker, args, {stdio: 'inherit'});

const dependencies = JSON.parse(fs.readFileSync(depJsonFile));

const lines = toPairs(dependencies)
  // no need to list optimize source code as dependency
  .filter(([name]) => name !== 'client@0.1.0')
  .reduce((lines, [name, {url: rawUrl, licenses, repository}]) => {
    const libLink = getLibraryLink(name, rawUrl, repository);

    return lines.concat(`* [${name}](${libLink}) (${licenses})`);
  }, []);

function getLibraryLink(name, rawUrl, repository) {
  const parsedUrl = rawUrl ? new URL(rawUrl.startsWith('http') ? rawUrl : `https://${rawUrl}`) : {};
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

function toPairs(obj) {
  const pairs = [];

  for (const key in obj) {
    if (obj.hasOwnProperty(key)) {
      pairs.push([key, obj[key]]);
    }
  }

  return pairs;
}
