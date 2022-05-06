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
import {convertToQueryString} from './utils/convertToQueryString';
import {screen, within} from '@testing-library/testcafe';
import {getPathname} from './utils/getPathname';
import {getSearch} from './utils/getSearch';
import {setFlyoutTestAttribute} from './utils/setFlyoutTestAttribute';
import {displayOptionalFilter} from './utils/displayOptionalFilter';
import {processesPage as ProcessesPage} from './PageModels/Processes';
import {validateCheckedState} from './utils/validateCheckedState';
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

test('Instance IDs filter', async (t) => {
  await t.expect(screen.queryByTestId('data-list').exists).ok();

  const instanceId = await within(screen.queryByTestId('data-list'))
    .queryAllByRole('link', {name: /View instance/i})
    .nth(0).innerText;

  await displayOptionalFilter('Instance Id(s)');

  await ProcessesPage.typeText(
    ProcessesPage.Filters.instanceIds.field,
    instanceId.toString(),
    {
      paste: true,
    }
  );

  // wait for filter to be applied, see there is only 1 result
  await t
    .expect(
      within(screen.queryByTestId('data-list')).queryAllByRole('row').count
    )
    .eql(1);

  // changes reflected in the url
  await t
    .expect(await getPathname())
    .eql('/processes')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
        ids: instanceId,
      })
    );

  // result is the one we filtered
  await t
    .expect(
      await within(screen.queryByTestId('data-list'))
        .getAllByRole('link', {name: /View instance/i})
        .nth(0).innerText
    )
    .eql(instanceId);

  await t.click(ProcessesPage.resetFiltersButton);

  // wait for reset filter to be applied, see there is more than one result again
  await t
    .expect(within(screen.queryByTestId('data-list')).getAllByRole('row').count)
    .gt(1);

  // instance ids filter is hidden
  await t.expect(ProcessesPage.Filters.instanceIds.field.exists).notOk();

  // changes reflected in the url
  await t
    .expect(await getPathname())
    .eql('/processes')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
      })
    );
});

test('Parent Instance Id filter', async (t) => {
  const {
    initialData: {callActivityProcessInstance},
  } = t.fixtureCtx;

  await t.click(ProcessesPage.Filters.completed.field);

  await displayOptionalFilter('Parent Instance Id');

  await ProcessesPage.typeText(
    ProcessesPage.Filters.parentInstanceId.field,
    callActivityProcessInstance.processInstanceKey,
    {paste: true}
  );

  // wait for filter to be applied, see there is only 1 result
  await t
    .expect(
      within(screen.queryByTestId('data-list')).queryAllByRole('row').count
    )
    .eql(1);

  // result is the one we filtered
  await t
    .expect(
      await within(screen.queryByTestId('data-list'))
        .getAllByRole('link', {name: /View parent instance/i})
        .nth(0).innerText
    )
    .eql(callActivityProcessInstance.processInstanceKey);

  await t.click(ProcessesPage.resetFiltersButton);

  // wait for reset filter to be applied, see there is more than one result again
  await t
    .expect(within(screen.queryByTestId('data-list')).getAllByRole('row').count)
    .gt(1);

  // parent id filter is hidden
  await t.expect(ProcessesPage.Filters.parentInstanceId.field.exists).notOk();
});

test('Error Message filter', async (t) => {
  await t.expect(screen.queryByTestId('data-list').exists).ok();

  const instanceCount = await within(
    screen.queryByTestId('data-list')
  ).getAllByRole('row').count;

  const errorMessage =
    "failed to evaluate expression 'nonExistingClientId': no variable found for name 'nonExistingClientId'";

  await displayOptionalFilter('Error Message');

  await ProcessesPage.typeText(
    ProcessesPage.Filters.errorMessage.field,
    errorMessage,
    {
      paste: true,
    }
  );

  // wait for filter to be applied, see results are narrowed down.
  await t
    .expect(within(screen.queryByTestId('data-list')).getAllByRole('row').count)
    .lt(instanceCount);

  // changes reflected in the url
  await t
    .expect(await getPathname())
    .eql('/processes')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
        errorMessage,
      })
    );

  await t.click(ProcessesPage.resetFiltersButton);

  // wait for reset filter to be applied, see there is more than one result again.
  await t
    .expect(within(screen.queryByTestId('data-list')).getAllByRole('row').count)
    .eql(instanceCount);

  // error message filter is hidden
  await t.expect(ProcessesPage.Filters.errorMessage.field.exists).notOk();

  // changes reflected in the url
  await t
    .expect(await getPathname())
    .eql('/processes')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
      })
    );
});

