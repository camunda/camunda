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

  const instanceId = instanceWithIncident.workflowInstanceKey;
  await t.navigateTo(`${config.endpoint}/#/instances/${instanceId}`);

  await t
    .expect(
      within(screen.getByRole('banner')).getByTestId('state-icon-INCIDENT')
        .exists
    )
    .ok()
    .expect(
      within(screen.getByRole('banner')).getByText(`Instance ${instanceId}`)
        .exists
    )
    .ok();

  const withinInstanceHeader = within(screen.getByTestId('instance-header'));

  await t
    .expect(withinInstanceHeader.getByTestId('INCIDENT-icon').exists)
    .ok()
    .expect(withinInstanceHeader.getByText('processWithAnIncident').exists)
    .ok()
    .expect(withinInstanceHeader.getByText(instanceId).exists)
    .ok()
    .expect(withinInstanceHeader.getByText('Version 1').exists)
    .ok()
    .expect(
      withinInstanceHeader.getByRole('button', {name: /retry instance/i}).exists
    )
    .ok()
    .expect(
      withinInstanceHeader.getByRole('button', {name: /cancel instance/i})
        .exists
    )
    .ok()
    .expect(withinInstanceHeader.getByTestId('start-date').textContent)
    .match(DATE_REGEX)
    .expect(withinInstanceHeader.getByTestId('end-date').textContent)
    .eql('--');
});

test('Instance with an incident - history panel', async (t) => {
  const {
    initialData: {processWithMultipleTokens},
  } = t.fixtureCtx;

  await t.navigateTo(
    `${config.endpoint}/#/instances/${processWithMultipleTokens.workflowInstanceKey}`
  );
  await t.expect(screen.getByText('Instance History').exists).ok();

  await t
    .click(within(screen.getByTestId('diagram')).getByText(/Task A/))
    .expect(
      within(screen.getByTestId('popover')).getByText(
        'To view metadata for any of these, select one instance in the Instance History.'
      ).exists
    )
    .ok()
    .expect(
      screen.getByText(
        'To view the Variables, select a single Flow Node Instance in the Instance History.'
      ).exists
    )
    .ok();

  await t
    .click(
      within(screen.getByTestId('instance-history'))
        .getAllByText(/Task A/)
        .nth(0)
    )
    .expect(within(screen.getByTestId('popover')).getByText(/Task A/).exists)
    .ok()
    .expect(screen.getByText('The Flow Node has no variables.').exists)
    .ok();

  await t
    .click(
      within(screen.getByTestId('instance-history'))
        .getAllByText(/Task A/)
        .nth(0)
    )
    .expect(screen.queryByTestId('popover').exists)
    .notOk()
    .expect(
      within(screen.getByTestId('variables-list')).getByText('shouldContinue')
        .exists
    )
    .ok();
});

test('Instance with an incident - diagram', async (t) => {
  const {
    initialData: {instanceWithIncident},
  } = t.fixtureCtx;

  const instanceId = instanceWithIncident.workflowInstanceKey;

  await t.navigateTo(`${config.endpoint}/#/instances/${instanceId}`);

  await t.expect(screen.queryByTestId('popover').exists).notOk();

  await t.click(within(screen.getByTestId('diagram')).getByText(/upper task/i));

  const withinPopopver = within(screen.getByTestId('popover'));
  await t
    .expect(withinPopopver.queryByText(/flowNodeInstanceId/).exists)
    .ok()
    .expect(withinPopopver.getByText(/startDate/).exists)
    .ok()
    .expect(withinPopopver.getByText(/endDate/).exists)
    .ok();

  await t.click(
    withinPopopver.getByRole('button', {name: 'Show more metadata'})
  );

  const withinModal = within(screen.getByTestId('modal'));
  await t
    .expect(withinModal.getByText('Flow Node "Upper task" Metadata').exists)
    .ok();
  await t.click(screen.getAllByRole('button', {name: 'Close Modal'}));
  await t.expect(screen.queryByTestId('modal').exists).notOk();
});

