/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {config} from '../config';
import {setup} from './Dashboard.setup';
import {demoUser} from './utils/Roles';
import {wait} from './utils/wait';
import {validateCheckedState} from './utils/validateCheckedState';
import {screen, within} from '@testing-library/testcafe';
import {instancesPage as InstancesPage} from './PageModels/Instances';

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
  const activeProcessInstancesCount = Number(
    await within(screen.queryByTestId('metric-panel')).queryByTestId(
      'active-instances-badge'
    ).textContent
  );

  await t
    .expect(screen.queryByTestId('total-instances-link').textContent)
    .eql(
      `${
        incidentInstancesCount + activeProcessInstancesCount
      } Running Process Instances in total`
    )
    .expect(incidentInstancesCount)
    .eql(1)
    .expect(activeProcessInstancesCount)
    .eql(37);
});

test('Navigation to Instances View', async (t) => {
  const activeProcessInstancesCount = await screen
    .getAllByTestId('active-instances-badge')
    .nth(0).textContent;

  const instancesWithIncidentCount = await screen
    .getAllByTestId('incident-instances-badge')
    .nth(0).textContent;

  await t.click(screen.queryByTestId('active-instances-link'));

  await validateCheckedState({
    checked: [InstancesPage.Filters.active.field],
    unChecked: [InstancesPage.Filters.incidents.field],
  });

  await t
    .expect(screen.getByTestId('result-count').textContent)
    .eql(`${activeProcessInstancesCount} results found`);

  await t.click(
    screen
      .queryAllByRole('link', {
        name: /dashboard/i,
      })
      .nth(0)
  );

  await t.click(screen.queryByTestId('incident-instances-link'));

  await validateCheckedState({
    checked: [InstancesPage.Filters.incidents.field],
    unChecked: [InstancesPage.Filters.active.field],
  });

  await t
    .expect(screen.getByTestId('result-count').textContent)
    .eql(`${instancesWithIncidentCount} results found`);
});

test('Select process instances by name', async (t) => {
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

  await validateCheckedState({
    checked: [
      InstancesPage.Filters.active.field,
      InstancesPage.Filters.incidents.field,
    ],
    unChecked: [],
  });

  await t
    .expect(screen.getByTestId('result-count').textContent)
    .eql(`${totalInstanceCount} results found`);
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

  await validateCheckedState({
    checked: [InstancesPage.Filters.incidents.field],
    unChecked: [InstancesPage.Filters.active.field],
  });

  await t
    .expect(screen.getByTestId('result-count').textContent)
    .eql(`${incidentCount} results found`);

  await t
    .expect(InstancesPage.Filters.errorMessage.value.value)
    .eql(incidentMessage);

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

  await validateCheckedState({
    checked: [InstancesPage.Filters.incidents.field],
    unChecked: [InstancesPage.Filters.active.field],
  });

  await t
    .expect(screen.getByTestId('result-count').textContent)
    .eql(`${incidentCount} results found`);

  await t
    .expect(InstancesPage.Filters.errorMessage.value.value)
    .eql(incidentMessage);

  await t.expect(screen.queryByTestId('diagram').exists).ok();
});
