/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import dotenv from 'dotenv';

import config from '../config';
import * as u from '../utils';

import * as Common from '../sm-tests/Common.elements.js';
import * as e from './smokeTest.elements.js';

fixture('Smoke test').page(config.collectionsEndpoint);

test('create a report from a template', async (t) => {
  if (!process.argv.includes('ci')) {
    dotenv.config();
  }
  await t.maximizeWindow();

  await t
    .typeText(e.usernameInput, process.env.AUTH0_USEREMAIL)
    .click(e.submitButton)
    .typeText(e.passwordInput, process.env.AUTH0_USERPASSWORD)
    .click(e.submitButton);

  await t.click(Common.collectionsPage);
  await t.click(Common.createNewButton);
  await t.click(Common.menuOption('Report'));
  await t.click(e.emptyTemplate);
  await t.click(Common.modalConfirmButton);
  await u.save(t);
});
