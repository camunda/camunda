/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {config} from '../config';
import {setup} from './Processes.setup';
import {deployProcess} from '../setup-utils';
import {demoUser} from './utils/Roles';
import {wait} from './utils/wait';
import {getPathname} from './utils/getPathname';
import {getSearch} from './utils/getSearch';
import {convertToQueryString} from './utils/convertToQueryString';
import {screen, within} from '@testing-library/testcafe';
import {setProcessesFlyoutTestAttributeLegacy} from './utils/setFlyoutTestAttribute';
import {displayOptionalFilter} from './utils/displayOptionalFilter';
import {validateCheckedState} from './utils/validateCheckedState';
import {
  validateSelectValue,
  validateSelectValueLegacy,
} from './utils/validateSelectValue';
import {processesPage as ProcessesPage} from './PageModels/Processes';
import {IS_COMBOBOX_ENABLED} from '../../src/modules/feature-flags';

fixture('Processes')
  .page(config.endpoint)
  .before(async (ctx) => {
    ctx.initialData = await setup();
    await wait();
  })
  .beforeEach(async (t) => {
    await t
      .useRole(demoUser)
      .maximizeWindow()
      .click(
        screen.queryByRole('link', {
          name: /processes/i,
        })
      );

    if (!IS_COMBOBOX_ENABLED) {
      await setProcessesFlyoutTestAttributeLegacy('processName');
    }
  });

test('Processes Page Initial Load', async (t) => {
  const {initialData} = t.fixtureCtx;
  const {
    runningInstances,
    active,
    incidents,
    finishedInstances,
    completed,
    canceled,
  } = ProcessesPage.Filters;

  await t.click(
    screen.getByRole('link', {
      name: /processes/i,
    })
  );

  await validateCheckedState({
    checked: [runningInstances.field, active.field, incidents.field],
    unChecked: [finishedInstances.field, completed.field, canceled.field],
  });

  await t
    .expect(screen.queryByText('There is no Process selected').exists)
    .ok()
    .expect(
      screen.queryByText(
        'To see a Diagram, select a Process in the Filters panel'
      ).exists
    )
    .ok();

  await displayOptionalFilter('Process Instance Key(s)');

  await ProcessesPage.typeText(
    ProcessesPage.Filters.instanceIds.field,
    `${initialData.instanceWithoutAnIncident.processInstanceKey}, ${initialData.instanceWithAnIncident.processInstanceKey}`
  );

  await t.expect(screen.queryByTestId('data-list').exists).ok();

  const withinInstancesList = within(screen.queryByTestId('data-list'));
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

  await t.click(
    screen.getByRole('link', {
      name: /processes/i,
    })
  );

  await displayOptionalFilter('Process Instance Key(s)');

  // Filter by Process Instance Key
  await ProcessesPage.typeText(
    ProcessesPage.Filters.instanceIds.field,
    instance.processInstanceKey,
    {
      paste: true,
    }
  );

  // Select "Order Process"
  await t.click(ProcessesPage.Filters.processName.field);
  await ProcessesPage.selectProcess('Order process');

  await t.expect(screen.queryByTestId('diagram').exists).ok();

  // Select "Ship Articles" flow node
  const shipArticlesTaskId = 'shipArticles';

  await t.click(
    within(screen.queryByTestId('diagram')).queryByText('Ship Articles')
  );

  await validateSelectValueLegacy(
    ProcessesPage.Filters.flowNode.field,
    'Ship Articles'
  );

  await t
    .expect(
      screen.queryByText('There are no Instances matching this filter set')
        .exists
    )
    .ok()
    .expect(await getPathname())
    .eql('/processes')
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
  await t.click(
    within(screen.queryByTestId('diagram')).queryByText('Check payment')
  );

  await validateSelectValueLegacy(
    ProcessesPage.Filters.flowNode.field,
    'Check payment'
  );

  await t
    .expect(within(screen.queryByTestId('data-list')).getAllByRole('row').count)
    .eql(1)
    .expect(await getPathname())
    .eql('/processes')
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
  await t.navigateTo(
    '/processes?active=true&incidents=true&process=testProcess&version=1'
  );

  await t.expect(screen.queryByTestId('table-skeleton').exists).ok();
  await t.expect(screen.queryByTestId('diagram-spinner').exists).ok();

  if (IS_COMBOBOX_ENABLED) {
    await t
      .expect(ProcessesPage.Filters.processName.field.hasAttribute('disabled'))
      .ok();
  } else {
    await t
      .expect(ProcessesPage.Filters.processName.field.getAttribute('disabled'))
      .eql('true');
  }

  await deployProcess(['newProcess.bpmn']);

  await t.expect(screen.queryByTestId('diagram').exists).ok();
  await t.expect(screen.queryByTestId('diagram-spinner').exists).notOk();

  await t.expect(screen.queryByTestId('table-skeleton').exists).notOk();
  await t
    .expect(
      screen.getByText('There are no Instances matching this filter set').exists
    )
    .ok();

  if (IS_COMBOBOX_ENABLED) {
    await t
      .expect(ProcessesPage.Filters.processName.field.hasAttribute('disabled'))
      .notOk();
  } else {
    await t
      .expect(ProcessesPage.Filters.processName.field.getAttribute('disabled'))
      .eql('false');
  }

  if (IS_COMBOBOX_ENABLED) {
    await validateSelectValue(
      ProcessesPage.Filters.processName.field,
      'Test Process'
    );
  } else {
    await validateSelectValueLegacy(
      ProcessesPage.Filters.processName.field,
      'Test Process'
    );
  }
});
