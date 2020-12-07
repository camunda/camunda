/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {config} from '../config';
import {setup} from './TaskDetails.setup';
import {screen, within} from '@testing-library/testcafe';
import {demoUser} from './utils/Roles';
import {wait} from './utils/common';
import {ClientFunction} from 'testcafe';

fixture('Task Details')
  .page(config.endpoint)
  .before(async () => {
    await setup();
    await wait();
  })
  .beforeEach(async (t) => {
    await t.useRole(demoUser);
  });

const getURL = ClientFunction(() => window.location.href);

test('load task details when a task is selected', async (t) => {
  const withinExpandedPanel = within(screen.getByTestId('expanded-panel'));

  await t.click(
    withinExpandedPanel.getAllByText('usertask_to_be_completed').nth(0),
  );

  await t.expect(screen.queryByTestId('details-table').exists).ok();

  const withinDetailsTable = within(screen.getByTestId('details-table'));

  await t
    .expect(withinDetailsTable.getByRole('columnheader', {name: 'Name'}).exists)
    .ok()
    .expect(
      withinDetailsTable.getByRole('cell', {name: 'Some user activity'}).exists,
    )
    .ok();

  await t
    .expect(
      withinDetailsTable.getByRole('columnheader', {name: 'Workflow'}).exists,
    )
    .ok()
    .expect(
      withinDetailsTable.getByRole('cell', {name: 'usertask_to_be_completed'})
        .exists,
    )
    .ok();

  await t
    .expect(
      withinDetailsTable.getByRole('columnheader', {name: 'Creation Time'})
        .exists,
    )
    .ok();

  await t
    .expect(
      withinDetailsTable.getByRole('columnheader', {name: 'Assignee'}).exists,
    )
    .ok()
    .expect(withinDetailsTable.getByText('--').exists)
    .ok()
    .expect(withinDetailsTable.getByRole('button', {name: 'Claim'}).exists)
    .ok();
});

test('claim and unclaim task', async (t) => {
  const withinExpandedPanel = within(screen.getByTestId('expanded-panel'));

  await t.click(
    withinExpandedPanel.getAllByText('usertask_to_be_completed').nth(0),
  );

  await t.expect(screen.queryByTestId('details-table').exists).ok();

  const withinDetailsTable = within(screen.getByTestId('details-table'));
  await t
    .expect(withinDetailsTable.getByRole('button', {name: 'Claim'}).exists)
    .ok()
    .expect(screen.queryByRole('button', {name: 'Complete Task'}).exists)
    .notOk();

  await t
    .click(withinDetailsTable.getByRole('button', {name: 'Claim'}))
    .expect(withinDetailsTable.getByText('Demo User').exists)
    .ok()
    .expect(withinDetailsTable.getByRole('button', {name: 'Unclaim'}).exists)
    .ok()
    .expect(screen.getByRole('button', {name: 'Complete Task'}).exists)
    .ok();

  await t
    .click(withinDetailsTable.getByRole('button', {name: 'Unclaim'}))
    .expect(withinDetailsTable.getByRole('button', {name: 'Claim'}).exists)
    .ok()
    .expect(withinDetailsTable.getByText('--').exists)
    .ok()
    .expect(screen.queryByRole('button', {name: 'Complete Task'}).exists)
    .notOk();
});

test('complete task', async (t) => {
  const withinExpandedPanel = within(screen.getByTestId('expanded-panel'));

  await t.click(withinExpandedPanel.getByText('usertask_to_be_completed'));

  await t.expect(screen.queryByTestId('details-table').exists).ok();

  const withinDetailsTable = within(screen.getByTestId('details-table'));

  await t
    .expect(
      withinDetailsTable.queryByRole('columnheader', {name: 'Completion Time'})
        .exists,
    )
    .notOk();

  const currentUrl = await getURL();

  await t
    .click(withinDetailsTable.getByRole('button', {name: 'Claim'}))
    .click(screen.getByRole('button', {name: 'Complete Task'}));

  await t
    .expect(screen.getByText('Select a Task to see the details').exists)
    .ok();

  await t.navigateTo(currentUrl);

  await t.expect(screen.queryByTestId('details-table').exists).ok();

  await t
    .expect(
      withinDetailsTable.getByRole('columnheader', {name: 'Completion Time'})
        .exists,
    )
    .ok();
});
