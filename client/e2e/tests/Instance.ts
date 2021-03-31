/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {config} from '../config';
import {setup} from './Instance.setup';
import {demoUser} from './utils/Roles';
import {wait} from './utils/wait';
import {screen, within} from '@testing-library/testcafe';
import {DATE_REGEX} from './constants';

fixture('Instance')
  .page(config.endpoint)
  .before(async (ctx) => {
    ctx.initialData = await setup();
    await wait();
  })
  .beforeEach(async (t) => {
    await t.useRole(demoUser);
  });

test('Instance with an incident - header and instance header', async (t) => {
  const {
    initialData: {instanceWithIncident},
  } = t.fixtureCtx;

  const instanceId = instanceWithIncident.processInstanceKey;
  await t.navigateTo(`/instances/${instanceId}`);

  await t
    .expect(
      within(screen.queryByRole('banner')).queryByTestId('state-icon-INCIDENT')
        .exists
    )
    .ok()
    .expect(
      within(screen.queryByRole('banner')).queryByText(`Instance ${instanceId}`)
        .exists
    )
    .ok();

  const withinInstanceHeader = within(screen.queryByTestId('instance-header'));

  await t
    .expect(withinInstanceHeader.queryByTestId('INCIDENT-icon').exists)
    .ok()
    .expect(withinInstanceHeader.queryByText('processWithAnIncident').exists)
    .ok()
    .expect(withinInstanceHeader.queryByText(instanceId).exists)
    .ok()
    .expect(withinInstanceHeader.queryByText('Version 1').exists)
    .ok()
    .expect(
      withinInstanceHeader.queryByRole('button', {name: /retry instance/i})
        .exists
    )
    .ok()
    .expect(
      withinInstanceHeader.queryByRole('button', {name: /cancel instance/i})
        .exists
    )
    .ok()
    .expect(withinInstanceHeader.queryByTestId('start-date').textContent)
    .match(DATE_REGEX)
    .expect(withinInstanceHeader.queryByTestId('end-date').textContent)
    .eql('--');
});

test('Instance with an incident - history panel', async (t) => {
  const {
    initialData: {processWithMultipleTokens},
  } = t.fixtureCtx;

  await t.navigateTo(
    `/instances/${processWithMultipleTokens.processInstanceKey}`
  );
  await t.expect(screen.queryByText('Instance History').exists).ok();

  await t
    .click(within(screen.queryByTestId('diagram')).queryByText(/Task A/))
    .expect(
      within(screen.queryByTestId('popover')).queryByText(
        'To view metadata for any of these, select one instance in the Instance History.'
      ).exists
    )
    .ok()
    .expect(
      screen.queryByText(
        'To view the Variables, select a single Flow Node Instance in the Instance History.'
      ).exists
    )
    .ok();

  await t
    .click(
      within(screen.queryByTestId('instance-history'))
        .getAllByText(/Task A/)
        .nth(0)
    )
    .expect(
      within(screen.queryByTestId('popover')).queryByText(/Task A/).exists
    )
    .ok()
    .expect(screen.queryByText('The Flow Node has no variables.').exists)
    .ok();

  await t
    .click(
      within(screen.queryByTestId('instance-history'))
        .getAllByText(/Task A/)
        .nth(0)
    )
    .expect(screen.queryByTestId('popover').exists)
    .notOk()
    .expect(
      within(screen.queryByTestId('variables-list')).queryByText(
        'shouldContinue'
      ).exists
    )
    .ok();
});

test('Instance with an incident - diagram', async (t) => {
  const {
    initialData: {instanceWithIncident},
  } = t.fixtureCtx;

  const instanceId = instanceWithIncident.processInstanceKey;

  await t.navigateTo(`/instances/${instanceId}`);

  await t.expect(screen.queryByTestId('popover').exists).notOk();

  await t.click(
    within(screen.queryByTestId('diagram')).queryByText(/upper task/i)
  );

  const withinPopopver = within(screen.queryByTestId('popover'));
  await t
    .expect(withinPopopver.queryByText(/flowNodeInstanceId/).exists)
    .ok()
    .expect(withinPopopver.queryByText(/startDate/).exists)
    .ok()
    .expect(withinPopopver.queryByText(/endDate/).exists)
    .ok();

  await t.click(
    withinPopopver.queryByRole('button', {name: 'Show more metadata'})
  );

  const withinModal = within(screen.queryByTestId('modal'));
  await t
    .expect(withinModal.queryByText('Flow Node "Upper task" Metadata').exists)
    .ok();
  await t.click(screen.getAllByRole('button', {name: 'Close Modal'}));
  await t.expect(screen.queryByTestId('modal').exists).notOk();
});

