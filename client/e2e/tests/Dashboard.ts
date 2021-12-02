/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {config} from '../config';
import {
  setup,
  cmActiveCheckbox,
  cmIncidentsCheckbox,
  cmErrorMessageField,
} from './Dashboard.setup';
import {demoUser} from './utils/Roles';
import {wait} from './utils/wait';
import {screen, within} from '@testing-library/testcafe';
import {IS_NEW_FILTERS_FORM} from '../../src/modules/feature-flags';

fixture('Dashboard')
  .page(config.endpoint)
  .before(async () => {
    await setup();
    await wait();
  })
  .beforeEach(async (t) => {
    await t
      .useRole(demoUser)
      .maximizeWindow()
      .click(
        screen
          .queryAllByRole('link', {
            name: /dashboard/i,
          })
          .nth(0)
      );
  });

test('Statistics', async (t) => {
  const incidentInstancesCount = Number(
    await within(screen.queryByTestId('metric-panel')).queryByTestId(
      'incident-instances-badge'
    ).textContent
  );
  const activeInstancesCount = Number(
    await within(screen.queryByTestId('metric-panel')).queryByTestId(
      'active-instances-badge'
    ).textContent
  );

  await t
    .expect(screen.queryByTestId('total-instances-link').textContent)
    .eql(
      `${
        incidentInstancesCount + activeInstancesCount
      } Running Instances in total`
    )
    .expect(incidentInstancesCount)
    .eql(1)
    .expect(activeInstancesCount)
    .eql(37);
});

test('Navigation to Instances View', async (t) => {
  const activeInstancesCount = await screen
    .getAllByTestId('active-instances-badge')
    .nth(0).textContent;

  const instancesWithIncidentCount = await screen
    .getAllByTestId('incident-instances-badge')
    .nth(0).textContent;

  await t.click(screen.queryByTestId('active-instances-link'));

  if (IS_NEW_FILTERS_FORM) {
    await t
      .expect(cmActiveCheckbox.hasClass('checked'))
      .ok()
      .expect(cmIncidentsCheckbox.hasClass('checked'))
      .notOk();
  } else {
    await t
      .expect(screen.queryByRole('checkbox', {name: 'Active'}).checked)
      .ok()
      .expect(screen.queryByRole('checkbox', {name: 'Incidents'}).checked)
      .notOk();
  }

  await t
    .expect(
      screen.getAllByTestId('filter-panel-header-badge').nth(0).textContent
    )
    .eql(activeInstancesCount);

  await t.click(
    screen
      .queryAllByRole('link', {
        name: /dashboard/i,
      })
      .nth(0)
  );

  await t.click(screen.queryByTestId('incident-instances-link'));

  if (IS_NEW_FILTERS_FORM) {
    await t
      .expect(cmActiveCheckbox.hasClass('checked'))
      .notOk()
      .expect(cmIncidentsCheckbox.hasClass('checked'))
      .ok();
  } else {
    await t
      .expect(screen.queryByRole('checkbox', {name: 'Active'}).checked)
      .notOk()
      .expect(screen.queryByRole('checkbox', {name: 'Incidents'}).checked)
      .ok();
  }

  await t
    .expect(
      screen.getAllByTestId('filter-panel-header-badge').nth(0).textContent
    )
    .eql(instancesWithIncidentCount);
});

test('Select instances by process', async (t) => {
  await t.expect(screen.queryByTestId('instances-by-process').exists).ok();

  const withinInstanceByProcess = within(
    screen.queryByTestId('incident-byProcess-0')
  );

  const incidentCount = Number(
    await withinInstanceByProcess.queryByTestId('incident-instances-badge')
      .textContent
  );
  const runningInstanceCount = Number(
    await withinInstanceByProcess.queryByTestId('active-instances-badge')
      .textContent
  );

  const totalInstanceCount = incidentCount + runningInstanceCount;

  await t.click(screen.queryByTestId('incident-byProcess-0'));

  if (IS_NEW_FILTERS_FORM) {
    await t
      .expect(cmActiveCheckbox.hasClass('checked'))
      .ok()
      .expect(cmIncidentsCheckbox.hasClass('checked'))
      .ok();
  } else {
    await t
      .expect(screen.queryByRole('checkbox', {name: 'Active'}).checked)
      .ok()
      .expect(screen.queryByRole('checkbox', {name: 'Incidents'}).checked)
      .ok();
  }

  await t
    .expect(
      screen.getAllByTestId('filter-panel-header-badge').nth(0).textContent
    )
    .eql(totalInstanceCount.toString());
});

test('Select instances by error message', async (t) => {
  await t.expect(screen.queryByTestId('incidents-by-error').exists).ok();

  const withinInstanceByError = within(
    screen.queryByTestId('incident-byError-0')
  );

  const incidentCount = await withinInstanceByError.queryByTestId(
    'incident-instances-badge'
  ).textContent;
  const incidentMessage = await withinInstanceByError.queryByTestId(
    'incident-message'
  ).textContent;

  await t.click(screen.queryByTestId('incident-byError-0'));

  if (IS_NEW_FILTERS_FORM) {
    await t
      .expect(cmActiveCheckbox.hasClass('checked'))
      .notOk()
      .expect(cmIncidentsCheckbox.hasClass('checked'))
      .ok();
  } else {
    await t
      .expect(screen.queryByRole('checkbox', {name: 'Active'}).checked)
      .notOk()
      .expect(screen.queryByRole('checkbox', {name: 'Incidents'}).checked)
      .ok();
  }

  await t
    .expect(
      screen.getAllByTestId('filter-panel-header-badge').nth(0).textContent
    )
    .eql(incidentCount);

  const errorMessageField = IS_NEW_FILTERS_FORM
    ? cmErrorMessageField
    : screen.queryByRole('textbox', {name: 'Error Message'});

  await t.expect(errorMessageField.value).eql(incidentMessage);

  await t.expect(screen.queryByTestId('diagram').exists).notOk();
});

test('Select instances by error message (expanded)', async (t) => {
  await t.expect(screen.queryByTestId('incidents-by-error').exists).ok();

  const withinInstanceByError = within(
    screen.queryByTestId('incident-byError-0')
  );

  const incidentCount = await withinInstanceByError.queryByTestId(
    'incident-instances-badge'
  ).textContent;
  const incidentMessage = await withinInstanceByError.queryByTestId(
    'incident-message'
  ).textContent;

  await t.click(
    within(screen.queryByTestId('incident-byError-0')).queryByRole('button', {
      name: /Expand/,
    })
  );
  await t.click(
    within(screen.queryByTestId('incident-byError-0'))
      .getAllByRole('listitem')
      .nth(0)
  );

  if (IS_NEW_FILTERS_FORM) {
    await t
      .expect(cmActiveCheckbox.hasClass('checked'))
      .notOk()
      .expect(cmIncidentsCheckbox.hasClass('checked'))
      .ok();
  } else {
    await t
      .expect(screen.queryByRole('checkbox', {name: 'Active'}).checked)
      .notOk()
      .expect(screen.queryByRole('checkbox', {name: 'Incidents'}).checked)
      .ok();
  }

  await t
    .expect(
      screen.getAllByTestId('filter-panel-header-badge').nth(0).textContent
    )
    .eql(incidentCount);

  const errorMessageField = IS_NEW_FILTERS_FORM
    ? cmErrorMessageField
    : screen.queryByRole('textbox', {name: 'Error Message'});

  await t.expect(errorMessageField.value).eql(incidentMessage);

  await t.expect(screen.queryByTestId('diagram').exists).ok();
});
