/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {screen, within} from '@testing-library/testcafe';
import {demoUser} from './utils/Roles';
import {wait} from './utils/wait';
import {config} from '../config';
import {setup} from './Operations.setup';
import {DATE_REGEX, DEFAULT_TIMEOUT} from './constants';

fixture('Operations')
  .page(config.endpoint)
  .before(async (ctx) => {
    ctx.initialData = await setup();
    await wait(DEFAULT_TIMEOUT);
  })
  .beforeEach(async (t) => {
    await t.useRole(demoUser);
    await t.maximizeWindow();

    await t.navigateTo('/');
    await t.click(screen.getByTestId('header-link-instances'));
  });

test('Cancel single instance ', async (t) => {
  const {initialData} = t.fixtureCtx;
  const [instance] = initialData.instancesToCancel;

  // filter by instance id
  await t.typeText(
    screen.getByRole('textbox', {
      name: 'Instance Id(s) separated by space or comma',
    }),
    instance.workflowInstanceKey,
    {paste: true}
  );

  // wait for filter to be applied
  await t
    .expect(
      within(screen.getByTestId('instances-list')).getAllByRole('row').count
    )
    .eql(1);

  // cancel single instance using operation button
  await t.click(
    screen.getByRole('button', {
      name: `Cancel Instance ${instance.workflowInstanceKey}`,
    })
  );

  await t
    .expect(screen.getByTestId('operations-list').visible)
    .notOk()
    .click(screen.getByRole('button', {name: 'Expand Operations'}))
    .expect(screen.findByTestId('operations-list').visible)
    .ok();

  const operationItem = within(screen.getByTestId('operations-list'))
    .getAllByRole('listitem')
    .nth(0);
  const operationId = await within(operationItem).getByTestId('operation-id')
    .innerText;
  await t.expect(within(operationItem).getByText('Cancel').exists).ok();

  // wait for instance to disappear from instances list
  await t
    .expect(
      screen.findByText('There are no instances matching this filter set.')
        .exists
    )
    .ok();

  // wait for instance to finish in operation list (end time is present)
  await t.expect(within(operationItem).queryByText(DATE_REGEX).exists).ok();

  await t.click(within(operationItem).getByText('1 Instance'));

  // wait for filter to be applied
  await t
    .expect(
      within(screen.getByTestId('instances-list')).getAllByRole('row').count
    )
    .eql(1);

  // expect operation id filter to be set
  await t
    .expect(
      screen.getByRole('textbox', {
        name: 'Operation Id',
      }).value
    )
    .eql(operationId);

  const instanceRow = within(
    within(screen.getByTestId('instances-list')).getAllByRole('row').nth(0)
  );
  await t
    .expect(instanceRow.getByTestId('CANCELED-icon').exists)
    .ok()
    .expect(instanceRow.queryByTestId('ACTIVE-icon').exists)
    .notOk()
    .expect(instanceRow.getByText(instance.bpmnProcessId).exists)
    .ok()
    .expect(instanceRow.getByText(instance.workflowInstanceKey).exists)
    .ok();
});

test('Retry multiple instances ', async (t) => {
  const {initialData} = t.fixtureCtx;
  const instances = initialData.instancesToRetry.slice(0, 5);

  // filter by instance ids
  await t.typeText(
    screen.getByRole('textbox', {
      name: 'Instance Id(s) separated by space or comma',
    }),
    instances.map((instance) => instance.workflowInstanceKey).join(','),
    {paste: true}
  );

  const instancesList = screen.getByTestId('instances-list');

  // wait for the filter to be applied
  await t
    .expect(within(instancesList).getAllByRole('row').count)
    .eql(instances.length);

  await t.click(
    screen.getByRole('checkbox', {
      name: 'Select all instances',
    })
  );

  await t.click(
    screen.getByRole('button', {
      name: `Apply Operation on ${instances.length} Instances...`,
    })
  );

  await t
    .click(
      within(screen.getByTestId('menu')).getByRole('button', {name: 'Retry'})
    )
    .expect(screen.getByTestId('operations-list').visible)
    .notOk()
    .click(screen.getByRole('button', {name: 'Apply'}))
    .expect(screen.findByTestId('operations-list').visible)
    .ok();

  // get first item in operation panel
  const operationItem = within(screen.getByTestId('operations-list'))
    .getAllByRole('listitem')
    .nth(0);
  const operationId = await within(operationItem).getByTestId('operation-id')
    .innerText;

  // expect first operation item to have progress bar
  await t.expect(within(operationItem).getByTestId('progress-bar').exists).ok();

  // wait for instance to finish in operation list (end time is present, progess bar gone)
  await t
    .expect(within(operationItem).queryByText(DATE_REGEX).exists)
    .ok()
    .expect(within(operationItem).queryByTestId('progress-bar').exists)
    .notOk();

  // reset filters
  await t
    .click(screen.getByRole('button', {name: 'Reset filters'}))
    .expect(within(instancesList).getAllByRole('row').count)
    .gt(instances.length);

  // select all instances from operation
  await t
    .click(within(operationItem).getByText('5 Instances'))
    .expect(within(instancesList).getAllByRole('row').count)
    .eql(instances.length)
    .expect(
      screen.getByRole('textbox', {
        name: 'Operation Id',
      }).value
    )
    .eql(operationId);

  // check if all instances are shown
  await Promise.all(
    instances.map(
      async (instance) =>
        await t
          .expect(
            within(instancesList).getByText(instance.workflowInstanceKey).exists
          )
          .ok()
    )
  );
});
