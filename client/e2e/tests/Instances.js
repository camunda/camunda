/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {config} from '../config';
import {setup} from './Instances.setup.js';
import {demoUser} from './utils/Roles';
import {wait} from './utils/wait';
import {screen, within} from '@testing-library/testcafe';

fixture('Instances')
  .page(config.endpoint)
  .before(async (ctx) => {
    ctx.initialData = await setup();
    await wait(20000);
  })
  .beforeEach(async (t) => {
    await t.useRole(demoUser);
    await t.navigateTo('/');
  });

test('Instances Page Initial Load', async (t) => {
  const {initialData} = t.fixtureCtx;

  await t.click(screen.getByRole('listitem', {name: 'Running Instances'}));

  await t
    .expect(screen.getByRole('checkbox', {name: 'Running Instances'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Active'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Incidents'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Finished Instances'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Completed'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Canceled'}).checked)
    .notOk();

  await t
    .expect(screen.getByText('There is no Workflow selected.').exists)
    .ok()
    .expect(
      screen.getByText(
        'To see a diagram, select a Workflow in the Filters panel.'
      ).exists
    )
    .ok();

  await t.typeText(
    screen.getByRole('textbox', {
      name: /instance id\(s\) separated by space or comma/i,
    }),
    `${initialData.instanceWithoutAnIncident}, ${initialData.instanceWithAnIncident}`
  );

  const withinInstancesList = within(screen.getByTestId('instances-list'));
  await t
    .expect(withinInstancesList.getAllByRole('row').count)
    .eql(2, {timeout: 5000});

  await t
    .expect(withinInstancesList.getByTestId('INCIDENT-icon').exists)
    .ok()
    .expect(withinInstancesList.getByTestId('ACTIVE-icon').exists)
    .ok();
});
