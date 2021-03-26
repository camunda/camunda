/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {config} from '../config';
import {setup} from './Dashboard.setup';
import {demoUser} from './utils/Roles';
import {wait} from './utils/wait';
import {screen, within} from '@testing-library/testcafe';

fixture('Dashboard')
  .page(config.endpoint)
  .before(async () => {
    await setup();
    await wait();
  })
  .beforeEach(async (t) => {
    await t.useRole(demoUser);
    await t.maximizeWindow();
    await t.navigateTo('/');
  });

test('Statistics', async (t) => {
  const incidentInstancesCount = Number(
    await within(screen.getByTestId('metric-panel')).queryByTestId(
      'incident-instances-badge'
    ).textContent
  );
  const activeInstancesCount = Number(
    await within(screen.getByTestId('metric-panel')).queryByTestId(
      'active-instances-badge'
    ).textContent
  );

  await t
    .expect(screen.getByTestId('total-instances-link').textContent)
    .eql(
      `${
        incidentInstancesCount + activeInstancesCount
      } Running Instances in total`
    )
    .expect(incidentInstancesCount)
    .eql(1)
    .expect(activeInstancesCount)
    .eql(37);

  await t
    .expect(
      within(
        screen.getByRole('listitem', {name: 'Running Instances'})
      ).getByTestId('badge').textContent
    )
    .eql('38');

  await t
    .expect(
      within(screen.getByRole('listitem', {name: 'Filters'})).getByTestId(
        'badge'
      ).textContent
    )
    .eql('38');

  await t
    .expect(
      within(screen.getByRole('listitem', {name: 'Incidents'})).getByTestId(
        'badge'
      ).textContent
    )
    .eql('1');
});

test('Navigation to Instances View', async (t) => {
  const activeInstancesCount = await screen
    .getAllByTestId('active-instances-badge')
    .nth(0).textContent;

  const instancesWithIncidentCount = await screen
    .getAllByTestId('incident-instances-badge')
    .nth(0).textContent;

  await t
    .click(screen.getByTestId('active-instances-link'))
    .expect(screen.getByRole('checkbox', {name: 'Active'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Incidents'}).checked)
    .notOk();

  await t
    .expect(
      within(screen.getByRole('listitem', {name: 'Filters'})).getByTestId(
        'badge'
      ).textContent
    )
    .eql(activeInstancesCount);

  await t.click(screen.getByRole('listitem', {name: 'Dashboard'}));

  await t
    .click(screen.getByTestId('incident-instances-link'))
    .expect(screen.getByRole('checkbox', {name: 'Active'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Incidents'}).checked)
    .ok();

  await t
    .expect(
      within(screen.getByRole('listitem', {name: 'Filters'})).getByTestId(
        'badge'
      ).textContent
    )
    .eql(instancesWithIncidentCount);
});

test('Select instances by process', async (t) => {
  await t.expect(screen.queryByTestId('instances-by-process').exists).ok();

  const withinInstanceByProcess = within(
    screen.getByTestId('incident-byProcess-0')
  );

  const incidentCount = Number(
    await withinInstanceByProcess.getByTestId('incident-instances-badge')
      .textContent
  );
  const runningInstanceCount = Number(
    await withinInstanceByProcess.getByTestId('active-instances-badge')
      .textContent
  );

  const totalInstanceCount = incidentCount + runningInstanceCount;

  await t.click(screen.getByTestId('incident-byProcess-0'));

  await t
    .expect(screen.getByRole('checkbox', {name: 'Active'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Incidents'}).checked)
    .ok();
  await t
    .expect(
      within(screen.getByRole('listitem', {name: 'Filters'})).getByTestId(
        'badge'
      ).textContent
    )
    .eql(totalInstanceCount.toString());
});

test('Select instances by error message', async (t) => {
  await t.expect(screen.queryByTestId('incidents-by-error').exists).ok();

  const withinInstanceByError = within(
    screen.getByTestId('incident-byError-0')
  );

  const incidentCount = await withinInstanceByError.getByTestId(
    'incident-instances-badge'
  ).textContent;
  const incidentMessage = await withinInstanceByError.getByTestId(
    'incident-message'
  ).textContent;

  await t.click(screen.getByTestId('incident-byError-0'));

  await t
    .expect(screen.getByRole('checkbox', {name: 'Active'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Incidents'}).checked)
    .ok();

  await t
    .expect(
      within(screen.getByRole('listitem', {name: 'Filters'})).getByTestId(
        'badge'
      ).textContent
    )
    .eql(incidentCount);

  await t
    .expect(screen.getByRole('textbox', {name: 'Error Message'}).value)
    .eql(incidentMessage);

  await t.expect(screen.queryByTestId('diagram').exists).notOk();
});

test('Select instances by error message (expanded)', async (t) => {
  await t.expect(screen.queryByTestId('incidents-by-error').exists).ok();

  const withinInstanceByError = within(
    screen.getByTestId('incident-byError-0')
  );

  const incidentCount = await withinInstanceByError.getByTestId(
    'incident-instances-badge'
  ).textContent;
  const incidentMessage = await withinInstanceByError.getByTestId(
    'incident-message'
  ).textContent;

  await t.click(
    within(screen.getByTestId('incident-byError-0')).getByRole('button', {
      name: /Expand/,
    })
  );
  await t.click(
    within(screen.getByTestId('incident-byError-0'))
      .getAllByRole('listitem')
      .nth(0)
  );

  await t
    .expect(screen.getByRole('checkbox', {name: 'Active'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Incidents'}).checked)
    .ok();

  await t
    .expect(
      within(screen.getByRole('listitem', {name: 'Filters'})).getByTestId(
        'badge'
      ).textContent
    )
    .eql(incidentCount);

  await t
    .expect(screen.getByRole('textbox', {name: 'Error Message'}).value)
    .eql(incidentMessage);

  await t.expect(screen.getByTestId('diagram').exists).ok();
});