test('End Date filter', async (t) => {
  const {
    initialData: {instanceToCancel},
  } = t.fixtureCtx;

  await displayOptionalFilter('Instance Id(s)');

  await ProcessesPage.typeText(
    ProcessesPage.Filters.instanceIds.field,
    instanceToCancel.processInstanceKey,
    {
      paste: true,
    }
  );

  // wait for filter to be applied
  await t
    .expect(within(screen.queryByTestId('data-list')).getAllByRole('row').count)
    .eql(1);
  await t
    .click(screen.queryByRole('button', {name: /cancel instance/i}))
    .click(screen.queryByRole('button', {name: 'Apply'}));

  // wait for operation to be completed
  await t
    .expect(
      screen.queryByText('There are no Instances matching this filter set')
        .exists
    )
    .ok();

  await t.click(ProcessesPage.Filters.finishedInstances.field);

  // wait for filter to be applied
  await t
    .expect(
      within(screen.queryByTestId('data-list')).queryAllByRole('row').count
    )
    .eql(1);

  // get end date from recently canceled instance
  const endDate = await within(screen.queryByTestId('data-list')).queryByTestId(
    'end-time'
  ).innerText;

  // reset the filters to start over
  await t.click(ProcessesPage.resetFiltersButton);

  const instanceCount = await within(
    screen.queryByTestId('data-list')
  ).getAllByRole('row').count;

  await displayOptionalFilter('End Date');

  await t.click(ProcessesPage.Filters.finishedInstances.field);

  await ProcessesPage.typeText(ProcessesPage.Filters.endDate.field, endDate, {
    paste: true,
  });

  // wait for filter to be applied, see results are narrowed down.
  await t
    .expect(within(screen.queryByTestId('data-list')).getAllByRole('row').count)
    .lt(instanceCount);

  await t
    .expect(await getPathname())
    .eql('/processes')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
        completed: 'true',
        canceled: 'true',
        endDate,
      })
    );

  await t.click(ProcessesPage.resetFiltersButton);

  // wait for filter to be applied, see there are more results again.
  await t
    .expect(within(screen.queryByTestId('data-list')).getAllByRole('row').count)
    .eql(instanceCount);

  // end date filter is hidden
  await t.expect(ProcessesPage.Filters.endDate.field.exists).notOk();

  // changes reflected in the url
  await t
    .expect(await getPathname())
    .eql('/processes')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
      })
    );
});

test('Variable filter', async (t) => {
  await displayOptionalFilter('Variable');

  await t.expect(screen.queryByTestId('data-list').exists).ok();

  const instanceCount = await within(
    screen.queryByTestId('data-list')
  ).getAllByRole('row').count;

  await ProcessesPage.typeText(
    ProcessesPage.Filters.variableName.field,
    'filtersTest',
    {
      paste: true,
    }
  );

  await ProcessesPage.typeText(
    ProcessesPage.Filters.variableValue.field,
    '123',
    {
      paste: true,
    }
  );

  // wait for filter to be applied, see results are narrowed down.
  await t
    .expect(within(screen.queryByTestId('data-list')).getAllByRole('row').count)
    .lt(instanceCount);

  await t
    .expect(await getPathname())
    .eql('/processes')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
        variableName: 'filtersTest',
        variableValue: '123',
      })
    );

  await t.click(ProcessesPage.resetFiltersButton);

  // wait for filter to be applied, see there is more than one result again.
  await t
    .expect(within(screen.queryByTestId('data-list')).getAllByRole('row').count)
    .eql(instanceCount);

  // variable name and value filters are hidden
  await t.expect(ProcessesPage.Filters.variableName.field.exists).notOk();
  await t.expect(ProcessesPage.Filters.variableValue.field.exists).notOk();

  await t
    .expect(await getPathname())
    .eql('/processes')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
      })
    );
});

