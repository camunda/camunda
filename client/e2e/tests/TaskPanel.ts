/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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
    .expect(
      withinExpandedPanel.getByText('There are no Tasks available').exists,
    )
    .ok();

  await t
    .click(withinExpandedPanel.getByRole('combobox'))
    .click(screen.getByText('All open'));

  await t.expect(await getURL()).contains('/?filter=all-open');

  await t
    .expect(
      withinExpandedPanel.queryByText('There are no Tasks available.').exists,
    )
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
