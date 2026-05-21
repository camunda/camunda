/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const {appendFileSync, mkdirSync} = require('fs');

module.exports = {
  hooks: {
    test: {
      after: async (t) => {
        const {error, warn} = await t.getBrowserConsoleMessages();
        if (error.length === 0 && warn.length === 0) return;

        mkdirSync('./build', {recursive: true});

        const fixtureName = t.testRun?.test?.fixture?.name ?? 'unknown fixture';
        const testName = t.testRun?.test?.name ?? 'unknown test';
        const lines = [
          `\n[${fixtureName}] ${testName}`,
          ...error.map((m) => `  [error] ${m}`),
          ...warn.map((m) => `  [warn]  ${m}`),
        ].join('\n');

        appendFileSync('./build/browser-console.log', lines + '\n');
      },
    },
  },
};