test('Instance with an incident - resolve an incident', async (t) => {
  const {
    initialData: {instanceWithIncidentToResolve},
  } = t.fixtureCtx;

  const instanceId = instanceWithIncidentToResolve.workflowInstanceKey;

  await t.navigateTo(`${config.endpoint}/#/instances/${instanceId}`);

  await t
    .click(screen.getByRole('button', {name: 'Add variable'}))
    .typeText(screen.getByRole('textbox', {name: /variable/i}), 'goUp', {
      paste: true,
    })
    .typeText(screen.getByRole('textbox', {name: /value/i}), '10', {
      paste: true,
    })
    .click(screen.getByRole('button', {name: 'Save variable'}));

  await t
    .click(screen.getByRole('button', {name: 'Add variable'}))
    .typeText(screen.getByRole('textbox', {name: /variable/i}), 'orderId', {
      paste: true,
    })
    .typeText(screen.getByRole('textbox', {name: /value/i}), '123', {
      paste: true,
    })
    .click(screen.getByRole('button', {name: 'Save variable'}));

  await t
    .click(screen.getByRole('button', {name: 'Add variable'}))
    .typeText(screen.getByRole('textbox', {name: /variable/i}), 'clientId', {
      paste: true,
    })
    .typeText(screen.getByRole('textbox', {name: /value/i}), '"test"', {
      paste: true,
    })
    .click(screen.getByRole('button', {name: 'Save variable'}));

  await t.expect(screen.queryByTestId('operation-spinner').exists).notOk();

  await t
    .click(
      screen.getByRole('button', {
        name: `Retry Instance ${instanceId}`,
      })
    )
    .expect(screen.getByTestId('operation-spinner').exists)
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
      within(screen.getByTestId('instance-header')).queryByTestId('ACTIVE-icon')
        .exists
    )
    .ok();

  await t
    .expect(
      within(screen.getByRole('banner')).getByTestId('state-icon-ACTIVE').exists
    )
    .ok();
});

