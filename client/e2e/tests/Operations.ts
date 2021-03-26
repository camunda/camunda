/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {screen, within} from '@testing-library/testcafe';
import {demoUser} from './utils/Roles';
import {config} from '../config';
import {setup} from './Operations.setup';
import {DATE_REGEX} from './constants';

fixture('Operations')
  .page(config.endpoint)
  .before(async (ctx) => {
    ctx.initialData = await setup();
  })
  .beforeEach(async (t) => {
    await t.useRole(demoUser);
    await t.maximizeWindow();

    await t.navigateTo('/');
    await t.click(screen.getByTestId('header-link-instances'));
  });

test('infinite scrolling', async (t) => {
  await t.click(screen.getByTitle('Expand Operations'));

  await t.expect(screen.getAllByTestId('operations-entry').count).eql(20);

  await t.hover(screen.getAllByTestId('operations-entry').nth(19));

  await t.expect(screen.getAllByTestId('operations-entry').count).eql(40);
});

// This test was skipped, because of OPE-1098, please unskip, when the bug is fixed
test.skip('Retry and Cancel single instance ', async (t) => {
  const {initialData} = t.fixtureCtx;
  const instance = initialData.singleOperationInstance;

  // filter by instance id
  await t.typeText(
    screen.getByRole('textbox', {
      name: 'Instance Id(s) separated by space or comma',
    }),
    instance.processInstanceKey,
    {paste: true}
  );

  // wait for filter to be applied
  await t
    .expect(
      within(screen.getByTestId('instances-list')).getAllByRole('row').count
    )
    .eql(1);

  // retry single instance using operation button
  await t.click(
    screen.getByRole('button', {
      name: `Retry Instance ${instance.processInstanceKey}`,
    })
  );

  // expect spinner to show and disappear
  await t.expect(screen.getByTestId('operation-spinner').exists).ok();
  await t.expect(screen.queryByTestId('operation-spinner').exists).notOk();

  // cancel single instance using operation button
  await t.click(
    screen.getByRole('button', {
      name: `Cancel Instance ${instance.processInstanceKey}`,
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
      screen.findByText('There are no Instances matching this filter set')
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
    .expect(
      instanceRow.getByTestId(`CANCELED-icon-${instance.processInstanceKey}`)
        .exists
    )
    .ok()
    .expect(
      instanceRow.queryByTestId(`ACTIVE-icon-${instance.processInstanceKey}`)
        .exists
    )
    .notOk()
    .expect(instanceRow.getByText(instance.bpmnProcessId).exists)
    .ok()
    .expect(instanceRow.getByText(instance.processInstanceKey).exists)
    .ok();
});

// This test was skipped, because of OPE-1098, please unskip, when the bug is fixed
test.skip('Retry and cancel multiple instances ', async (t) => {
  const {initialData} = t.fixtureCtx;
  const instances = initialData.batchOperationInstances.slice(0, 5);
  const instancesListItems = within(
    screen.getByTestId('operations-list')
  ).getAllByRole('listitem');

  // filter by instance ids
  await t.typeText(
    screen.getByRole('textbox', {
      name: 'Instance Id(s) separated by space or comma',
    }),
    // @ts-ignore I had to use ignore instead of expect-error here because Testcafe would not run the tests with it
    instances.map((instance) => instance.processInstanceKey).join(','),
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
    .ok()
    .expect(screen.getAllByTitle(/has scheduled operations/i).count)
    .eql(instances.length);

  // expect first operation item to have progress bar
  await t
    .expect(
      within(instancesListItems.nth(0)).getByTestId('progress-bar').exists
    )
    .ok();

  // wait for instance to finish in operation list (end time is present, progess bar gone)
  await t
    .expect(within(instancesListItems.nth(0)).queryByText(DATE_REGEX).exists)
    .ok()
    .expect(
      within(instancesListItems.nth(0)).queryByTestId('progress-bar').exists
    )
    .notOk();

  // reset filters
  await t
    .click(screen.getByRole('button', {name: 'Reset filters'}))
    .expect(within(instancesList).getAllByRole('row').count)
    .gt(instances.length);

  // select all instances from operation
  await t
    .click(
      within(instancesListItems.nth(0)).getByText(
        `${instances.length} Instances`
      )
    )
    .expect(within(instancesList).getAllByRole('row').count)
    .eql(instances.length)
    .expect(
      screen.getByRole('textbox', {
        name: 'Operation Id',
      }).value
    )
    .eql(
      await within(instancesListItems.nth(0)).getByTestId('operation-id')
        .innerText
    );

  // check if all instances are shown
  await Promise.all(
    instances.map(
      // @ts-ignore I had to use ignore instead of expect-error here because Testcafe would not run the tests with it
      async (instance) =>
        await t
          .expect(
            within(instancesList).getByText(instance.processInstanceKey).exists
          )
          .ok()
    )
  );

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
      within(screen.getByTestId('menu')).getByRole('button', {name: 'Cancel'})
    )
    .click(screen.getByRole('button', {name: 'Apply'}))
    .expect(screen.findByTestId('operations-list').visible)
    .ok()
    .expect(screen.getAllByTitle(/has scheduled operations/i).count)
    .eql(instances.length);

  // expect first operation item to have progress bar
  await t
    .expect(
      within(instancesListItems.nth(0)).getByTestId('progress-bar').exists
    )
    .ok();

  // expect cancel icon to show for each instance
  await Promise.all(
    // @ts-ignore I had to use ignore instead of expect-error here because Testcafe would not run the tests with it
    instances.map(async (instance) =>
      t
        .expect(
          screen.queryByTestId(`CANCELED-icon-${instance.processInstanceKey}`)
            .exists
        )
        .ok({timeout: 30000})
    )
  );
});
