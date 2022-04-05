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

fixture('Task Panel')
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
  const withinExpandedPanel = within(screen.getByTestId('expanded-panel'));

  await t
    .click(withinExpandedPanel.getByRole('combobox'))
    .click(screen.getByText('Claimed by me'));

  await t.expect(await getURL()).contains('/?filter=claimed-by-me');
  await t
    .expect(withinExpandedPanel.getByText('No Tasks available').exists)
    .ok();

  await t
    .click(withinExpandedPanel.getByRole('combobox'))
    .click(screen.getByText('All open'));

  await t.expect(await getURL()).contains('/?filter=all-open');

  await t
    .expect(withinExpandedPanel.queryByText('No Tasks available.').exists)
    .notOk()
    .expect(withinExpandedPanel.getByRole('list').exists)
    .ok();
});

test('update task list according to user actions', async (t) => {
  const withinExpandedPanel = within(screen.getByTestId('expanded-panel'));

  await t
    .click(withinExpandedPanel.getByRole('combobox'))
    .click(screen.getByText('Unclaimed'));

  await t.expect(await getURL()).contains('/?filter=unclaimed');

  await t
    .click(
      within(screen.getByTestId('expanded-panel')).getByText(
        'usertask_to_be_claimed',
      ),
    )
    .click(screen.getByRole('button', {name: 'Claim'}));

  await t
    .expect(
      within(screen.getByTestId('expanded-panel')).queryByText(
        'usertask_to_be_claimed',
      ).exists,
    )
    .notOk();

  await t
    .click(withinExpandedPanel.getByRole('combobox'))
    .click(screen.getByText('Claimed by me'));

  await t.expect(await getURL()).contains('/?filter=claimed-by-me');

  await t
    .click(
      within(screen.getByTestId('expanded-panel')).getByText(
        'usertask_to_be_claimed',
      ),
    )
    .click(screen.getByRole('button', {name: 'Complete Task'}));

  await t
    .expect(
      within(screen.getByTestId('expanded-panel')).queryByText(
        'usertask_to_be_claimed',
      ).exists,
    )
    .notOk();

  await t
    .click(withinExpandedPanel.getByRole('combobox'))
    .click(screen.getByText('Completed'));

  await t.expect(await getURL()).contains('/?filter=completed');

  await t
    .expect(
      within(screen.getByTestId('expanded-panel'))
        .getAllByText('usertask_to_be_claimed')
        .nth(0).exists,
    )
    .ok();
});

test('scrolling', async (t) => {
  await t
    .expect(screen.getAllByTestId(/task-/).count)
    .eql(50)
    .expect(screen.getByText('usertask_for_scrolling_1').exists)
    .ok();

  await t.hover(screen.getAllByTestId(/task-/).nth(49));
  await t
    .expect(screen.getAllByTestId(/task-/).count)
    .eql(100)
    .expect(screen.getByText('usertask_for_scrolling_1').exists)
    .ok();

  await t.hover(screen.getAllByTestId(/task-/).nth(99));
  await t
    .expect(screen.getAllByTestId(/task-/).count)
    .eql(150)
    .expect(screen.getByText('usertask_for_scrolling_1').exists)
    .ok();

  await t.hover(screen.getAllByTestId(/task-/).nth(149));
  await t
    .expect(screen.getAllByTestId(/task-/).count)
    .eql(200)
    .expect(screen.getByText('usertask_for_scrolling_1').exists)
    .ok();

  await t.hover(screen.getAllByTestId(/task-/).nth(199));
  await t
    .expect(screen.getByText('usertask_for_scrolling_3').exists)
    .ok()
    .expect(screen.queryByText('usertask_for_scrolling_1').exists)
    .notOk()
    .expect(screen.getAllByTestId(/task-/).count)
    .eql(200);

  await t.hover(screen.getAllByTestId(/task-/).nth(0));
  await t
    .expect(screen.getByText('usertask_for_scrolling_1').exists)
    .ok()
    .expect(screen.queryByText('usertask_for_scrolling_3').exists)
    .notOk()
    .expect(screen.getAllByTestId(/task-/).count)
    .eql(200);
});