test('Instance with an incident - incident bar', async (t) => {
  const {
    initialData: {instanceWithIncidentForIncidentsBar},
  } = t.fixtureCtx;

  const instanceId = instanceWithIncidentForIncidentsBar.workflowInstanceKey;

  await t.navigateTo(`${config.endpoint}/#/instances/${instanceId}`);

  // click and expand incident bar
  await t
    .click(
      screen.queryByRole('button', {name: /view 3 incidents in instance/i})
    )
    .expect(screen.getByText(/incident type:/i).exists)
    .ok()
    .expect(screen.getByText(/flow node:/i).exists)
    .ok();

  const withinIncidentsTable = within(screen.getByTestId('incidents-table'));
  const withinHistoryPanel = within(screen.getByTestId('instance-history'));
  const withinVariablesTable = within(screen.getByTestId('variables-list'));

  // should be ordered by incident type by default
  await t
    .expect(
      within(withinIncidentsTable.getAllByRole('row').nth(1)).getByText(
        /i\/o mapping error/i
      ).exists
    )
    .ok()
    .expect(
      within(withinIncidentsTable.getAllByRole('row').nth(2)).getByText(
        /extract value error/i
      ).exists
    )
    .ok()
    .expect(
      within(withinIncidentsTable.getAllByRole('row').nth(3)).getByText(
        /extract value error/i
      ).exists
    )
    .ok();

  // change the order of incident type
  await t.click(screen.getByRole('button', {name: /sort by errortype/i}));

  await t
    .expect(
      within(withinIncidentsTable.getAllByRole('row').nth(1)).getByText(
        /extract value error/i
      ).exists
    )
    .ok()
    .expect(
      within(withinIncidentsTable.getAllByRole('row').nth(2)).getByText(
        /extract value error/i
      ).exists
    )
    .ok()
    .expect(
      within(withinIncidentsTable.getAllByRole('row').nth(3)).getByText(
        /i\/o mapping error/i
      ).exists
    )
    .ok();

  // order by workflow name
  await t.click(screen.getByRole('button', {name: /sort by flownodename/i}));
  await t
    .expect(
      within(withinIncidentsTable.getAllByRole('row').nth(1)).getByText(
        'Where to go?'
      ).exists
    )
    .ok()
    .expect(
      within(withinIncidentsTable.getAllByRole('row').nth(2)).getByText(
        'Upper task'
      ).exists
    )
    .ok()
    .expect(
      within(withinIncidentsTable.getAllByRole('row').nth(3)).getByText(
        'message'
      ).exists
    )
    .ok();

  // change the order of workflow name
  await t.click(screen.getByRole('button', {name: /sort by flownodename/i}));
  await t
    .expect(
      within(withinIncidentsTable.getAllByRole('row').nth(1)).getByText(
        'message'
      ).exists
    )
    .ok()
    .expect(
      within(withinIncidentsTable.getAllByRole('row').nth(2)).getByText(
        'Upper task'
      ).exists
    )
    .ok()
    .expect(
      within(withinIncidentsTable.getAllByRole('row').nth(3)).getByText(
        'Where to go?'
      ).exists
    )
    .ok();

  // filter by incident type pills
  await t
    .click(screen.getByRole('button', {name: /extract value error/i}))
    .expect(withinIncidentsTable.queryAllByRole('row').count)
    .eql(3)
    .expect(withinIncidentsTable.getAllByText(/extract value error/i).count)
    .eql(2)
    .expect(withinIncidentsTable.findByText(/i\/o mapping error/i).exists)
    .notOk();

  await t.click(screen.getByRole('button', {name: /extract value error/i})); // deselect pill

  await t
    .click(screen.getByRole('button', {name: /i\/o mapping error/i}))
    .expect(withinIncidentsTable.queryAllByRole('row').count)
    .eql(2)
    .expect(withinIncidentsTable.getByText(/i\/o mapping error/i).exists)
    .ok()
    .expect(withinIncidentsTable.findByText(/extract value error/i).exists)
    .notOk();

  // clear filter pills
  await t
    .click(screen.getByRole('button', {name: /clear all/i}))
    .expect(withinIncidentsTable.queryAllByRole('row').count)
    .eql(4)
    .expect(withinIncidentsTable.getAllByText(/extract value error/i).count)
    .eql(2)
    .expect(withinIncidentsTable.getByText(/i\/o mapping error/i).exists)
    .ok();

  // filter by flow node pills
  await t
    .click(screen.getByRole('button', {name: /where to go\? /i}))
    .expect(withinIncidentsTable.queryAllByRole('row').count)
    .eql(2)
    .click(screen.getByRole('button', {name: /message /i}))
    .expect(withinIncidentsTable.queryAllByRole('row').count)
    .eql(3)
    .click(screen.getByRole('button', {name: /upper task /i}))
    .expect(withinIncidentsTable.queryAllByRole('row').count)
    .eql(4);

  // clear filter pills
  await t
    .click(screen.getByRole('button', {name: /clear all/i}))
    .expect(withinIncidentsTable.getAllByRole('row').count)
    .eql(4);

  // select a node from history panel, add variables to it, also see the variables when nodes are selected from the table inside the incident bar
  await t.click(withinHistoryPanel.getByText(/where to go\?/i));

  await t
    .click(screen.getByRole('button', {name: 'Add variable'}))
    .typeText(screen.getByRole('textbox', {name: /variable/i}), 'goUp', {
      paste: true,
    })
    .typeText(screen.getByRole('textbox', {name: /value/i}), '10', {
      paste: true,
    })
    .click(screen.getByRole('button', {name: 'Save variable'}));

  await t.click(withinHistoryPanel.getByText(/upper task/i));

  await t
    .click(screen.getByRole('button', {name: 'Add variable'}))
    .typeText(screen.getByRole('textbox', {name: /variable/i}), 'orderId', {
      paste: true,
    })
    .typeText(screen.getByRole('textbox', {name: /value/i}), '123', {
      paste: true,
    })
    .click(screen.getByRole('button', {name: 'Save variable'}));

  await t.click(withinHistoryPanel.getByText(/message/i));

  await t
    .click(screen.getByRole('button', {name: 'Add variable'}))
    .typeText(screen.getByRole('textbox', {name: /variable/i}), 'clientId', {
      paste: true,
    })
    .typeText(screen.getByRole('textbox', {name: /value/i}), '"test"', {
      paste: true,
    })
    .click(screen.getByRole('button', {name: 'Save variable'}));

  await t
    .expect(screen.queryByTestId('operation-spinner').exists)
    .notOk({timeout: 120000});

  // clear filters
  await t
    .click(screen.getByRole('button', {name: /clear all/i}))
    .expect(withinIncidentsTable.queryAllByRole('row').count)
    .eql(4);

  await t
    .click(withinIncidentsTable.getByRole('row', {name: /Upper task/}))
    .expect(withinVariablesTable.queryAllByRole('row').count)
    .eql(2)
    .expect(withinVariablesTable.getByRole('cell', {name: /orderid/i}).exists)
    .ok();

  await t
    .click(withinIncidentsTable.getByRole('row', {name: /Where to go\?/}))
    .expect(withinVariablesTable.queryAllByRole('row').count)
    .eql(2)
    .expect(withinVariablesTable.getByRole('cell', {name: /goUp/i}).exists)
    .ok();

  await t
    .click(withinIncidentsTable.getByRole('row', {name: /message/}))
    .expect(withinVariablesTable.queryAllByRole('row').count)
    .eql(2)
    .expect(withinVariablesTable.getByRole('cell', {name: /clientId/i}).exists)
    .ok();

  // resolve one incident, see it disappears after resolving
  await t
    .click(
      within(
        withinIncidentsTable.getByRole('row', {name: /Upper task/})
      ).getByRole('button', {name: 'Retry Incident'})
    )
    .expect(
      within(
        withinIncidentsTable.getByRole('row', {name: /Upper task/})
      ).getByTestId('operation-spinner').exists
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
        withinIncidentsTable.getByRole('row', {name: /Where to go\?/})
      ).getByRole('button', {name: 'Retry Incident'})
    )
    .click(
      within(
        withinIncidentsTable.getByRole('row', {name: /message/})
      ).getByRole('button', {name: 'Retry Incident'})
    );

  await t
    .expect(screen.queryByTestId('incidents-banner').exists)
    .notOk()
    .expect(screen.queryByTestId('incidents-table').exists)
    .notOk();

  await t
    .expect(
      within(screen.getByTestId('instance-header')).queryByTestId('ACTIVE-icon')
        .exists
    )
    .ok();

  await t
    .expect(
      within(screen.getByRole('banner')).getByTestId('state-icon-ACTIVE').exists
    )
    .ok();
});

