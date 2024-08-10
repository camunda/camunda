/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import stylelint from 'stylelint';
import fs from 'fs';
import path from 'path';

const {
  createPlugin,
  utils: {report, ruleMessages, validateOptions},
} = stylelint;

const ruleName = 'camunda/license-header';

/** @type {import('stylelint').RuleMessages} */
const messages = ruleMessages(ruleName, {
  rejected: 'Header not present',
  added: 'Header added',
});
/** @type {import('stylelint').RuleMeta} */
const meta = {
  fixable: true,
};

/** @type {import('stylelint').Rule} */
const ruleFunction = (headerFilePath, _, context) => {
  return (root, result) => {
    const areOptionsValid = validateOptions(result, ruleName, {
      actual: headerFilePath,
      possible: [() => isFilePathValid(headerFilePath)],
    });

    if (!areOptionsValid) {
      console.error('Invalid rule options.');
      return;
    }

    try {
      const headerContent = fs.readFileSync(
        path.resolve(headerFilePath),
        'utf8',
      );

      if (root.source.input.css.startsWith(headerContent)) {
        return;
      }

      if (context.fix) {
        root.prepend(headerContent);
        return;
      }

      report({
        result,
        ruleName,
        message: messages.rejected,
        node: root,
      });
    } catch (err) {
      console.error(
        `Failed to load header from ${path.resolve(headerFilePath)}: ${err}`,
      );
      return;
    }
  };
};

ruleFunction.ruleName = ruleName;
ruleFunction.messages = messages;
ruleFunction.meta = meta;

function isFilePathValid(filePath) {
  try {
    const absoluteFilePath = path.resolve(filePath);
    fs.accessSync(absoluteFilePath, fs.constants.R_OK);
    return true;
  } catch {
    return false;
  }
}

export default createPlugin(ruleName, ruleFunction);
