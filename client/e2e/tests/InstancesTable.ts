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
  const instancesList = document.querySelector(
    '[data-testid="process-instances-list"]'
  );

  const rowHeight =
    instancesList?.getElementsByTagName('tr')[0]?.clientHeight ?? 0;

  instancesList?.scrollTo(0, rowHeight * totalInstancesDisplayed);
});

fixture('InstancesTable')
  .page(config.endpoint)
  .before(async (ctx) => {
    ctx.initialData = await setup();
    await wait();
  })
  .beforeEach(async (t) => {
    await t
      .useRole(demoUser)
      .maximizeWindow()
      .click(
        screen.queryByRole('listitem', {
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
      screen.queryByRole('textbox', {
        name: 'Instance Id(s) separated by space or comma',
      }),
      instanceIds.join(),
      {paste: true}
    )
    .expect(
      within(screen.queryByTestId('header-link-filters')).queryByTestId('badge')
        .innerText
    )
    .eql('3', {timeout: 10000});

  const instanceRows = within(
    screen.queryByTestId('instances-list')
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
    .click(screen.queryByRole('button', {name: 'Sort by processName'}))
    .expect(instanceRows.nth(0).innerText)
    .contains('instancesTableProcessA')
    .expect(instanceRows.nth(1).innerText)
    .contains('instancesTableProcessB')
    .expect(instanceRows.nth(2).innerText)
    .contains('instancesTableProcessB');

  await t
    .click(screen.queryByRole('button', {name: 'Sort by processName'}))
    .expect(instanceRows.nth(0).innerText)
    .contains('instancesTableProcessB')
    .expect(instanceRows.nth(1).innerText)
    .contains('instancesTableProcessB')
    .expect(instanceRows.nth(2).innerText)
    .contains('instancesTableProcessA');

  await t
    .click(screen.queryByRole('button', {name: 'Sort by id'}))
    .expect(instanceRows.nth(0).innerText)
    .contains(instanceIds[2])
    .expect(instanceRows.nth(1).innerText)
    .contains(instanceIds[1])
    .expect(instanceRows.nth(2).innerText)
    .contains(instanceIds[0]);

  await t
    .click(screen.queryByRole('button', {name: 'Sort by id'}))
    .expect(instanceRows.nth(0).innerText)
    .contains(instanceIds[0])
    .expect(instanceRows.nth(1).innerText)
    .contains(instanceIds[1])
    .expect(instanceRows.nth(2).innerText)
    .contains(instanceIds[2]);

  await t
    .click(screen.queryByRole('button', {name: 'Sort by processVersion'}))
    .expect(instanceRows.nth(0).innerText)
    .contains('Version 2')
    .expect(instanceRows.nth(1).innerText)
    .contains('Version 1')
    .expect(instanceRows.nth(2).innerText)
    .contains('Version 1');

  await t
    .click(screen.queryByRole('button', {name: 'Sort by processVersion'}))
    .expect(instanceRows.nth(0).innerText)
    .contains('Version 1')
    .expect(instanceRows.nth(1).innerText)
    .contains('Version 1')
    .expect(instanceRows.nth(2).innerText)
    .contains('Version 2');

  await t
    .click(screen.queryByRole('button', {name: 'Sort by startDate'}))
    .expect(instanceRows.nth(0).innerText)
    .contains(instanceIds[2])
    .expect(instanceRows.nth(1).innerText)
    .contains(instanceIds[1])
    .expect(instanceRows.nth(2).innerText)
    .contains(instanceIds[0]);

  await t
    .click(screen.queryByRole('button', {name: 'Sort by startDate'}))
    .expect(instanceRows.nth(0).innerText)
    .contains(instanceIds[0])
    .expect(instanceRows.nth(1).innerText)
    .contains(instanceIds[1])
    .expect(instanceRows.nth(2).innerText)
    .contains(instanceIds[2]);
});

//  TODO: If we have loading indicators for prev/next fetches in the future, we can remove the manual awaits after scrolls
test('Scrolling', async (t) => {
  const {initialData} = t.fixtureCtx;
  const {instancesForInfiniteScroll} = initialData.instances;

  const descendingInstanceIds = instancesForInfiniteScroll
    .map((instance: any) => instance.processInstanceKey)
    .sort((instanceId1: number, instanceId2: number) => {
      return instanceId2 - instanceId1;
    });

  const processCombobox = screen.queryByRole('combobox', {
    name: 'Process',
  });

  await t.click(processCombobox).click(
    within(processCombobox).queryByRole('option', {
      name: 'Process For Infinite Scroll',
    })
  );

  await t.click(screen.queryByRole('button', {name: /Sort by id/}));

  const instanceRows = within(
    screen.queryByTestId('instances-list')
  ).getAllByRole('row');

  await t
    .expect(instanceRows.count)
    .eql(50)
    .expect(instanceRows.nth(0).innerText)
    .contains(descendingInstanceIds[0])
    .expect(instanceRows.nth(49).innerText)
    .contains(descendingInstanceIds[49]);

  // scroll until max stored instances is reached (200)
  await t.scrollIntoView(instanceRows.nth(49));
  await t.expect(instanceRows.count).eql(100);

  await t.scrollIntoView(instanceRows.nth(99));
  await t.expect(instanceRows.count).eql(150);

  await t.scrollIntoView(instanceRows.nth(149));
  await t.expect(instanceRows.count).eql(200);

  await t
    .expect(instanceRows.count)
    .eql(200)
    .expect(instanceRows.nth(0).innerText)
    .contains(descendingInstanceIds[0])
    .expect(instanceRows.nth(199).innerText)
    .contains(descendingInstanceIds[199]);

  await scrollDown(200);
  // await t.scrollIntoView(instanceRows.nth(199)); TODO: OPE-1299 - scrollIntoView does not work correctly after max amount of instances ist reached

  await t
    .expect(instanceRows.count)
    .eql(200)
    .expect(instanceRows.nth(0).innerText)
    .contains(descendingInstanceIds[50])
    .expect(instanceRows.nth(199).innerText)
    .contains(descendingInstanceIds[249]);

  await t.scrollIntoView(instanceRows.nth(0)); // TODO: OPE-1299 - this does not work properly too, it keeps scrolling top but since its already beginning of the list after first scroll, following assertions are passed

  await t
    .expect(instanceRows.count)
    .eql(200)
    .expect(instanceRows.nth(0).innerText)
    .contains(descendingInstanceIds[0])
    .expect(instanceRows.nth(199).innerText)
    .contains(descendingInstanceIds[199]);
});
