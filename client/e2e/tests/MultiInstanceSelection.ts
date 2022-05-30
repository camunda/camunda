/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {screen, within} from '@testing-library/testcafe';
import {wait} from './utils/wait';
import {demoUser} from './utils/Roles';
import {setup} from './MultiInstanceSelection.setup';

fixture('Multi Instance Flow Node Selection')
  .before(async (ctx) => {
    ctx.initialData = await setup();
    await wait();
  })
  .beforeEach(async (t) => {
    await t.useRole(demoUser);
    await t.maximizeWindow();

    const {
      initialData: {multiInstanceProcessInstance},
    } = t.fixtureCtx;

    const processInstanceId = multiInstanceProcessInstance.processInstanceKey;
    await t.navigateTo(`/processes/${processInstanceId}`);
  });

test('Should select multi instance flow nodes', async (t) => {
  await t.expect(screen.queryByTestId('instance-history').exists).ok();

  const withinInstanceHistory = within(
    screen.queryByTestId('instance-history')
  );
  const selectedInstanceHistoryRows = withinInstanceHistory.queryAllByRole(
    'row',
    {
      selected: true,
    }
  );
  const withinIncidentsTable = within(screen.queryByTestId('incidents-table'));

  const selectedIncidentsTableRows = withinIncidentsTable.queryAllByRole(
    'row',
    {
      selected: true,
    }
  );

  const withinPopover = within(screen.queryByTestId('popover'));

  await t
    .expect(
      withinInstanceHistory.queryByRole('row', {selected: true}).innerText
    )
    .eql('multiInstanceProcess');

  await t
    .click(
      withinInstanceHistory
        .queryAllByRole('button', {
          name: 'Unfold Task B (Multi Instance)',
        })
        .nth(0)
    )
    .click(
      withinInstanceHistory
        .queryAllByRole('button', {
          name: 'Unfold Task B (Multi Instance)',
        })
        .nth(0)
    )
    .expect(
      withinInstanceHistory.queryAllByRole('row', {
        name: 'Task B',
      }).count
    )
    .eql(10);

  await t
    .click(within(screen.queryByTestId('diagram')).queryByText(/Task B/))
    .expect(selectedInstanceHistoryRows.count)
    .eql(5)
    .expect(
      within(screen.queryByTestId('popover')).queryByText(
        /To view details for any of these,.*select one Instance in the Instance History./
      ).exists
    )
    .ok()
    .expect(
      screen.queryByText(
        'To view the Variables, select a single Flow Node Instance in the Instance History.'
      ).exists
    )
    .ok();

  await Promise.all(
    new Array(5).map((i) =>
      t
        .expect(selectedInstanceHistoryRows.nth(i).textContent)
        .eql('Task B (Multi Instance)')
    )
  );

  await t
    .click(
      screen.queryByRole('button', {name: /view 25 incidents in instance/i})
    )
    .expect(withinIncidentsTable.queryByRole('row', {selected: true}).exists)
    .notOk();

  await t
    .click(withinInstanceHistory.queryAllByRole('row', {name: 'Task B'}).nth(0))
    .expect(selectedIncidentsTableRows.count)
    .eql(1)
    .expect(selectedIncidentsTableRows.nth(0).textContent)
    .contains('Task B')
    .expect(selectedInstanceHistoryRows.count)
    .eql(1)
    .expect(selectedInstanceHistoryRows.nth(0).textContent)
    .eql('Task B')
    .expect(
      screen.queryByText(
        'To view the Variables, select a single Flow Node Instance in the Instance History.'
      ).exists
    )
    .notOk();

  await t.click(
    screen.queryByRole('button', {name: /view 25 incidents in instance/i})
  );

  await t
    .click(withinPopover.queryByRole('button', {name: 'Task B'}))
    .expect(selectedInstanceHistoryRows.count)
    .eql(10)
    .expect(withinPopover.queryByText(/there are 25 instances/i).exists)
    .ok();

  await Promise.all(
    new Array(10).map((i) =>
      t.expect(selectedInstanceHistoryRows.nth(i).textContent).eql('Task B')
    )
  );

  await t
    .click(
      screen.queryByRole('button', {name: /view 25 incidents in instance/i})
    )
    .expect(selectedIncidentsTableRows.count)
    .eql(25);

  await t
    .click(withinIncidentsTable.queryAllByText(/Task B/).nth(0))
    .expect(selectedIncidentsTableRows.count)
    .eql(1);

  await t
    .click(
      screen.queryByRole('button', {name: /view 25 incidents in instance/i})
    )
    .click(
      withinPopover.queryByRole('button', {name: 'Task B (Multi Instance)'})
    )
    .expect(withinPopover.queryByText(/there are 5 instances/i).exists)
    .ok()
    .expect(selectedInstanceHistoryRows.count)
    .eql(5);

  await Promise.all(
    new Array(5).map((i) =>
      t
        .expect(selectedInstanceHistoryRows.nth(i).textContent)
        .eql('Task B (Multi Instance)')
    )
  );
});
