/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {config} from '../config';
import {setup} from './Instances.setup';
import {deploy} from '../setup-utils';
import {demoUser} from './utils/Roles';
import {wait} from './utils/wait';
import {getPathname} from './utils/getPathname';
import {getSearch} from './utils/getSearch';
import {convertToQueryString} from './utils/convertToQueryString';
import {screen, within} from '@testing-library/testcafe';

fixture('Instances')
  .page(config.endpoint)
  .before(async (ctx) => {
    ctx.initialData = await setup();
    await wait();
  })
  .beforeEach(async (t) => {
    await t
      .useRole(demoUser)
      .navigateTo('/')
      .click(
        screen.getByRole('listitem', {
          name: /running instances/i,
        })
      );
  });

test('Instances Page Initial Load', async (t) => {
  const {initialData} = t.fixtureCtx;

  await t
    .expect(screen.queryByRole('checkbox', {name: 'Running Instances'}).checked)
    .ok()
    .expect(screen.queryByRole('checkbox', {name: 'Active'}).checked)
    .ok()
    .expect(screen.queryByRole('checkbox', {name: 'Incidents'}).checked)
    .ok()
    .expect(
      screen.queryByRole('checkbox', {name: 'Finished Instances'}).checked
    )
    .notOk()
    .expect(screen.queryByRole('checkbox', {name: 'Completed'}).checked)
    .notOk()
    .expect(screen.queryByRole('checkbox', {name: 'Canceled'}).checked)
    .notOk();

  await t
    .expect(screen.queryByText('There is no Process selected').exists)
    .ok()
    .expect(
      screen.queryByText(
        'To see a Diagram, select a Process in the Filters panel'
      ).exists
    )
    .ok();

  await t.typeText(
    screen.queryByRole('textbox', {
      name: /instance id\(s\) separated by space or comma/i,
    }),
    `${initialData.instanceWithoutAnIncident.processInstanceKey}, ${initialData.instanceWithAnIncident.processInstanceKey}`
  );

  const withinInstancesList = within(screen.queryByTestId('instances-list'));
  await t.expect(withinInstancesList.getAllByRole('row').count).eql(2);

  await t
    .expect(
      withinInstancesList.queryByTestId(
        `INCIDENT-icon-${initialData.instanceWithAnIncident.processInstanceKey}`
      ).exists
    )
    .ok()
    .expect(
      withinInstancesList.queryByTestId(
        `ACTIVE-icon-${initialData.instanceWithoutAnIncident.processInstanceKey}`
      ).exists
    )
    .ok();
});

test('Select flow node in diagram', async (t) => {
  const {initialData} = t.fixtureCtx;
  const instance = initialData.instanceWithoutAnIncident;

  // Filter by Instance ID
  await t.typeText(
    screen.queryByRole('textbox', {
      name: 'Instance Id(s) separated by space or comma',
    }),
    instance.processInstanceKey,
    {paste: true}
  );

  const processCombobox = screen.queryByRole('combobox', {
    name: 'Process',
  });

  // Select "Order Process"
  await t.click(processCombobox).click(
    within(processCombobox).queryByRole('option', {
      name: 'Order process',
    })
  );

  // Select "Ship Articles" flow node
  const shipArticlesTaskId = 'shipArticles';
  await t
    .click(within(screen.queryByTestId('diagram')).queryByText('Ship Articles'))
    .expect(screen.queryByRole('combobox', {name: 'Flow Node'}).value)
    .eql(shipArticlesTaskId)
    .expect(
      screen.queryByText('There are no Instances matching this filter set')
        .exists
    )
    .ok()
    .expect(await getPathname())
    .eql('/instances')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
        ids: instance.processInstanceKey,
        process: 'orderProcess',
        version: '1',
        flowNodeId: shipArticlesTaskId,
      })
    );

  // Select "Check Payment" flow node
  const checkPaymentTaskId = 'checkPayment';
  await t
    .click(within(screen.queryByTestId('diagram')).queryByText('Check payment'))
    .expect(screen.queryByRole('combobox', {name: 'Flow Node'}).value)
    .eql(checkPaymentTaskId)
    .expect(
      within(screen.queryByTestId('instances-list')).getAllByRole('row').count
    )
    .eql(1)
    .expect(await getPathname())
    .eql('/instances')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
        ids: instance.processInstanceKey,
        process: 'orderProcess',
        version: '1',
        flowNodeId: checkPaymentTaskId,
      })
    );
});

test('Wait for process creation', async (t) => {
  await t.openWindow(
    `${config.endpoint}/#/instances?active=true&incidents=true&process=testProcess&version=1`
  );

  await t.expect(screen.queryByTestId('listpanel-skeleton').exists).ok();
  await t.expect(screen.queryByTestId('diagram-spinner').exists).ok();
  await t
    .expect(
      screen
        .queryByRole('combobox', {
          name: 'Process',
        })
        .hasAttribute('disabled')
    )
    .ok();

  await deploy(['./e2e/tests/resources/newProcess.bpmn']);

  await t.expect(screen.queryByTestId('diagram').exists).ok();
  await t.expect(screen.queryByTestId('diagram-spinner').exists).notOk();

  await t.expect(screen.queryByTestId('listpanel-skeleton').exists).notOk();
  await t
    .expect(
      screen.getByText('There are no Instances matching this filter set').exists
    )
    .ok();

  await t
    .expect(
      screen.getByRole('combobox', {name: 'Process'}).hasAttribute('disabled')
    )
    .notOk();

  await t
    .expect(screen.getByRole('combobox', {name: 'Process'}).value)
    .eql('testProcess');
});