test('Instance with an incident - resolve an incident', async (t) => {
  const {
    initialData: {instanceWithIncidentToResolve},
  } = t.fixtureCtx;

  const instanceId = instanceWithIncidentToResolve.processInstanceKey;

  await t.navigateTo(`/instances/${instanceId}`);

  await t
    .click(screen.queryByRole('button', {name: 'Add variable'}))
    .typeText(screen.queryByRole('textbox', {name: /variable/i}), 'goUp', {
      paste: true,
    })
    .typeText(screen.queryByRole('textbox', {name: /value/i}), '10', {
      paste: true,
    })
    .click(screen.queryByRole('button', {name: 'Save variable'}));

  await t
    .click(screen.queryByRole('button', {name: 'Add variable'}))
    .typeText(screen.queryByRole('textbox', {name: /variable/i}), 'orderId', {
      paste: true,
    })
    .typeText(screen.queryByRole('textbox', {name: /value/i}), '123', {
      paste: true,
    })
    .click(screen.queryByRole('button', {name: 'Save variable'}));

  await t
    .click(screen.queryByRole('button', {name: 'Add variable'}))
    .typeText(screen.queryByRole('textbox', {name: /variable/i}), 'clientId', {
      paste: true,
    })
    .typeText(screen.queryByRole('textbox', {name: /value/i}), '"test"', {
      paste: true,
    })
    .click(screen.queryByRole('button', {name: 'Save variable'}));

  await t.expect(screen.queryByTestId('operation-spinner').exists).notOk();

  await t
    .click(
      screen.queryByRole('button', {
        name: `Retry Instance ${instanceId}`,
      })
    )
    .expect(screen.queryByTestId('operation-spinner').exists)
    .ok();

  await t.expect(screen.queryByTestId('operation-spinner').exists).notOk();

  await t
    .expect(
      screen.queryByRole('button', {
        name: `View 3 Incidents in Instance ${instanceId}.`,
      }).exists
    )
    .notOk();

  await t
    .expect(
      within(screen.queryByTestId('instance-header')).queryByTestId(
        'ACTIVE-icon'
      ).exists
    )
    .ok();

  await t
    .expect(
      within(screen.queryByRole('banner')).queryByTestId('state-icon-ACTIVE')
        .exists
    )
    .ok();
});