test('Operation ID filter', async (t) => {
  const {
    initialData: {instanceToCancelForOperations},
  } = t.fixtureCtx;

  await displayOptionalFilter('Instance Id(s)');

  await ProcessesPage.typeText(
    ProcessesPage.Filters.instanceIds.field,
    instanceToCancelForOperations.processInstanceKey,
    {
      paste: true,
    }
  );

  // wait for filter to be applied
  await t
    .expect(within(screen.queryByTestId('data-list')).getAllByRole('row').count)
    .eql(1);
  await t
    .click(screen.queryByRole('button', {name: /cancel instance/i}))
    .click(screen.queryByRole('button', {name: 'Apply'}));
  // wait for operation to be completed
  await t
    .expect(
      screen.queryByText('There are no Instances matching this filter set')
        .exists
    )
    .ok();

  await t.click(screen.queryByRole('button', {name: 'Expand Operations'}));
  const operationId = await screen.getAllByTestId('operation-id').nth(0)
    .innerText;

  await t.click(screen.queryByRole('button', {name: 'Collapse Operations'}));

  // reset the filters to start over
  await t.click(ProcessesPage.resetFiltersButton);

  const instanceCount = await within(
    screen.queryByTestId('data-list')
  ).getAllByRole('row').count;

  await displayOptionalFilter('Operation Id');

  await t.click(ProcessesPage.Filters.finishedInstances.field);
  await ProcessesPage.typeText(
    ProcessesPage.Filters.operationId.field,
    operationId,
    {
      paste: true,
    }
  );

  // wait for filter to be applied
  await t
    .expect(within(screen.queryByTestId('data-list')).getAllByRole('row').count)
    .eql(1);

  await t
    .expect(await getPathname())
    .eql('/processes')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
        completed: 'true',
        canceled: 'true',
        operationId,
      })
    );

  await t.click(ProcessesPage.resetFiltersButton);

  // wait for filter to be applied, see there are more results again.
  await t
    .expect(within(screen.queryByTestId('data-list')).getAllByRole('row').count)
    .eql(instanceCount);

  // operation id filter is hidden
  await t.expect(ProcessesPage.Filters.operationId.field.exists).notOk();

  await t
    .expect(await getPathname())
    .eql('/processes')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
      })
    );
});

test('Checkboxes', async (t) => {
  const {
    runningInstances,
    active,
    incidents,
    finishedInstances,
    completed,
    canceled,
  } = ProcessesPage.Filters;

  await t.click(runningInstances.field);

  await validateCheckedState({
    checked: [],
    unChecked: [
      runningInstances.field,
      active.field,
      incidents.field,
      finishedInstances.field,
      completed.field,
      canceled.field,
    ],
  });

  await t.expect(await getPathname()).eql('/processes');
  await t.click(active.field);

  await validateCheckedState({
    checked: [active.field],
    unChecked: [
      runningInstances.field,
      incidents.field,
      finishedInstances.field,
      completed.field,
      canceled.field,
    ],
  });

  await t
    .expect(await getPathname())
    .eql('/processes')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
      })
    );

  await t.click(incidents.field);

  await validateCheckedState({
    checked: [runningInstances.field, active.field, incidents.field],
    unChecked: [finishedInstances.field, completed.field, canceled.field],
  });

  await t
    .expect(await getPathname())
    .eql('/processes')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
      })
    );

  await t.click(finishedInstances.field);

  await validateCheckedState({
    checked: [
      runningInstances.field,
      active.field,
      incidents.field,
      finishedInstances.field,
      completed.field,
      canceled.field,
    ],
    unChecked: [],
  });

  await t
    .expect(await getPathname())
    .eql('/processes')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
        completed: 'true',
        canceled: 'true',
      })
    );

  await t.click(completed.field);

  await validateCheckedState({
    checked: [
      runningInstances.field,
      active.field,
      incidents.field,
      canceled.field,
    ],
    unChecked: [finishedInstances.field, completed.field],
  });

  await t
    .expect(await getPathname())
    .eql('/processes')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
        canceled: 'true',
      })
    );

  await t.click(canceled.field);

  await validateCheckedState({
    checked: [runningInstances.field, active.field, incidents.field],
    unChecked: [finishedInstances.field, completed.field, canceled.field],
  });

  await t
    .expect(await getPathname())
    .eql('/processes')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
      })
    );

  await t.click(finishedInstances.field);

  await validateCheckedState({
    checked: [
      runningInstances.field,
      active.field,
      incidents.field,
      finishedInstances.field,
      completed.field,
      canceled.field,
    ],
    unChecked: [],
  });

  await t
    .expect(await getPathname())
    .eql('/processes')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
        completed: 'true',
        canceled: 'true',
      })
    );

  await t.click(ProcessesPage.resetFiltersButton);

  await validateCheckedState({
    checked: [runningInstances.field, active.field, incidents.field],
    unChecked: [finishedInstances.field, completed.field, canceled.field],
  });

  await t
    .expect(await getPathname())
    .eql('/processes')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
      })
    );
});

