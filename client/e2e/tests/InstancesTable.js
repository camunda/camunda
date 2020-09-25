/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {screen, within} from '@testing-library/testcafe';
import {demoUser} from './utils/Roles';
import {wait} from './utils/wait';
import {config} from '../config';
import {setup} from './InstancesTable.setup';

fixture('InstancesTable')
  .page(config.endpoint)
  .before(async (ctx) => {
    ctx.initialData = await setup();
    await wait();
  })

  .beforeEach(async (t) => {
    await t.useRole(demoUser);
    await t.maximizeWindow();
    await t.navigateTo(`${config.endpoint}/#/instances`);
  });

const isButtonDisabled = (name) => {
  return screen.getByRole('button', {name}).hasAttribute('disabled');
};

test('Pagination ', async (t) => {
  const {initialData} = t.fixtureCtx;
  const {instances} = initialData;

  // filter by instance ids
  await t.typeText(
    screen.getByRole('textbox', {
      name: 'Instance Id(s) separated by space or comma',
    }),
    instances.processA
      .map(({workflowInstanceKey}) => workflowInstanceKey)
      .join(),
    {paste: true}
  );

  await t
    .expect(
      within(screen.getByTestId('header-link-filters')).getByTestId('badge')
        .innerText
    )
    .eql(instances.processA.length.toString());

  const instancesPerPage = await within(
    screen.getByTestId('instances-list')
  ).getAllByRole('row').count;

  const pageCount = Math.ceil(instances.processA.length / instancesPerPage);

  await t
    .expect(screen.getAllByRole('button', {name: /^Page/}).count)
    .eql(pageCount);

  await t
    .expect(isButtonDisabled('First page'))
    .ok()
    .expect(isButtonDisabled('Previous page'))
    .ok()
    .expect(isButtonDisabled('Next page'))
    .notOk()
    .expect(isButtonDisabled('Last page'))
    .notOk();

  // go to page with preceding and following page(s)
  await t
    .click(screen.getByRole('button', {name: 'Page 2'}))
    .expect(isButtonDisabled('First page'))
    .notOk()
    .expect(isButtonDisabled('Previous page'))
    .notOk()
    .expect(isButtonDisabled('Next page'))
    .notOk()
    .expect(isButtonDisabled('Last page'))
    .notOk();

  // go to last page
  await t
    .click(screen.getByRole('button', {name: `Page ${pageCount}`}))
    .expect(isButtonDisabled('First page'))
    .notOk()
    .expect(isButtonDisabled('Previous page'))
    .notOk()
    .expect(isButtonDisabled('Next page'))
    .ok()
    .expect(isButtonDisabled('Last page'))
    .ok();
});

test('Sorting', async (t) => {
  const {initialData} = t.fixtureCtx;
  const {instances} = initialData;

  // pick one instance from each workflow
  const instanceIds = [
    instances.processA[0].workflowInstanceKey,
    instances.processB_v_1[0].workflowInstanceKey,
    instances.processB_v_2[0].workflowInstanceKey,
  ].sort();

  await t
    .typeText(
      screen.getByRole('textbox', {
        name: 'Instance Id(s) separated by space or comma',
      }),
      instanceIds.join(),
      {paste: true}
    )
    .expect(
      within(screen.getByTestId('header-link-filters')).getByTestId('badge')
        .innerText
    )
    .eql('3', {timeout: 10000});

  const instanceRows = within(
    screen.getByTestId('instances-list')
  ).getAllByRole('row');

  // test default workflow sorting
  await t
    .expect(instanceRows.nth(0).innerText)
    .contains('instancesTableProcessB')
    .expect(instanceRows.nth(1).innerText)
    .contains('instancesTableProcessB')
    .expect(instanceRows.nth(2).innerText)
    .contains('instancesTableProcessA');

  await t
    .click(screen.getByRole('button', {name: 'Sort by workflowName'}))
    .expect(instanceRows.nth(0).innerText)
    .contains('instancesTableProcessA')
    .expect(instanceRows.nth(1).innerText)
    .contains('instancesTableProcessB')
    .expect(instanceRows.nth(2).innerText)
    .contains('instancesTableProcessB');

  await t
    .click(screen.getByRole('button', {name: 'Sort by workflowName'}))
    .expect(instanceRows.nth(0).innerText)
    .contains('instancesTableProcessB')
    .expect(instanceRows.nth(1).innerText)
    .contains('instancesTableProcessB')
    .expect(instanceRows.nth(2).innerText)
    .contains('instancesTableProcessA');

  await t
    .click(screen.getByRole('button', {name: 'Sort by id'}))
    .expect(instanceRows.nth(0).innerText)
    .contains(instanceIds[2])
    .expect(instanceRows.nth(1).innerText)
    .contains(instanceIds[1])
    .expect(instanceRows.nth(2).innerText)
    .contains(instanceIds[0]);

  await t
    .click(screen.getByRole('button', {name: 'Sort by id'}))
    .expect(instanceRows.nth(0).innerText)
    .contains(instanceIds[0])
    .expect(instanceRows.nth(1).innerText)
    .contains(instanceIds[1])
    .expect(instanceRows.nth(2).innerText)
    .contains(instanceIds[2]);

  await t
    .click(screen.getByRole('button', {name: 'Sort by workflowVersion'}))
    .expect(instanceRows.nth(0).innerText)
    .contains('Version 2')
    .expect(instanceRows.nth(1).innerText)
    .contains('Version 1')
    .expect(instanceRows.nth(2).innerText)
    .contains('Version 1');

  await t
    .click(screen.getByRole('button', {name: 'Sort by workflowVersion'}))
    .expect(instanceRows.nth(0).innerText)
    .contains('Version 1')
    .expect(instanceRows.nth(1).innerText)
    .contains('Version 1')
    .expect(instanceRows.nth(2).innerText)
    .contains('Version 2');

  await t
    .click(screen.getByRole('button', {name: 'Sort by startDate'}))
    .expect(instanceRows.nth(0).innerText)
    .contains(instanceIds[2])
    .expect(instanceRows.nth(1).innerText)
    .contains(instanceIds[1])
    .expect(instanceRows.nth(2).innerText)
    .contains(instanceIds[0]);

  await t
    .click(screen.getByRole('button', {name: 'Sort by startDate'}))
    .expect(instanceRows.nth(0).innerText)
    .contains(instanceIds[0])
    .expect(instanceRows.nth(1).innerText)
    .contains(instanceIds[1])
    .expect(instanceRows.nth(2).innerText)
    .contains(instanceIds[2]);
});
