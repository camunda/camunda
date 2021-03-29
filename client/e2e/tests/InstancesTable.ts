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
import {ClientFunction} from 'testcafe';

const scrollDown = ClientFunction((totalInstancesDisplayed) => {
  const instancesList = document.getElementById('scrollable-list');

  const rowHeight =
    instancesList?.getElementsByTagName('tr')[0]?.clientHeight ?? 0;

  instancesList?.scrollTo(0, rowHeight * totalInstancesDisplayed);
});

const scrollUp = ClientFunction(() => {
  const instancesList = document.getElementById('scrollable-list');

  instancesList?.scrollTo(0, 0);
});

fixture('InstancesTable')
  .page(config.endpoint)
  .before(async (ctx) => {
    ctx.initialData = await setup();
    await wait();
  })

  .beforeEach(async (t) => {
    await t.useRole(demoUser);
    await t.maximizeWindow();
    await t.click(
      screen.getByRole('listitem', {
        name: /running instances/i,
      })
    );
  });

test('Sorting', async (t) => {
  const {initialData} = t.fixtureCtx;
  const {instances} = initialData;

  // pick one instance from each process
  const instanceIds = [
    instances.processA[0].processInstanceKey,
    instances.processB_v_1[0].processInstanceKey,
    instances.processB_v_2[0].processInstanceKey,
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

  // test default process sorting
  await t
    .expect(instanceRows.nth(0).innerText)
    .contains('instancesTableProcessB')
    .expect(instanceRows.nth(1).innerText)
    .contains('instancesTableProcessB')
    .expect(instanceRows.nth(2).innerText)
    .contains('instancesTableProcessA');

  await t
    .click(screen.getByRole('button', {name: 'Sort by processName'}))
    .expect(instanceRows.nth(0).innerText)
    .contains('instancesTableProcessA')
    .expect(instanceRows.nth(1).innerText)
    .contains('instancesTableProcessB')
    .expect(instanceRows.nth(2).innerText)
    .contains('instancesTableProcessB');

  await t
    .click(screen.getByRole('button', {name: 'Sort by processName'}))
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
    .click(screen.getByRole('button', {name: 'Sort by processVersion'}))
    .expect(instanceRows.nth(0).innerText)
    .contains('Version 2')
    .expect(instanceRows.nth(1).innerText)
    .contains('Version 1')
    .expect(instanceRows.nth(2).innerText)
    .contains('Version 1');

  await t
    .click(screen.getByRole('button', {name: 'Sort by processVersion'}))
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

//  TODO: If we have loading indicators for prev/next fetches in the future, we can remove the manual awaits after scrolls
test('Scrolling', async (t) => {
  let totalInstancesDisplayed = 50;

  const {initialData} = t.fixtureCtx;
  const {instancesForInfiniteScroll} = initialData.instances;

  const descendingInstanceIds = instancesForInfiniteScroll
    .map((instance: any) => instance.processInstanceKey)
    .sort((instanceId1: number, instanceId2: number) => {
      return instanceId2 - instanceId1;
    });

  const processCombobox = screen.getByRole('combobox', {
    name: 'Process',
  });

  await t.click(processCombobox).click(
    within(processCombobox).getByRole('option', {
      name: 'Process For Infinite Scroll',
    })
  );

  await t.click(screen.getByRole('button', {name: /Sort by id/}));

  const instanceRows = within(
    screen.getByTestId('instances-list')
  ).getAllByRole('row');

  let totalRowCount = await instanceRows.count;
  let firstVisibleRow = await instanceRows.nth(0).innerText;
  let lastVisibleRow = await instanceRows.nth(totalRowCount - 1).innerText;

  await t
    .expect(totalRowCount)
    .eql(50)
    .expect(firstVisibleRow)
    .contains(descendingInstanceIds[0])
    .expect(lastVisibleRow)
    .contains(descendingInstanceIds[49]);

  // scroll until max stored instances is reached (200)
  await scrollDown(totalInstancesDisplayed);
  totalInstancesDisplayed += 50;
  await t.expect(instanceRows.count).eql(totalInstancesDisplayed);

  await scrollDown(totalInstancesDisplayed);
  totalInstancesDisplayed += 50;
  await t.expect(instanceRows.count).eql(totalInstancesDisplayed);

  await scrollDown(totalInstancesDisplayed);
  totalInstancesDisplayed += 50;
  await t.expect(instanceRows.count).eql(totalInstancesDisplayed);

  totalRowCount = await instanceRows.count;
  firstVisibleRow = await instanceRows.nth(0).innerText;
  lastVisibleRow = await instanceRows.nth(totalRowCount - 1).innerText;

  await t
    .expect(totalRowCount)
    .eql(200)
    .expect(firstVisibleRow)
    .contains(descendingInstanceIds[0])
    .expect(lastVisibleRow)
    .contains(descendingInstanceIds[199]);

  await scrollDown(totalInstancesDisplayed);
  totalInstancesDisplayed += 50;
  await t.wait(2000);

  totalRowCount = await instanceRows.count;
  firstVisibleRow = await instanceRows.nth(0).innerText;
  lastVisibleRow = await instanceRows.nth(totalRowCount - 1).innerText;
  await t
    .expect(totalRowCount)
    .eql(200)
    .expect(firstVisibleRow)
    .contains(descendingInstanceIds[50])
    .expect(lastVisibleRow)
    .contains(descendingInstanceIds[249]);

  await scrollUp();
  await t.wait(2000);

  totalRowCount = await instanceRows.count;
  firstVisibleRow = await instanceRows.nth(0).innerText;
  lastVisibleRow = await instanceRows.nth(totalRowCount - 1).innerText;
  await t
    .expect(totalRowCount)
    .eql(200)
    .expect(firstVisibleRow)
    .contains(descendingInstanceIds[0])
    .expect(lastVisibleRow)
    .contains(descendingInstanceIds[199]);
});