test('Instance with an incident - incident bar', async (t) => {
  const {
    initialData: {instanceWithIncidentForIncidentsBar},
  } = t.fixtureCtx;

  const instanceId = instanceWithIncidentForIncidentsBar.processInstanceKey;

  await t.navigateTo(`/instances/${instanceId}`);

  // click and expand incident bar
  await t
    .click(
      screen.queryByRole('button', {name: /view 3 incidents in instance/i})
    )
    .expect(screen.queryByText(/incident type:/i).exists)
    .ok()
    .expect(screen.queryByText(/flow node:/i).exists)
    .ok();

  const withinIncidentsTable = within(screen.queryByTestId('incidents-table'));
  const withinHistoryPanel = within(screen.queryByTestId('instance-history'));
  const withinVariablesTable = within(screen.queryByTestId('variables-list'));

  // should be ordered by incident type by default
  await t
    .expect(
      within(withinIncidentsTable.getAllByRole('row').nth(1)).queryByText(
        /i\/o mapping error/i
      ).exists
    )
    .ok()
    .expect(
      within(withinIncidentsTable.getAllByRole('row').nth(2)).queryByText(
        /extract value error/i
      ).exists
    )
    .ok()
    .expect(
      within(withinIncidentsTable.getAllByRole('row').nth(3)).queryByText(
        /extract value error/i
      ).exists
    )
    .ok();

  // change the order of incident type
  await t.click(screen.queryByRole('button', {name: /sort by errortype/i}));

  await t
    .expect(
      within(withinIncidentsTable.getAllByRole('row').nth(1)).queryByText(
        /extract value error/i
      ).exists
    )
    .ok()
    .expect(
      within(withinIncidentsTable.getAllByRole('row').nth(2)).queryByText(
        /extract value error/i
      ).exists
    )
    .ok()
    .expect(
      within(withinIncidentsTable.getAllByRole('row').nth(3)).queryByText(
        /i\/o mapping error/i
      ).exists
    )
    .ok();

  // order by process name
  await t.click(screen.queryByRole('button', {name: /sort by flownodename/i}));
  await t
    .expect(
      within(withinIncidentsTable.getAllByRole('row').nth(1)).queryByText(
        'Where to go?'
      ).exists
    )
    .ok()
    .expect(
      within(withinIncidentsTable.getAllByRole('row').nth(2)).queryByText(
        'Upper task'
      ).exists
    )
    .ok()
    .expect(
      within(withinIncidentsTable.getAllByRole('row').nth(3)).queryByText(
        'message'
      ).exists
    )
    .ok();

  // change the order of process name
  await t.click(screen.queryByRole('button', {name: /sort by flownodename/i}));
  await t
    .expect(
      within(withinIncidentsTable.getAllByRole('row').nth(1)).queryByText(
        'message'
      ).exists
    )
    .ok()
    .expect(
      within(withinIncidentsTable.getAllByRole('row').nth(2)).queryByText(
        'Upper task'
      ).exists
    )
    .ok()
    .expect(
      within(withinIncidentsTable.getAllByRole('row').nth(3)).queryByText(
        'Where to go?'
      ).exists
    )
    .ok();

  // filter by incident type pills
  await t
    .click(screen.queryByRole('button', {name: /extract value error/i}))
    .expect(withinIncidentsTable.queryAllByRole('row').count)
    .eql(3)
    .expect(withinIncidentsTable.getAllByText(/extract value error/i).count)
    .eql(2)
    .expect(withinIncidentsTable.findByText(/i\/o mapping error/i).exists)
    .notOk();

  await t.click(screen.queryByRole('button', {name: /extract value error/i})); // deselect pill

  await t
    .click(screen.queryByRole('button', {name: /i\/o mapping error/i}))
    .expect(withinIncidentsTable.queryAllByRole('row').count)
    .eql(2)
    .expect(withinIncidentsTable.queryByText(/i\/o mapping error/i).exists)
    .ok()
    .expect(withinIncidentsTable.findByText(/extract value error/i).exists)
    .notOk();

  // clear filter pills
  await t
    .click(screen.queryByRole('button', {name: /clear all/i}))
    .expect(withinIncidentsTable.queryAllByRole('row').count)
    .eql(4)
    .expect(withinIncidentsTable.getAllByText(/extract value error/i).count)
    .eql(2)
    .expect(withinIncidentsTable.queryByText(/i\/o mapping error/i).exists)
    .ok();

  // filter by flow node pills
  await t
    .click(screen.queryByRole('button', {name: /where to go\? /i}))
    .expect(withinIncidentsTable.queryAllByRole('row').count)
    .eql(2)
    .click(screen.queryByRole('button', {name: /message /i}))
    .expect(withinIncidentsTable.queryAllByRole('row').count)
    .eql(3)
    .click(screen.queryByRole('button', {name: /upper task /i}))
    .expect(withinIncidentsTable.queryAllByRole('row').count)
    .eql(4);

  // clear filter pills
  await t
    .click(screen.queryByRole('button', {name: /clear all/i}))
    .expect(withinIncidentsTable.getAllByRole('row').count)
    .eql(4);

  // select a node from history panel, add variables to it, also see the variables when nodes are selected from the table inside the incident bar
  await t.click(withinHistoryPanel.queryByText(/where to go\?/i));

  await t
    .click(screen.queryByRole('button', {name: 'Add variable'}))
    .typeText(screen.queryByRole('textbox', {name: /variable/i}), 'goUp', {
      paste: true,
    })
    .typeText(screen.queryByRole('textbox', {name: /value/i}), '10', {
      paste: true,
    })
    .click(screen.queryByRole('button', {name: 'Save variable'}));

  await t.click(withinHistoryPanel.queryByText(/upper task/i));

  await t
    .click(screen.queryByRole('button', {name: 'Add variable'}))
    .typeText(screen.queryByRole('textbox', {name: /variable/i}), 'orderId', {
      paste: true,
    })
    .typeText(screen.queryByRole('textbox', {name: /value/i}), '123', {
      paste: true,
    })
    .click(screen.queryByRole('button', {name: 'Save variable'}));

  await t.click(withinHistoryPanel.queryByText(/message/i));

  await t
    .click(screen.queryByRole('button', {name: 'Add variable'}))
    .typeText(screen.queryByRole('textbox', {name: /variable/i}), 'clientId', {
      paste: true,
    })
    .typeText(screen.queryByRole('textbox', {name: /value/i}), '"test"', {
      paste: true,
    })
    .click(screen.queryByRole('button', {name: 'Save variable'}));

  await t
    .expect(screen.queryByTestId('operation-spinner').exists)
    .notOk({timeout: 120000});

  // clear filters
  await t
    .click(screen.queryByRole('button', {name: /clear all/i}))
    .expect(withinIncidentsTable.queryAllByRole('row').count)
    .eql(4);

  await t
    .click(withinIncidentsTable.queryByRole('row', {name: /Upper task/}))
    .expect(withinVariablesTable.queryAllByRole('row').count)
    .eql(2)
    .expect(withinVariablesTable.queryByRole('cell', {name: /orderid/i}).exists)
    .ok();

  await t
    .click(withinIncidentsTable.queryByRole('row', {name: /Where to go\?/}))
    .expect(withinVariablesTable.queryAllByRole('row').count)
    .eql(2)
    .expect(withinVariablesTable.queryByRole('cell', {name: /goUp/i}).exists)
    .ok();

  await t
    .click(withinIncidentsTable.queryByRole('row', {name: /message/}))
    .expect(withinVariablesTable.queryAllByRole('row').count)
    .eql(2)
    .expect(
      withinVariablesTable.queryByRole('cell', {name: /clientId/i}).exists
    )
    .ok();

  // resolve one incident, see it disappears after resolving
  await t
    .click(
      within(
        withinIncidentsTable.queryByRole('row', {name: /Upper task/})
      ).queryByRole('button', {name: 'Retry Incident'})
    )
    .expect(
      within(
        withinIncidentsTable.queryByRole('row', {name: /Upper task/})
      ).queryByTestId('operation-spinner').exists
    )
    .ok();

  await t
    .expect(
      withinIncidentsTable.queryByRole('row', {name: /Upper task/}).exists
    )
    .notOk();

  // see incident bar disappears after all other incidents are resolved
  await t
    .click(
      within(
        withinIncidentsTable.queryByRole('row', {name: /Where to go\?/})
      ).queryByRole('button', {name: 'Retry Incident'})
    )
    .click(
      within(
        withinIncidentsTable.queryByRole('row', {name: /message/})
      ).queryByRole('button', {name: 'Retry Incident'})
    );

  await t
    .expect(screen.queryByTestId('incidents-banner').exists)
    .notOk()
    .expect(screen.queryByTestId('incidents-table').exists)
    .notOk();

  await t
    .expect(
      within(screen.queryByTestId('instance-header')).queryByTestId(
        'ACTIVE-icon'
      ).exists
    )
    .ok();

  await t
    .expect(
      within(screen.queryByRole('banner')).queryByTestId('state-icon-ACTIVE')
        .exists
    )
    .ok();
});

