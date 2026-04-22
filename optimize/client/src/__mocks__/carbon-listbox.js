/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// @carbon/react 1.104+ CJS modules use `exports.default = X` without `__esModule: true`,
// causing babel's _interopRequireDefault to double-wrap default imports in Jest.
// This shim adds __esModule: true so that `import ListBox from '@carbon/react/lib/components/ListBox'`
// resolves correctly to the ListBox component (with sub-components like MenuIcon attached).
//
// We require by absolute path to avoid a circular moduleNameMapper reference.
const path = require('path');
const m = require(
  path.join(__dirname, '../../node_modules/@carbon/react/lib/components/ListBox/index.js')
);
module.exports = {__esModule: true, default: m.default};