test('Process Filter', async (t) => {
  // select a process with multiple versions, see that latest version is selected by default, a diagram is displayed and selected instances are removed
  await t.click(ProcessesPage.Filters.processName.field);

  await ProcessesPage.selectProcess('Process With Multiple Versions');

  await validateSelectValue(
    ProcessesPage.Filters.processVersion.field,
    'Version 2'
  );

  await t
    .expect(await getPathname())
    .eql('/processes')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
        process: 'processWithMultipleVersions',
        version: '2',
      })
    );

  await t.expect(screen.queryByTestId('diagram').exists).ok();

  // select all versions, see that diagram disappeared and selected instances are removed
  await t.click(ProcessesPage.Filters.processVersion.field);

  await ProcessesPage.selectVersion('All');
  await validateSelectValue(ProcessesPage.Filters.processVersion.field, 'All');

  await t
    .expect(screen.queryByTestId('diagram').exists)
    .notOk()
    .expect(
      screen.queryByText(
        'There is more than one Version selected for Process "Process With Multiple Versions"'
      ).exists
    )
    .ok()
    .expect(
      screen.queryByText('To see a Diagram, select a single Version').exists
    )
    .ok();

  await t
    .expect(await getPathname())
    .eql('/processes')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
        process: 'processWithMultipleVersions',
        version: 'all',
      })
    );

  // reset the filters to start over
  await t.click(ProcessesPage.resetFiltersButton);

  // select a process and a flow node
  await t.click(ProcessesPage.Filters.processName.field);
  await ProcessesPage.selectProcess('Process With Multiple Versions');

  await t.click(ProcessesPage.Filters.flowNode.field);
  await ProcessesPage.selectFlowNode('StartEvent_1');

  await validateSelectValue(
    ProcessesPage.Filters.flowNode.field,
    'StartEvent_1'
  );

  await t
    .expect(await getPathname())
    .eql('/processes')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
        process: 'processWithMultipleVersions',
        version: '2',
        flowNodeId: 'StartEvent_1',
      })
    );

  // change process and see flow node filter has been reset
  await t.click(ProcessesPage.Filters.processName.field);
  await ProcessesPage.selectProcess('Order process');

  await validateSelectValue(ProcessesPage.Filters.flowNode.field, '--');

  await t
    .expect(await getPathname())
    .eql('/processes')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
        process: 'orderProcess',
        version: '1',
      })
    );
});

test('Process Filter - Interaction with diagram', async (t) => {
  // select a process that has only one version
  await t
    .expect(ProcessesPage.Filters.processName.field.getAttribute('disabled'))
    .eql('false');
  await t.click(ProcessesPage.Filters.processName.field);
  await ProcessesPage.selectProcess('Order process');
  await validateSelectValue(
    ProcessesPage.Filters.processVersion.field,
    'Version 1'
  );
  await validateSelectValue(ProcessesPage.Filters.flowNode.field, '--');

  await t
    .expect(await getPathname())
    .eql('/processes')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
        process: 'orderProcess',
        version: '1',
      })
    );

  // select a flow node from the diagram
  await t
    .click(
      within(screen.queryByTestId('diagram')).queryByText(/ship articles/i)
    )
    .expect(
      screen.queryByText('There are no Instances matching this filter set')
        .exists
    )
    .ok();

  await validateSelectValue(
    ProcessesPage.Filters.flowNode.field,
    'Ship Articles'
  );

  await t
    .expect(await getPathname())
    .eql('/processes')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
        process: 'orderProcess',
        version: '1',
        flowNodeId: 'shipArticles',
      })
    );

  // select same flow node again and see filter is removed
  await t.click(
    within(screen.queryByTestId('diagram')).queryByText(/ship articles/i)
  );

  await t
    .expect(await getPathname())
    .eql('/processes')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
        process: 'orderProcess',
        version: '1',
      })
    );

  await validateSelectValue(ProcessesPage.Filters.flowNode.field, '--');
});
