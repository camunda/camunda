/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {config} from '../config';
import {setup} from './TaskPanel.setup';
import {screen, within} from '@testing-library/testcafe';
import {demoUser} from './utils/Roles';
import {ClientFunction} from 'testcafe';
import {wait} from './utils/common';

fixture('Left panel')
  .page(config.endpoint)
  .before(async () => {
    await setup();
    await wait();
  })
  .beforeEach(async (t) => {
    await t.useRole(demoUser);
  });

const getURL = ClientFunction(() => window.location.href);

test('filter selection', async (t) => {
  const withinExpandedPanel = within(screen.getByLabelText('Left panel'));

  await t.expect(screen.queryAllByText('Some user activity').exists).ok();

  await t
    .click(screen.getByRole('combobox', {name: 'Filter options'}))
    .click(screen.getByText('Assigned to me'));

  await t.expect(await getURL()).contains('/?filter=assigned-to-me');

  await t
    .expect(withinExpandedPanel.getByText('No tasks found').exists)
    .ok()
    .click(withinExpandedPanel.getByText('Assigned to me'))
    .click(screen.getByText('All open'));

  await t.expect(await getURL()).contains('/?filter=all-open');

  await t
    .expect(withinExpandedPanel.queryByText('No tasks found').exists)
    .notOk()
    .expect(screen.queryAllByText('Some user activity').count)
    .eql(50);
});

test('update task list according to user actions', async (t) => {
  const withinExpandedPanel = within(screen.getByLabelText('Left panel'));

  await t
    .click(withinExpandedPanel.getByRole('combobox', {name: 'Filter options'}))
    .click(screen.getByRole('option', {name: /unassigned/i}));

  await t.expect(await getURL()).contains('?filter=unassigned');

  await t
    .click(withinExpandedPanel.getByText('usertask_to_be_assigned'))
    .click(screen.getByRole('button', {name: 'Assign to me'}));

  await t
    .expect(withinExpandedPanel.queryByText('usertask_to_be_assigned').exists)
    .notOk();

  await t
    .click(withinExpandedPanel.getByRole('combobox', {name: 'Filter options'}))
    .click(screen.getByRole('option', {name: /assigned to me/i}));

  await t.expect(await getURL()).contains('?filter=assigned-to-me');

  await t
    .click(withinExpandedPanel.queryByText('usertask_to_be_assigned'))
    .click(screen.getByRole('button', {name: 'Complete Task'}));

  await t
    .expect(withinExpandedPanel.queryByText('usertask_to_be_assigned').exists)
    .notOk();

  await t
    .click(withinExpandedPanel.getByRole('combobox', {name: 'Filter options'}))
    .click(screen.queryByRole('option', {name: /completed/i}));

  await t.expect(await getURL()).contains('?filter=completed');

  await t
    .expect(
      within(screen.getByLabelText('Left panel'))
        .getAllByText('usertask_to_be_assigned')
        .nth(0).exists,
    )
    .ok();
});

test('scrolling', async (t) => {
  await t
    .expect(screen.queryAllByText('Created').count)
    .eql(50)
    .expect(screen.getByText('usertask_for_scrolling_1').exists)
    .ok();

  await t.hover(screen.queryAllByText('Created').nth(49));
  await t
    .expect(screen.queryAllByText('Created').count)
    .eql(100)
    .expect(screen.getByText('usertask_for_scrolling_1').exists)
    .ok();

  await t.hover(screen.queryAllByText('Created').nth(99));
  await t
    .expect(screen.queryAllByText('Created').count)
    .eql(150)
    .expect(screen.getByText('usertask_for_scrolling_1').exists)
    .ok();

  await t.hover(screen.queryAllByText('Created').nth(149));
  await t
    .expect(screen.queryAllByText('Created').count)
    .eql(200)
    .expect(screen.getByText('usertask_for_scrolling_1').exists)
    .ok();

  await t.hover(screen.queryAllByText('Created').nth(199));
  await t
    .expect(screen.getByText('usertask_for_scrolling_3').exists)
    .ok()
    .expect(screen.queryByText('usertask_for_scrolling_1').exists)
    .notOk()
    .expect(screen.queryAllByText('Created').count)
    .eql(200);

  await t.hover(screen.queryAllByText('Some user activity').nth(0));
  await t
    .expect(screen.getByText('usertask_for_scrolling_1').exists)
    .ok()
    .expect(screen.queryByText('usertask_for_scrolling_3').exists)
    .notOk()
    .expect(screen.queryAllByText('Created').count)
    .eql(200);
});
