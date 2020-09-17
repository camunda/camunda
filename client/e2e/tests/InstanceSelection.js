/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {config} from '../config';
import {setup} from './Dashboard.setup.js';
import * as Elements from './InstancesSelection.elements.js';
import {demoUser} from './utils/Roles';
import {wait} from './utils/wait';
import {DEFAULT_TIMEOUT} from './constants';

fixture('Select Instances')
  .page(config.endpoint)
  .before(async (t) => {
    await setup();
    await wait(DEFAULT_TIMEOUT);
  })
  .beforeEach(async (t) => {
    await t.useRole(demoUser);
    await t.maximizeWindow();
  });

test('Selection of instances applied/removed on filter selection', async (t) => {
  // open instances page, select the first instance on the instances table
  await t
    .navigateTo(`${config.endpoint}/#/instances`)
    .click(Elements.instanceCheckbox.nth(0));

  // When instance is selected, crate operation dropdown should be displayed
  await t.expect(Elements.createOperationDropdown.exists).ok();

  // click incidents link on header and see create operation dropdown is disappeared
  await t
    .click(Elements.headerLinkIncidents)
    .expect(Elements.createOperationDropdown.exists)
    .notOk();

  // go back to instances, select the first instance on the instances table
  await t
    .click(Elements.headerLinkInstances)
    .click(Elements.instanceCheckbox.nth(0));

  // apply a filter from the filters panel and see create operation dropdown is disappeared
  await t.typeText(Elements.errorMessageFilter, 'An error message');
  await t.expect(Elements.createOperationDropdown.exists).notOk();

  // remove filter, select first instance on the instances table
  await t
    .selectText(Elements.errorMessageFilter)
    .pressKey('delete')
    .click(Elements.instanceCheckbox.nth(0));

  // go to next page on the instances table and see create operation dropdown is still there
  await t
    .click(Elements.nextPage)
    .expect(Elements.createOperationDropdown.exists)
    .ok();

  // sort by workflow name on the instances table and see create operation dropdown is still there
  await t
    .click(Elements.sortByWorkflowName)
    .expect(Elements.createOperationDropdown.exists)
    .ok();
});
