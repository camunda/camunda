/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {config} from '../config';
import {setup} from './ProcessInstance.setup';
import {demoUser} from './utils/Roles';
import {wait} from './utils/wait';
import {screen, within} from '@testing-library/testcafe';
import {DATE_REGEX} from './constants';
import {processInstancePage as ProcessInstancePage} from './PageModels/ProcessInstance';

fixture('Process Instance')
  .page(config.endpoint)
  .before(async (ctx) => {
    ctx.initialData = await setup();
    await wait();
  })
  .beforeEach(async (t) => {
    await t.useRole(demoUser);
  });

test('Instance with an incident - instance header', async (t) => {
  const {
    initialData: {instanceWithIncident},
  } = t.fixtureCtx;

  const instanceId = instanceWithIncident.processInstanceKey;
  await t.navigateTo(`/processes/${instanceId}`);

  await t
    .expect(screen.queryByTestId('instance-header-skeleton').exists)
    .notOk();

  const withinInstanceHeader = within(screen.queryByTestId('instance-header'));

  await t
    .expect(withinInstanceHeader.queryByTestId('INCIDENT-icon').exists)
    .ok()
    .expect(withinInstanceHeader.queryByText('processWithAnIncident').exists)
    .ok()
    .expect(withinInstanceHeader.queryByText(instanceId).exists)
    .ok()
    .expect(withinInstanceHeader.queryByText('1').exists)
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
    `/processes/${processWithMultipleTokens.processInstanceKey}`
  );
  await t
    .expect(screen.queryByText('Instance History').exists)
    .ok()
    .expect(screen.queryByTestId('instance-history').exists)
    .ok()
    .expect(screen.queryByTestId('diagram').exists)
    .ok();

  await t
    .click(within(screen.queryByTestId('diagram')).queryByText(/Task A/))
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

  await t
    .click(
      within(screen.queryByTestId('instance-history'))
        .getAllByText(/Task A/)
        .nth(0)
    )
    .expect(screen.queryByTestId('popover').exists)
    .ok()
    .expect(
      within(screen.queryByTestId('popover')).queryByText(/Task A/).exists
    )
    .ok()
    .expect(screen.queryByText('The Flow Node has no Variables').exists)
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

  await t.navigateTo(`/processes/${instanceId}`);

  await t
    .expect(screen.queryByTestId('diagram').exists)
    .ok()
    .expect(screen.queryByTestId('popover').exists)
    .notOk();

  await t
    .click(within(screen.queryByTestId('diagram')).queryByText(/upper task/i))
    .expect(screen.queryByTestId('popover').exists)
    .ok();

  const withinPopopver = within(screen.queryByTestId('popover'));
  await t
    .expect(withinPopopver.queryByText(/Flow Node Instance Id/).exists)
    .ok()
    .expect(withinPopopver.queryByText(/Start Date/).exists)
    .ok()
    .expect(withinPopopver.queryByText(/End Date/).exists)
    .ok()
    .expect(withinPopopver.queryByText(/Type/).exists)
    .ok()
    .expect(withinPopopver.queryByText(/Error Message/).exists)
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

test('Instance with an incident - resolve incidents', async (t) => {
  const {
    initialData: {instanceWithIncidentToResolve},
  } = t.fixtureCtx;

  const instanceId = instanceWithIncidentToResolve.processInstanceKey;

  await t.navigateTo(`/processes/${instanceId}`);

  // click and expand incident bar
  await t
    .click(
      screen.queryByRole('button', {name: /view 2 incidents in instance/i})
    )
    .expect(screen.queryByText(/incident type:/i).exists)
    .ok()
    .expect(screen.queryByText(/flow node:/i).exists)
    .ok();

  const withinIncidentsTable = within(screen.queryByTestId('incidents-table'));
  const withinVariablesList = within(screen.queryByTestId('variables-list'));

  // edit goUp variable
  await t
    .click(withinVariablesList.queryByRole('button', {name: 'Enter edit mode'}))
    .typeText(
      within(ProcessInstancePage.editVariableValueField).queryByRole('textbox'),
      '20',
      {
        paste: true,
        replace: true,
      }
    )
    .click(withinVariablesList.queryByRole('button', {name: 'Save variable'}))
    .expect(ProcessInstancePage.variableSpinner.exists)
    .ok()
    .expect(ProcessInstancePage.variableSpinner.exists)
    .notOk({timeout: 120000});

  // retry one incident to resolve it
  await t
    .click(
      within(
        withinIncidentsTable.queryByRole('row', {name: /Condition error/i})
      ).queryByRole('button', {name: 'Retry Incident'})
    )
    .expect(
      within(
        withinIncidentsTable.queryByRole('row', {name: /Condition error/i})
      ).queryByTestId('operation-spinner').exists
    )
    .ok()
    .expect(withinIncidentsTable.queryByTestId('operation-spinner').exists)
    .notOk({timeout: 120000});

  await t
    .expect(withinIncidentsTable.queryAllByRole('row').count)
    .eql(2)
    .expect(withinIncidentsTable.queryByText(/is cool\?/i).exists)
    .ok()
    .expect(withinIncidentsTable.queryByText(/where to go\?/i).exists)
    .notOk();

  // add variable isCool

  await t
    .click(ProcessInstancePage.addVariableButton)
    .typeText(
      within(ProcessInstancePage.newVariableNameField).queryByRole('textbox'),
      'isCool',
      {
        paste: true,
      }
    )
    .typeText(
      within(ProcessInstancePage.newVariableValueField).queryByRole('textbox'),
      'true',
      {
        paste: true,
      }
    )
    .expect(
      screen
        .queryByRole('button', {name: 'Save variable'})
        .hasAttribute('disabled')
    )
    .notOk();

  await t
    .click(screen.queryByRole('button', {name: 'Save variable'}))
    .expect(ProcessInstancePage.variableSpinner.exists)
    .ok()
    .expect(ProcessInstancePage.variableSpinner.exists)
    .notOk({timeout: 120000});

  // retry second incident to resolve it
  await t
    .click(
      within(
        withinIncidentsTable.queryByRole('row', {name: /Extract value error/i})
      ).queryByRole('button', {name: 'Retry Incident'})
    )
    .expect(
      within(
        withinIncidentsTable.queryByRole('row', {name: /Extract value error/i})
      ).queryByTestId('operation-spinner').exists
    )
    .ok();

  // expect all incidents resolved
  await t
    .expect(screen.queryByTestId('incidents-banner').exists)
    .notOk()
    .expect(screen.queryByTestId('incidents-table').exists)
    .notOk()
    .expect(
      within(screen.queryByTestId('instance-header')).queryByTestId(
        'COMPLETED-icon'
      ).exists
    )
    .ok();
});

test('Instance with an incident - incident bar', async (t) => {
  const {
    initialData: {instanceWithIncidentForIncidentsBar},
  } = t.fixtureCtx;

  const instanceId = instanceWithIncidentForIncidentsBar.processInstanceKey;

  await t.navigateTo(`/processes/${instanceId}`);

  // click and expand incident bar
  await t
    .click(
      screen.queryByRole('button', {name: /view 2 incidents in instance/i})
    )
    .expect(screen.queryByText(/incident type:/i).exists)
    .ok()
    .expect(screen.queryByText(/flow node:/i).exists)
    .ok();

  const withinIncidentsTable = within(screen.queryByTestId('incidents-table'));
  const withinIncidentsFilter = within(
    screen.queryByTestId('incidents-filter')
  );

  // expect incidents ordered by type by default
  await t
    .expect(
      within(withinIncidentsTable.queryAllByRole('row').nth(1)).queryByText(
        /extract value error/i
      ).exists
    )
    .ok()
    .expect(
      within(withinIncidentsTable.queryAllByRole('row').nth(2)).queryByText(
        /condition error/i
      ).exists
    )
    .ok();

  // change order of incident type
  await t
    .click(
      withinIncidentsTable.queryByRole('button', {name: /sort by errortype/i})
    )
    .expect(
      within(withinIncidentsTable.queryAllByRole('row').nth(1)).queryByText(
        /condition error/i
      ).exists
    )
    .ok()
    .expect(
      within(withinIncidentsTable.queryAllByRole('row').nth(2)).queryByText(
        /extract value error/i
      ).exists
    )
    .ok();

  // change order of process name
  await t
    .click(screen.queryByRole('button', {name: /sort by flownodename/i}))
    .expect(
      within(withinIncidentsTable.queryAllByRole('row').nth(1)).queryByText(
        /where to go\?/i
      ).exists
    )
    .ok()
    .expect(
      within(withinIncidentsTable.queryAllByRole('row').nth(2)).queryByText(
        /is cool\?/i
      ).exists
    )
    .ok();

  await t
    .click(screen.queryByRole('button', {name: /sort by flownodename/i}))
    .expect(
      within(withinIncidentsTable.queryAllByRole('row').nth(1)).queryByText(
        /is cool\?/i
      ).exists
    )
    .ok()
    .expect(
      within(withinIncidentsTable.queryAllByRole('row').nth(2)).queryByText(
        /where to go\?/i
      ).exists
    )
    .ok();

  // filter by incident type pills
  await t
    .click(
      withinIncidentsFilter.queryByRole('button', {
        name: /extract value error/i,
      })
    )
    .expect(withinIncidentsTable.queryAllByRole('row').count)
    .eql(2)
    .expect(withinIncidentsTable.queryByText(/extract value error/i).exists)
    .ok();

  // deselect pill
  await t.click(
    withinIncidentsFilter.queryByRole('button', {name: /extract value error/i})
  );

  await t
    .click(
      withinIncidentsFilter.queryByRole('button', {name: /condition error/i})
    )
    .expect(withinIncidentsTable.queryAllByRole('row').count)
    .eql(2)
    .expect(withinIncidentsTable.queryByText(/condition error/i).exists)
    .ok();

  // clear filter pills
  await t
    .click(withinIncidentsFilter.queryByRole('button', {name: /clear all/i}))
    .expect(withinIncidentsTable.queryAllByRole('row').count)
    .eql(3)
    .expect(withinIncidentsTable.queryByText(/extract value error/i).exists)
    .ok()
    .expect(withinIncidentsTable.queryByText(/condition error/i).exists)
    .ok();

  // filter by flow node pills
  await t
    .click(
      withinIncidentsFilter.queryByRole('button', {name: /where to go\?/i})
    )
    .expect(withinIncidentsTable.queryAllByRole('row').count)
    .eql(2)
    .expect(withinIncidentsTable.queryByText(/where to go\?/i).exists)
    .ok();

  await t
    .click(withinIncidentsFilter.queryByRole('button', {name: /is cool\?/i}))

    .expect(withinIncidentsTable.queryAllByRole('row').count)
    .eql(3)
    .expect(withinIncidentsTable.queryByText(/is cool\?/i).exists)
    .ok()
    .expect(withinIncidentsTable.queryByText(/where to go\?/i).exists)
    .ok();

  await t
    .click(withinIncidentsFilter.queryByRole('button', {name: /is cool\?/i}))
    .expect(withinIncidentsTable.queryAllByRole('row').count)
    .eql(2)
    .expect(withinIncidentsTable.queryByText(/is cool\?/i).exists)
    .notOk();

  // clear filter pills
  await t
    .click(withinIncidentsFilter.queryByRole('button', {name: /clear all/i}))
    .expect(withinIncidentsTable.queryAllByRole('row').count)
    .eql(3);
});

test('Instance with an incident - cancel an instance', async (t) => {
  const {
    initialData: {instanceWithIncidentToCancel},
  } = t.fixtureCtx;

  const instanceId = instanceWithIncidentToCancel.processInstanceKey;

  await t.navigateTo(`/processes/${instanceId}`);

  await t
    .expect(
      screen.queryByRole('button', {
        name: `View 3 Incidents in Instance ${instanceId}`,
      }).exists
    )
    .ok();

  await t
    .click(
      screen.queryByRole('button', {
        name: `Cancel Instance ${instanceId}`,
      })
    )
    .click(screen.getByRole('button', {name: 'Apply'}))
    .expect(ProcessInstancePage.operationSpinner.exists)
    .ok()
    .expect(ProcessInstancePage.operationSpinner.exists)
    .notOk({timeout: 120000});

  await t
    .expect(
      screen.queryByRole('button', {
        name: `View 3 Incidents in Instance ${instanceId}`,
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
    .match(DATE_REGEX);
});

test('Instance without an incident', async (t) => {
  const {
    initialData: {instanceWithoutAnIncident},
  } = t.fixtureCtx;

  await t.navigateTo(
    `/processes/${instanceWithoutAnIncident.processInstanceKey}`
  );

  await t
    .expect(screen.queryByTestId('diagram').exists)
    .ok()
    .expect(screen.queryByTestId('incidents-banner').exists)
    .notOk()
    .expect(
      within(screen.queryByTestId('instance-header')).queryByTestId(
        'ACTIVE-icon'
      ).exists
    )
    .ok()
    .expect(screen.queryByText('Instance History').exists)
    .ok()
    .expect(ProcessInstancePage.addVariableButton.exists)
    .ok();
});
