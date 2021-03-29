/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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
    await t.navigateTo(`/instances/${processInstanceId}`);
  });

test('Should select multi instance flow nodes', async (t) => {
  await t.expect(screen.getByText('Instance History').exists).ok();

  const withinInstanceHistory = within(
    screen.queryByTestId('instance-history')
  );
  const selectedInstanceHistoryRows = withinInstanceHistory.getAllByRole(
    'row',
    {
      selected: true,
    }
  );
  const withinIncidentsTable = within(screen.getByTestId('incidents-table'));

  const selectedIncidentsTableRows = withinIncidentsTable.getAllByRole('row', {
    selected: true,
  });

  const withinPopover = within(screen.getByTestId('popover'));

  await t
    .expect(
      withinInstanceHistory.queryByRole('row', {selected: true}).innerText
    )
    .eql('multiInstanceProcess');

  await t
    .click(
      withinInstanceHistory
        .getAllByRole('button', {
          name: 'Unfold Task B (Multi Instance)',
        })
        .nth(0)
    )
    .click(
      withinInstanceHistory
        .getAllByRole('button', {
          name: 'Unfold Task B (Multi Instance)',
        })
        .nth(0)
    )
    .expect(
      withinInstanceHistory.getAllByRole('row', {
        name: 'Task B',
      }).count
    )
    .eql(10);

  await t
    .click(within(screen.getByTestId('diagram')).getByText(/Task B/))
    .expect(selectedInstanceHistoryRows.count)
    .eql(5);

  await Promise.all(
    new Array(5).map((i) =>
      t
        .expect(selectedInstanceHistoryRows.nth(i).textContent)
        .eql('Task B (Multi Instance)')
    )
  );

  await t
    .click(screen.getByRole('button', {name: /view 25 incidents in instance/i}))
    .expect(withinIncidentsTable.findByRole('row', {selected: true}).exists)
    .notOk();

  await t
    .click(withinInstanceHistory.getAllByRole('row', {name: 'Task B'}).nth(0))
    .expect(selectedIncidentsTableRows.count)
    .eql(1)
    .expect(selectedIncidentsTableRows.nth(0).textContent)
    .contains('Task B')
    .expect(selectedInstanceHistoryRows.count)
    .eql(1)
    .expect(selectedInstanceHistoryRows.nth(0).textContent)
    .eql('Task B');

  await t.click(
    screen.getByRole('button', {name: /view 25 incidents in instance/i})
  );

  await t
    .click(withinPopover.getByRole('button', {name: 'Task B'}))
    .expect(selectedInstanceHistoryRows.count)
    .eql(10)
    .expect(withinPopover.getByText(/there are 25 instances/i).exists)
    .ok();

  await Promise.all(
    new Array(10).map((i) =>
      t.expect(selectedInstanceHistoryRows.nth(i).textContent).eql('Task B')
    )
  );

  await t
    .click(screen.getByRole('button', {name: /view 25 incidents in instance/i}))
    .expect(selectedIncidentsTableRows.count)
    .eql(25);

  await t
    .click(withinIncidentsTable.getAllByRole('row', {name: /Task B/}).nth(0))
    .expect(selectedIncidentsTableRows.count)
    .eql(1);

  await t
    .click(screen.getByRole('button', {name: /view 25 incidents in instance/i}))
    .click(withinPopover.getByRole('button', {name: 'Task B (Multi Instance)'}))
    .expect(withinPopover.getByText(/there are 5 instances/i).exists)
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
