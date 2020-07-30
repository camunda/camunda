/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {config} from '../config';
import {setup} from './Dashboard.setup.js';
import {demoUser} from './utils/Roles';
import {wait} from './utils/wait';

import * as Header from './Header.elements.js';
import * as Dashboard from './Dashboard.elements.js';
import * as Instances from './Instances.elements.js';

fixture('Dashboard')
  .page(config.endpoint)
  .before(async () => {
    await setup();
    await wait(20000);
  })
  .beforeEach(async (t) => {
    await t.useRole(demoUser);
    await t.navigateTo('/');
  });

test('Dashboard statistics', async (t) => {
  await t
    .expect(Dashboard.totalInstancesLink.textContent)
    .eql('37 Running Instances in total')
    .expect(Dashboard.incidentInstancesBadge.textContent)
    .eql('0')
    .expect(Dashboard.activeInstancesBadge.textContent)
    .eql('37');
});

test('Navigation to Instances View', async (t) => {
  await t
    .click(Dashboard.activeInstancesLink)
    .expect(Instances.filtersRunningActiveCheckbox.checked)
    .ok()
    .expect(Instances.filtersRunningIncidentsCheckbox.checked)
    .notOk();

  await t.click(Header.dashboardLink);

  await t
    .click(Dashboard.incidentInstancesLink)
    .expect(Instances.filtersRunningActiveCheckbox.checked)
    .notOk()
    .expect(Instances.filtersRunningIncidentsCheckbox.checked)
    .ok();
});
