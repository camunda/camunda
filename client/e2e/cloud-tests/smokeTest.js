/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import config from '../config';
import * as u from '../utils';

import * as e from './smokeTest.elements.js';
import * as Common from '../tests/Common.elements.js';

fixture('Smoke test').page(config.endpoint);

test('create a report from a template', async (t) => {
  if (!process.argv.includes('ci')) {
    require('dotenv').config();
  }
  await t.maximizeWindow();

  await t
    .typeText(e.usernameInput, process.env.AUTH0_USEREMAIL)
    .click(e.submitButton)
    .typeText(e.passwordInput, process.env.AUTH0_USERPASSWORD)
    .click(e.submitButton);

  await t.click(e.whatsNewCloseBtn);
  await t.click(Common.createNewMenu);
  await t.click(Common.option('Report'));
  await t.click(e.emptyTemplate);
  await t.click(Common.modalConfirmButton);
  await u.save(t);
});
