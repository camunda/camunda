/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {screen, within} from '@testing-library/testcafe';
import {demoUser} from './utils/Roles';
import {wait} from './utils/wait';
import {config} from '../config';
import {setup} from './ProcessInstancesTable.setup';
import {setProcessesFlyoutTestAttributeLegacy} from './utils/setFlyoutTestAttribute';
import {displayOptionalFilter} from './utils/displayOptionalFilter';
import {processesPage as ProcessesPage} from './PageModels/Processes';
import {IS_COMBOBOX_ENABLED} from '../../src/modules/feature-flags';

fixture('Process Instances Table')
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
          name: /processes/i,
        })
      );

    if (!IS_COMBOBOX_ENABLED) {
      await setProcessesFlyoutTestAttributeLegacy('processName');
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

  await displayOptionalFilter('Process Instance Key(s)');

  await ProcessesPage.typeText(
    ProcessesPage.Filters.instanceIds.field,
    instanceIds.join(),
    {
      paste: true,
    }
  );

  await t.expect(screen.queryByText(/3 results found/).exists).ok();

  const instanceRows = within(screen.queryByTestId('data-list')).getAllByRole(
    'row'
  );

  // test default Start Date sorting

  await t
    .expect(instanceRows.nth(0).innerText)
    .contains(instanceIds[2])
    .expect(instanceRows.nth(1).innerText)
    .contains(instanceIds[1])
    .expect(instanceRows.nth(2).innerText)
    .contains(instanceIds[0]);

  await t
    .click(screen.queryByRole('button', {name: 'Sort by Start Date'}))
    .expect(instanceRows.nth(0).innerText)
    .contains(instanceIds[0])
    .expect(instanceRows.nth(1).innerText)
    .contains(instanceIds[1])
    .expect(instanceRows.nth(2).innerText)
    .contains(instanceIds[2]);

  await t
    .click(screen.queryByRole('button', {name: 'Sort by Start Date'}))
    .expect(instanceRows.nth(0).innerText)
    .contains(instanceIds[2])
    .expect(instanceRows.nth(1).innerText)
    .contains(instanceIds[1])
    .expect(instanceRows.nth(2).innerText)
    .contains(instanceIds[0]);

  await t
    .click(screen.queryByRole('button', {name: 'Sort by Process Instance Key'}))
    .expect(instanceRows.nth(0).innerText)
    .contains(instanceIds[2])
    .expect(instanceRows.nth(1).innerText)
    .contains(instanceIds[1])
    .expect(instanceRows.nth(2).innerText)
    .contains(instanceIds[0]);

  await t
    .click(screen.queryByRole('button', {name: 'Sort by Process Instance Key'}))
    .expect(instanceRows.nth(0).innerText)
    .contains(instanceIds[0])
    .expect(instanceRows.nth(1).innerText)
    .contains(instanceIds[1])
    .expect(instanceRows.nth(2).innerText)
    .contains(instanceIds[2]);

  await t
    .click(screen.queryByRole('button', {name: 'Sort by Version'}))
    .expect(instanceRows.nth(0).innerText)
    .contains(instanceIds[2])
    .expect(instanceRows.nth(1).innerText)
    .contains(instanceIds[0])
    .expect(instanceRows.nth(2).innerText)
    .contains(instanceIds[1]);

  await t
    .click(screen.queryByRole('button', {name: 'Sort by Version'}))
    .expect(instanceRows.nth(0).innerText)
    .contains(instanceIds[0])
    .expect(instanceRows.nth(1).innerText)
    .contains(instanceIds[1])
    .expect(instanceRows.nth(2).innerText)
    .contains(instanceIds[2]);

  await t
    .click(screen.queryByRole('button', {name: 'Sort by Name'}))
    .expect(instanceRows.nth(0).innerText)
    .contains('instancesTableProcessB')
    .expect(instanceRows.nth(1).innerText)
    .contains('instancesTableProcessB')
    .expect(instanceRows.nth(2).innerText)
    .contains('instancesTableProcessA');

  await t
    .click(screen.queryByRole('button', {name: 'Sort by Name'}))
    .expect(instanceRows.nth(0).innerText)
    .contains('instancesTableProcessA')
    .expect(instanceRows.nth(1).innerText)
    .contains('instancesTableProcessB')
    .expect(instanceRows.nth(2).innerText)
    .contains('instancesTableProcessB');
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

  await t.click(ProcessesPage.Filters.processName.field);
  await ProcessesPage.selectProcess('Process For Infinite Scroll');

  await t.click(
    screen.queryByRole('button', {name: /Sort by Process Instance Key/})
  );

  const instanceRows = within(screen.queryByTestId('data-list')).getAllByRole(
    'row'
  );

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
