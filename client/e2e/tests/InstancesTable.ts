/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {screen, within} from '@testing-library/testcafe';
import {demoUser} from './utils/Roles';
import {wait} from './utils/wait';
import {config} from '../config';
import {cmInstanceIdsField, setup} from './InstancesTable.setup';
import {IS_NEW_FILTERS_FORM} from '../../src/modules/feature-flags';
import {setFlyoutTestAttribute} from './utils/setFlyoutTestAttribute';

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
        screen.queryByRole('link', {
          name: /view instances/i,
        })
      );

    if (IS_NEW_FILTERS_FORM) {
      await setFlyoutTestAttribute('processName');
    }
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

  const instanceIdsField = IS_NEW_FILTERS_FORM
    ? cmInstanceIdsField
    : screen.queryByRole('textbox', {
        name: 'Instance Id(s) separated by space or comma',
      });

  await t
    .typeText(instanceIdsField, instanceIds.join(), {paste: true})
    .expect(
      screen.getAllByTestId('filter-panel-header-badge').nth(0).textContent
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

  const processCombobox = IS_NEW_FILTERS_FORM
    ? screen.getByTestId('filter-process-name')
    : screen.queryByRole('combobox', {
        name: 'Process',
      });

  await t.click(processCombobox).click(
    IS_NEW_FILTERS_FORM
      ? within(
          screen.queryByTestId('cm-flyout-process-name').shadowRoot()
        ).queryByText('Process For Infinite Scroll')
      : within(processCombobox).queryByRole('option', {
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
  await t.scrollIntoView(
    screen.getByRole('row', {name: `Instance ${descendingInstanceIds[49]}`})
  );
  await t.expect(instanceRows.count).eql(100);

  await t.scrollIntoView(
    screen.getByRole('row', {name: `Instance ${descendingInstanceIds[99]}`})
  );
  await t.expect(instanceRows.count).eql(150);

  await t.scrollIntoView(
    screen.getByRole('row', {name: `Instance ${descendingInstanceIds[149]}`})
  );
  await t.expect(instanceRows.count).eql(200);

  await t
    .expect(instanceRows.count)
    .eql(200)
    .expect(instanceRows.nth(0).innerText)
    .contains(descendingInstanceIds[0])
    .expect(instanceRows.nth(199).innerText)
    .contains(descendingInstanceIds[199]);

  await t.scrollIntoView(
    screen.getByRole('row', {name: `Instance ${descendingInstanceIds[199]}`})
  );

  await t
    .expect(instanceRows.count)
    .eql(200)
    .expect(instanceRows.nth(0).innerText)
    .contains(descendingInstanceIds[50])
    .expect(instanceRows.nth(199).innerText)
    .contains(descendingInstanceIds[249]);

  await t.scrollIntoView(
    screen.getByRole('row', {name: `Instance ${descendingInstanceIds[50]}`})
  );

  await t
    .expect(instanceRows.count)
    .eql(200)
    .expect(instanceRows.nth(0).innerText)
    .contains(descendingInstanceIds[0])
    .expect(instanceRows.nth(199).innerText)
    .contains(descendingInstanceIds[199]);
});