// This test was skipped, because of OPE-1098, please unskip, when the bug is fixed
test.skip('Instance with an incident - cancel an instance', async (t) => {
  const {
    initialData: {instanceWithIncidentToCancel},
  } = t.fixtureCtx;

  const instanceId = instanceWithIncidentToCancel.workflowInstanceKey;

  await t.navigateTo(`${config.endpoint}/#/instances/${instanceId}`);

  await t
    .expect(
      screen.getByRole('button', {
        name: `View 3 Incidents in Instance ${instanceId}.`,
      }).exists
    )
    .ok();

  await t
    .click(
      screen.getByRole('button', {
        name: `Cancel Instance ${instanceId}`,
      })
    )
    .expect(screen.getByTestId('operation-spinner').exists)
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
      within(screen.getByTestId('instance-header')).queryByTestId(
        'CANCELED-icon'
      ).exists
    )
    .ok();

  await t
    .expect(
      within(screen.getByTestId('instance-header')).getByTestId('end-date')
        .textContent
    )
    .match(DATE_REGEX)
    .expect(
      within(screen.getByRole('banner')).getByTestId('state-icon-CANCELED')
        .exists
    )
    .ok();
});

test('Instance without an incident', async (t) => {
  const {
    initialData: {instanceWithoutAnIncident},
  } = t.fixtureCtx;

  await t.navigateTo(
    `${config.endpoint}/#/instances/${instanceWithoutAnIncident.workflowInstanceKey}`
  );

  await t
    .expect(screen.queryByTestId('diagram').exists)
    .ok()
    .expect(screen.queryByTestId('incidents-banner').exists)
    .notOk()
    .expect(
      within(screen.getByRole('banner')).getByTestId('state-icon-ACTIVE').exists
    )
    .ok()
    .expect(
      within(screen.getByRole('banner')).getByText(
        `Instance ${instanceWithoutAnIncident.workflowInstanceKey}`
      ).exists
    )
    .ok()
    .expect(
      within(screen.getByTestId('instance-header')).getByTestId('ACTIVE-icon')
        .exists
    )
    .ok()
    .expect(screen.getByText('Instance History').exists)
    .ok()
    .expect(screen.getByRole('button', {name: 'Add variable'}).exists)
    .ok();
});
