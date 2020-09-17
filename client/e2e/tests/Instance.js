/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {config} from '../config';
import {setup} from './Instance.setup.js';
import {demoUser} from './utils/Roles';
import {wait} from './utils/wait';
import {screen, within} from '@testing-library/testcafe';
import {DATE_REGEX, DEFAULT_TIMEOUT} from './constants';

fixture('Instance')
  .page(config.endpoint)
  .before(async (ctx) => {
    ctx.initialData = await setup();
    await wait(DEFAULT_TIMEOUT);
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
    initialData: {instanceWithIncident},
  } = t.fixtureCtx;

  const instanceId = instanceWithIncident.workflowInstanceKey;

  await t.navigateTo(`${config.endpoint}/#/instances/${instanceId}`);

  await t.expect(screen.getByText('Instance History').exists).ok();

  // TODO: OPE-1054
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
    .expect(withinPopopver.getByText(/activityInstanceId/).exists)
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

test('Instance with an incident - cancel an instance', async (t) => {
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
