/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {config} from '../config';
import {setup} from './ProcessInstancesFilters.setup';
import {demoUser} from './utils/Roles';
import {wait} from './utils/wait';
import {screen, within} from '@testing-library/testcafe';
import {setFlyoutTestAttribute} from './utils/setFlyoutTestAttribute';
import {displayOptionalFilter} from './utils/displayOptionalFilter';
import {processesPage as ProcessesPage} from './PageModels/Processes';
import {validateSelectValue} from './utils/validateSelectValue';

fixture('Process Instances Filters')
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
          name: /view processes/i,
        })
      );

    await setFlyoutTestAttribute('processName');
    await setFlyoutTestAttribute('processVersion');
    await setFlyoutTestAttribute('flowNode');
  });

test('Apply Filters', async (t) => {
  const {
    initialData: {callActivityProcessInstance},
  } = t.fixtureCtx;

  await displayOptionalFilter('Parent Instance Id');
  await ProcessesPage.typeText(
    ProcessesPage.Filters.parentInstanceId.field,
    callActivityProcessInstance.processInstanceKey,
    {paste: true}
  );

  await t
    .expect(
      screen.queryByText('There are no Instances matching this filter set')
        .exists
    )
    .ok();

  await t
    .click(ProcessesPage.Filters.completed.field)
    .expect(screen.queryByText('1 results found').exists)
    .ok();

  // result is the one we filtered
  await t
    .expect(
      await within(screen.queryByTestId('data-list'))
        .getAllByRole('link', {name: /View parent instance/i})
        .nth(0).innerText
    )
    .eql(callActivityProcessInstance.processInstanceKey);

  const endDate = await within(screen.queryByTestId('data-list')).queryByTestId(
    'end-time'
  ).innerText;

  const rowCount = within(screen.queryByTestId('data-list')).getAllByRole(
    'row'
  ).count;

  await t.click(ProcessesPage.resetFiltersButton);

  await t
    .expect(ProcessesPage.Filters.parentInstanceId.field.exists)
    .notOk()
    .expect(rowCount)
    .gt(1);

  await t.click(ProcessesPage.Filters.completed.field);

  let currentRowCount = await rowCount;

  await displayOptionalFilter('End Date');
  await ProcessesPage.typeText(ProcessesPage.Filters.endDate.field, endDate, {
    paste: true,
  });
  await t.expect(rowCount).lt(currentRowCount);

  currentRowCount = await rowCount;

  await t.click(ProcessesPage.resetFiltersButton);
  await t.expect(rowCount).gt(currentRowCount);

  currentRowCount = await rowCount;

  await displayOptionalFilter('Error Message');

  await ProcessesPage.typeText(
    ProcessesPage.Filters.errorMessage.field,
    "failed to evaluate expression 'nonExistingClientId': no variable found for name 'nonExistingClientId'",
    {
      paste: true,
    }
  );

  await t.expect(rowCount).lt(currentRowCount);

  await displayOptionalFilter('Start Date');
  await ProcessesPage.typeText(
    ProcessesPage.Filters.startDate.field,
    '2022-01-01',
    {
      paste: true,
    }
  );
  await t
    .expect(
      screen.queryByText('There are no Instances matching this filter set')
        .exists
    )
    .ok();

  await t.click(screen.queryByRole('button', {name: /reset filters/i}));
  await t
    .expect(
      screen.queryByText('There are no Instances matching this filter set')
        .exists
    )
    .notOk()
    .expect(ProcessesPage.Filters.errorMessage.field.exists)
    .notOk()
    .expect(ProcessesPage.Filters.startDate.field.exists)
    .notOk();
});

test('Interaction between diagram and filters', async (t) => {
  await t.click(ProcessesPage.Filters.processName.field);

  await ProcessesPage.selectProcess('Process With Multiple Versions');

  await validateSelectValue(
    ProcessesPage.Filters.processVersion.field,
    'Version 2'
  );

  await t.click(ProcessesPage.Filters.flowNode.field);
  await ProcessesPage.selectFlowNode('StartEvent_1');

  // change version and see flow node filter has been reset
  await t.click(ProcessesPage.Filters.processVersion.field);
  await ProcessesPage.selectVersion('Version 1');
  await validateSelectValue(ProcessesPage.Filters.flowNode.field, '--');

  await t.click(ProcessesPage.Filters.flowNode.field);
  await ProcessesPage.selectFlowNode('StartEvent_1');
  await t
    .expect(
      screen.queryByText('There are no Instances matching this filter set')
        .exists
    )
    .ok();

  // select another flow node from the diagram
  await t.click(
    within(screen.queryByTestId('diagram')).queryByText(/always fails/i)
  );
  await validateSelectValue(
    ProcessesPage.Filters.flowNode.field,
    'Always fails'
  );

  // select same flow node again and see filter is removed
  await t
    .click(within(screen.queryByTestId('diagram')).queryByText(/always fails/i))
    .expect(
      screen.queryByText('There are no Instances matching this filter set')
        .exists
    )
    .notOk();
  await validateSelectValue(ProcessesPage.Filters.flowNode.field, '--');
});