// This test was skipped, because of OPE-1098, please unskip, when the bug is fixed
test.skip('Instance with an incident - cancel an instance', async (t) => {
  const {
    initialData: {instanceWithIncidentToCancel},
  } = t.fixtureCtx;

  const instanceId = instanceWithIncidentToCancel.processInstanceKey;

  await t.navigateTo(`/instances/${instanceId}`);

  await t
    .expect(
      screen.queryByRole('button', {
        name: `View 3 Incidents in Instance ${instanceId}.`,
      }).exists
    )
    .ok();

  await t
    .click(
      screen.queryByRole('button', {
        name: `Cancel Instance ${instanceId}`,
      })
    )
    .expect(screen.queryByTestId('operation-spinner').exists)
    .ok();

  await t.expect(screen.queryByTestId('operation-spinner').exists).notOk();
  await t
    .expect(
      screen.queryByRole('button', {
        name: `View 3 Incidents in Instance ${instanceId}.`,
      }).exists
    )
    .notOk();

  await t
    .expect(
      within(screen.queryByTestId('instance-header')).queryByTestId(
        'CANCELED-icon'
      ).exists
    )
    .ok();

  await t
    .expect(
      within(screen.queryByTestId('instance-header')).queryByTestId('end-date')
        .textContent
    )
    .match(DATE_REGEX)
    .expect(
      within(screen.queryByRole('banner')).queryByTestId('state-icon-CANCELED')
        .exists
    )
    .ok();
});

test('Instance without an incident', async (t) => {
  const {
    initialData: {instanceWithoutAnIncident},
  } = t.fixtureCtx;

  await t.navigateTo(
    `/instances/${instanceWithoutAnIncident.processInstanceKey}`
  );

  await t
    .expect(screen.queryByTestId('diagram').exists)
    .ok()
    .expect(screen.queryByTestId('incidents-banner').exists)
    .notOk()
    .expect(
      within(screen.queryByRole('banner')).queryByTestId('state-icon-ACTIVE')
        .exists
    )
    .ok()
    .expect(
      within(screen.queryByRole('banner')).queryByText(
        `Instance ${instanceWithoutAnIncident.processInstanceKey}`
      ).exists
    )
    .ok()
    .expect(
      within(screen.queryByTestId('instance-header')).queryByTestId(
        'ACTIVE-icon'
      ).exists
    )
    .ok()
    .expect(screen.queryByText('Instance History').exists)
    .ok()
    .expect(screen.queryByRole('button', {name: 'Add variable'}).exists)
    .ok();
});
