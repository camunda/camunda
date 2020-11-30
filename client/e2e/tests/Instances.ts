/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {config} from '../config';
import {setup} from './Instances.setup';
import {demoUser} from './utils/Roles';
import {wait} from './utils/wait';
import {getPathname} from './utils/getPathname';
import {screen, within} from '@testing-library/testcafe';

fixture('Instances')
  .page(config.endpoint)
  .before(async (ctx) => {
    ctx.initialData = await setup();
    await wait();
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
    .expect(screen.getByText('There is no Workflow selected').exists)
    .ok()
    .expect(
      screen.getByText(
        'To see a Diagram, select a Workflow in the Filters panel'
      ).exists
    )
    .ok();

  await t.typeText(
    screen.getByRole('textbox', {
      name: /instance id\(s\) separated by space or comma/i,
    }),
    `${initialData.instanceWithoutAnIncident.workflowInstanceKey}, ${initialData.instanceWithAnIncident.workflowInstanceKey}`
  );

  const withinInstancesList = within(screen.getByTestId('instances-list'));
  await t.expect(withinInstancesList.getAllByRole('row').count).eql(2);

  await t
    .expect(
      withinInstancesList.getByTestId(
        `INCIDENT-icon-${initialData.instanceWithAnIncident.workflowInstanceKey}`
      ).exists
    )
    .ok()
    .expect(
      withinInstancesList.getByTestId(
        `ACTIVE-icon-${initialData.instanceWithoutAnIncident.workflowInstanceKey}`
      ).exists
    )
    .ok();
});

test('Select flow node in diagram', async (t) => {
  const {initialData} = t.fixtureCtx;
  const instance = initialData.instanceWithoutAnIncident;

  await t.click(screen.getByRole('listitem', {name: 'Running Instances'}));

  // Filter by Instance ID
  await t.typeText(
    screen.getByRole('textbox', {
      name: 'Instance Id(s) separated by space or comma',
    }),
    instance.workflowInstanceKey,
    {paste: true}
  );

  const workflowCombobox = screen.getByRole('combobox', {
    name: 'Workflow',
  });

  // Select "Order Process"
  await t.click(workflowCombobox).click(
    within(workflowCombobox).getByRole('option', {
      name: 'Order process',
    })
  );

  // Select "Ship Articles" flow node
  const shipArticlesTaskId = 'shipArticles';
  await t
    .click(within(screen.getByTestId('diagram')).getByText('Ship Articles'))
    .expect(screen.getByRole('combobox', {name: 'Flow Node'}).value)
    .eql(shipArticlesTaskId)
    .expect(
      screen.queryByText('There are no Instances matching this filter set')
        .exists
    )
    .ok()
    .expect(getPathname())
    .eql(
      `#/instances?filter={${encodeURI(
        `"active":true,"incidents":true,"ids":"${instance.workflowInstanceKey}","version":"1","workflow":"orderProcess","activityId":"${shipArticlesTaskId}"`
      )}}&name=${encodeURI('"Order process"')}`
    );

  // Select "Check Payment" flow node
  const checkPaymentTaskId = 'checkPayment';
  await t
    .click(within(screen.getByTestId('diagram')).getByText('Check payment'))
    .expect(screen.getByRole('combobox', {name: 'Flow Node'}).value)
    .eql(checkPaymentTaskId)
    .expect(
      within(screen.getByTestId('instances-list')).getAllByRole('row').count
    )
    .eql(1)
    .expect(getPathname())
    .eql(
      `#/instances?filter={${encodeURI(
        `"active":true,"incidents":true,"ids":"${instance.workflowInstanceKey}","version":"1","workflow":"orderProcess","activityId":"${checkPaymentTaskId}"`
      )}}&name=${encodeURI('"Order process"')}`
    );
});
