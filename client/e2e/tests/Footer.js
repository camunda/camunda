/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import setup from '../setup';
import config from '../config';
import * as u from '../utils';

import * as Footer from './Footer.elements.js';

fixture('Footer')
  .page(config.endpoint)
  .before(setup);

test('should not show a footer on the login page, but after login', async t => {
  // testcafe appears to have a very rudimentary implementation of visibilityChecks
  // than can report false positives. Maybe we can use these assertions once
  // https://github.com/DevExpress/testcafe/issues/1186 is fixed

  // await t.expect(Footer.connection('Elasticsearch').visible).notOk();
  // await t.expect(Footer.connection('camunda-bpm').visible).notOk();

  await u.login(t);

  await t.expect(Footer.connection('Elasticsearch').visible).ok();
  await t.expect(Footer.connection('camunda-bpm').visible).ok();
});
