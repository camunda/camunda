/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {config} from '../config';
import {
  setup,
  cmRunningInstancesCheckbox,
  cmActiveCheckbox,
  cmIncidentsCheckbox,
  cmFinishedInstancesCheckbox,
  cmCompletedCheckbox,
  cmCanceledCheckbox,
  cmParentInstanceIdField,
  cmErrorMessageField,
  cmOperationIdField,
  cmStartDateField,
  cmEndDateField,
  cmVariableNameField,
  cmVariableValueField,
  cmInstanceIdsField,
} from './Filters.setup';
import {demoUser} from './utils/Roles';
import {wait} from './utils/wait';
import {convertToQueryString} from './utils/convertToQueryString';
import {screen, within} from '@testing-library/testcafe';
import {getPathname} from './utils/getPathname';
import {getSearch} from './utils/getSearch';
import {IS_NEW_FILTERS_FORM} from '../../src/modules/feature-flags';
import {setFlyoutTestAttribute} from './utils/setFlyoutTestAttribute';
import {displayOptionalFilter} from './utils/displayOptionalFilter';

fixture('Filters')
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
          name: /view instances/i,
        })
      );

    if (IS_NEW_FILTERS_FORM) {
      await setFlyoutTestAttribute('processName');
      await setFlyoutTestAttribute('processVersion');
      await setFlyoutTestAttribute('flowNode');
    }
  });

test('Navigating in header should affect filters and url correctly', async (t) => {
  await t.click(
    screen.queryByRole('link', {
      name: /view instances/i,
    })
  );

  await t
    .expect(await getPathname())
    .eql('/instances')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
      })
    );

  if (IS_NEW_FILTERS_FORM) {
    await t
      .expect(cmRunningInstancesCheckbox.hasClass('checked'))
      .ok()
      .expect(cmActiveCheckbox.hasClass('checked'))
      .ok()
      .expect(cmIncidentsCheckbox.hasClass('checked'))
      .ok()
      .expect(cmFinishedInstancesCheckbox.hasClass('checked'))
      .notOk()
      .expect(cmCompletedCheckbox.hasClass('checked'))
      .notOk()
      .expect(cmCanceledCheckbox.hasClass('checked'))
      .notOk();
  } else {
    await t
      .expect(
        screen.queryByRole('checkbox', {name: 'Running Instances'}).checked
      )
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
  }
});

test('Instance IDs filter', async (t) => {
  await t.expect(screen.queryByTestId('instances-list').exists).ok();

  const instanceId = await within(screen.queryByTestId('instances-list'))
    .queryAllByRole('link', {name: /View instance/i})
    .nth(0).innerText;

  if (IS_NEW_FILTERS_FORM) {
    await displayOptionalFilter('Instance Id(s)');
  }

  const instanceIdsField = IS_NEW_FILTERS_FORM
    ? cmInstanceIdsField
    : screen.queryByRole('textbox', {
        name: 'Instance Id(s) separated by space or comma',
      });

  await t.typeText(instanceIdsField, instanceId.toString(), {
    paste: true,
  });

  // wait for filter to be applied, see there is only 1 result
  await t
    .expect(
      within(screen.queryByTestId('instances-list')).queryAllByRole('row').count
    )
    .eql(1);

  // changes reflected in the url
  await t
    .expect(await getPathname())
    .eql('/instances')
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
      await within(screen.queryByTestId('instances-list'))
        .getAllByRole('link', {name: /View instance/i})
        .nth(0).innerText
    )
    .eql(instanceId);

  await t.click(screen.queryByRole('button', {name: /reset filters/i}));

  // wait for reset filter to be applied, see there is more than one result again
  await t
    .expect(
      within(screen.queryByTestId('instances-list')).getAllByRole('row').count
    )
    .gt(1);

  if (IS_NEW_FILTERS_FORM) {
    // instance ids filter is hidden
    await t.expect(screen.queryByTestId('filter-instance-ids').exists).notOk();
  } else {
    // filter has been reset
    await t.expect(instanceIdsField.value).eql('');
  }

  // changes reflected in the url
  await t
    .expect(await getPathname())
    .eql('/instances')
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

  const completedCheckbox = IS_NEW_FILTERS_FORM
    ? cmCompletedCheckbox
    : screen.queryByRole('checkbox', {name: 'Completed'});

  await t.click(completedCheckbox);

  if (IS_NEW_FILTERS_FORM) {
    await displayOptionalFilter('Parent Instance Id');
  }

  const parentInstanceIdField = IS_NEW_FILTERS_FORM
    ? cmParentInstanceIdField
    : screen.queryByRole('textbox', {name: 'Parent Instance Id'});

  await t.typeText(
    parentInstanceIdField,
    callActivityProcessInstance.processInstanceKey,
    {paste: true}
  );

  // wait for filter to be applied, see there is only 1 result
  await t
    .expect(
      within(screen.queryByTestId('instances-list')).queryAllByRole('row').count
    )
    .eql(1);

  // result is the one we filtered
  await t
    .expect(
      await within(screen.queryByTestId('instances-list'))
        .getAllByRole('link', {name: /View parent instance/i})
        .nth(0).innerText
    )
    .eql(callActivityProcessInstance.processInstanceKey);

  await t.click(screen.queryByRole('button', {name: /reset filters/i}));

  // wait for reset filter to be applied, see there is more than one result again
  await t
    .expect(
      within(screen.queryByTestId('instances-list')).getAllByRole('row').count
    )
    .gt(1);

  if (IS_NEW_FILTERS_FORM) {
    // parent id filter is hidden
    await t
      .expect(screen.queryByTestId('filter-parent-instance-id').exists)
      .notOk();
  } else {
    // filter has been reset
    await t.expect(parentInstanceIdField.value).eql('');
  }
});

test('Error Message filter', async (t) => {
  await t.expect(screen.queryByTestId('instances-list').exists).ok();

  const instanceCount = await within(
    screen.queryByTestId('instances-list')
  ).getAllByRole('row').count;

  const errorMessage =
    "failed to evaluate expression 'nonExistingClientId': no variable found for name 'nonExistingClientId'";

  if (IS_NEW_FILTERS_FORM) {
    await displayOptionalFilter('Error Message');
  }

  const errorMessageField = IS_NEW_FILTERS_FORM
    ? cmErrorMessageField
    : screen.queryByRole('textbox', {name: /error message/i});

  await t.typeText(errorMessageField, errorMessage, {
    paste: true,
  });

  // wait for filter to be applied, see results are narrowed down.
  await t
    .expect(
      within(screen.queryByTestId('instances-list')).getAllByRole('row').count
    )
    .lt(instanceCount);

  // changes reflected in the url
  await t
    .expect(await getPathname())
    .eql('/instances')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
        errorMessage,
      })
    );

  await t.click(screen.queryByRole('button', {name: /reset filters/i}));

  // wait for reset filter to be applied, see there is more than one result again.
  await t
    .expect(
      within(screen.queryByTestId('instances-list')).getAllByRole('row').count
    )
    .eql(instanceCount);

  if (IS_NEW_FILTERS_FORM) {
    // error message filter is hidden
    await t.expect(screen.queryByTestId('filter-error-message').exists).notOk();
  } else {
    // filter has been reset
    await t.expect(errorMessageField.value).eql('');
  }

  // changes reflected in the url
  await t
    .expect(await getPathname())
    .eql('/instances')
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

  if (IS_NEW_FILTERS_FORM) {
    await displayOptionalFilter('Instance Id(s)');
  }

  const instanceIdsField = IS_NEW_FILTERS_FORM
    ? cmInstanceIdsField
    : screen.queryByRole('textbox', {
        name: 'Instance Id(s) separated by space or comma',
      });

  await t.typeText(instanceIdsField, instanceToCancel.processInstanceKey, {
    paste: true,
  });

  // wait for filter to be applied
  await t
    .expect(
      within(screen.queryByTestId('instances-list')).getAllByRole('row').count
    )
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

  const finishedInstancesCheckbox = IS_NEW_FILTERS_FORM
    ? cmFinishedInstancesCheckbox
    : screen.queryByRole('checkbox', {name: 'Finished Instances'});

  await t.click(finishedInstancesCheckbox);

  // wait for filter to be applied
  await t
    .expect(
      within(screen.queryByTestId('instances-list')).getAllByRole('row').count
    )
    .eql(1);

  // get end date from recently canceled instance
  const endDate = await within(
    screen.queryByTestId('instances-list')
  ).queryByTestId('end-time').innerText;

  // reset the filters to start over
  await t.click(screen.queryByRole('button', {name: /reset filters/i}));

  const instanceCount = await within(
    screen.queryByTestId('instances-list')
  ).getAllByRole('row').count;

  if (IS_NEW_FILTERS_FORM) {
    await displayOptionalFilter('End Date');
  }

  const endDateField = IS_NEW_FILTERS_FORM
    ? cmEndDateField
    : screen.queryByRole('textbox', {name: /end date/i});

  await t.click(finishedInstancesCheckbox).typeText(endDateField, endDate, {
    paste: true,
  });

  // wait for filter to be applied, see results are narrowed down.
  await t
    .expect(
      within(screen.queryByTestId('instances-list')).getAllByRole('row').count
    )
    .lt(instanceCount);

  await t
    .expect(await getPathname())
    .eql('/instances')
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

  await t.click(screen.queryByRole('button', {name: /reset filters/i}));

  // wait for filter to be applied, see there are more results again.
  await t
    .expect(
      within(screen.queryByTestId('instances-list')).getAllByRole('row').count
    )
    .eql(instanceCount);

  if (IS_NEW_FILTERS_FORM) {
    // end date filter is hidden
    await t.expect(screen.queryByTestId('filter-end-date').exists).notOk();
  } else {
    // filter has been reset
    await t.expect(endDateField.value).eql('');
  }

  // changes reflected in the url
  await t
    .expect(await getPathname())
    .eql('/instances')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
      })
    );
});

test('Variable filter', async (t) => {
  if (IS_NEW_FILTERS_FORM) {
    await displayOptionalFilter('Variable');
  }

  const variableNameField = IS_NEW_FILTERS_FORM
    ? cmVariableNameField
    : screen.queryByRole('textbox', {name: /variable/i});

  const variableValueField = IS_NEW_FILTERS_FORM
    ? cmVariableValueField
    : screen.queryByRole('textbox', {name: /value/i});

  await t.expect(screen.queryByTestId('instances-list').exists).ok();

  const instanceCount = await within(
    screen.queryByTestId('instances-list')
  ).getAllByRole('row').count;

  await t.typeText(variableNameField, 'filtersTest', {
    paste: true,
  });

  await t.typeText(variableValueField, '123', {
    paste: true,
  });

  // wait for filter to be applied, see results are narrowed down.
  await t
    .expect(
      within(screen.queryByTestId('instances-list')).getAllByRole('row').count
    )
    .lt(instanceCount);

  await t
    .expect(await getPathname())
    .eql('/instances')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
        variableName: 'filtersTest',
        variableValue: '123',
      })
    );

  await t.click(screen.queryByRole('button', {name: /reset filters/i}));

  // wait for filter to be applied, see there is more than one result again.
  await t
    .expect(
      within(screen.queryByTestId('instances-list')).getAllByRole('row').count
    )
    .eql(instanceCount);

  if (IS_NEW_FILTERS_FORM) {
    // variable name and value filters are hidden
    await t.expect(screen.queryByTestId('filter-variable-name').exists).notOk();
    await t
      .expect(screen.queryByTestId('filter-variable-value').exists)
      .notOk();
  } else {
    // filter has been reset
    await t.expect(variableNameField.value).eql('');

    await t.expect(variableValueField.value).eql('');
  }

  await t
    .expect(await getPathname())
    .eql('/instances')
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

  if (IS_NEW_FILTERS_FORM) {
    await displayOptionalFilter('Instance Id(s)');
  }

  const instanceIdsField = IS_NEW_FILTERS_FORM
    ? cmInstanceIdsField
    : screen.queryByRole('textbox', {
        name: 'Instance Id(s) separated by space or comma',
      });

  await t.typeText(
    instanceIdsField,
    instanceToCancelForOperations.processInstanceKey,
    {
      paste: true,
    }
  );

  // wait for filter to be applied
  await t
    .expect(
      within(screen.queryByTestId('instances-list')).getAllByRole('row').count
    )
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
  await t.click(screen.queryByRole('button', {name: /reset filters/i}));

  const instanceCount = await within(
    screen.queryByTestId('instances-list')
  ).getAllByRole('row').count;

  if (IS_NEW_FILTERS_FORM) {
    await displayOptionalFilter('Operation Id');
  }

  const operationIdField = IS_NEW_FILTERS_FORM
    ? cmOperationIdField
    : screen.queryByRole('textbox', {name: /operation id/i});

  const finishedInstancesCheckbox = IS_NEW_FILTERS_FORM
    ? cmFinishedInstancesCheckbox
    : screen.queryByRole('checkbox', {name: 'Finished Instances'});

  await t
    .click(finishedInstancesCheckbox)
    .typeText(operationIdField, operationId, {
      paste: true,
    });

  // wait for filter to be applied
  await t
    .expect(
      within(screen.queryByTestId('instances-list')).getAllByRole('row').count
    )
    .eql(1);

  await t
    .expect(await getPathname())
    .eql('/instances')
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

  await t.click(screen.queryByRole('button', {name: /reset filters/i}));

  // wait for filter to be applied, see there are more results again.
  await t
    .expect(
      within(screen.queryByTestId('instances-list')).getAllByRole('row').count
    )
    .eql(instanceCount);

  if (IS_NEW_FILTERS_FORM) {
    // operation id filter is hidden
    await t.expect(screen.queryByTestId('filter-operation-id').exists).notOk();
  } else {
    // filter has been reset
    await t.expect(operationIdField.value).eql('');
  }

  await t
    .expect(await getPathname())
    .eql('/instances')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
      })
    );
});

test('Checkboxes', async (t) => {
  if (IS_NEW_FILTERS_FORM) {
    await t
      .click(cmRunningInstancesCheckbox)
      .expect(cmRunningInstancesCheckbox.hasClass('checked'))
      .notOk()
      .expect(cmActiveCheckbox.hasClass('checked'))
      .notOk()
      .expect(cmIncidentsCheckbox.hasClass('checked'))
      .notOk()
      .expect(cmFinishedInstancesCheckbox.hasClass('checked'))
      .notOk()
      .expect(cmCompletedCheckbox.hasClass('checked'))
      .notOk()
      .expect(cmCanceledCheckbox.hasClass('checked'))
      .notOk();
  } else {
    await t
      .click(screen.queryByRole('checkbox', {name: 'Running Instances'}))
      .expect(
        screen.queryByRole('checkbox', {name: 'Running Instances'}).checked
      )
      .notOk()
      .expect(screen.queryByRole('checkbox', {name: 'Active'}).checked)
      .notOk()
      .expect(screen.queryByRole('checkbox', {name: 'Incidents'}).checked)
      .notOk()
      .expect(
        screen.queryByRole('checkbox', {name: 'Finished Instances'}).checked
      )
      .notOk()
      .expect(screen.queryByRole('checkbox', {name: 'Completed'}).checked)
      .notOk()
      .expect(screen.queryByRole('checkbox', {name: 'Canceled'}).checked)
      .notOk();
  }

  await t.expect(await getPathname()).eql('/instances');

  if (IS_NEW_FILTERS_FORM) {
    await t
      .click(cmActiveCheckbox)
      .expect(cmRunningInstancesCheckbox.hasClass('checked'))
      .notOk()
      .expect(cmActiveCheckbox.hasClass('checked'))
      .ok()
      .expect(cmIncidentsCheckbox.hasClass('checked'))
      .notOk()
      .expect(cmFinishedInstancesCheckbox.hasClass('checked'))
      .notOk()
      .expect(cmCompletedCheckbox.hasClass('checked'))
      .notOk()
      .expect(cmCanceledCheckbox.hasClass('checked'))
      .notOk();
  } else {
    await t
      .click(screen.queryByRole('checkbox', {name: 'Active'}))
      .expect(
        screen.queryByRole('checkbox', {name: 'Running Instances'}).checked
      )
      .notOk()
      .expect(screen.queryByRole('checkbox', {name: 'Active'}).checked)
      .ok()
      .expect(screen.queryByRole('checkbox', {name: 'Incidents'}).checked)
      .notOk()
      .expect(
        screen.queryByRole('checkbox', {name: 'Finished Instances'}).checked
      )
      .notOk()
      .expect(screen.queryByRole('checkbox', {name: 'Completed'}).checked)
      .notOk()
      .expect(screen.queryByRole('checkbox', {name: 'Canceled'}).checked)
      .notOk();
  }

  await t
    .expect(await getPathname())
    .eql('/instances')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
      })
    );

  if (IS_NEW_FILTERS_FORM) {
    await t
      .click(cmIncidentsCheckbox)
      .expect(cmRunningInstancesCheckbox.hasClass('checked'))
      .ok()
      .expect(cmActiveCheckbox.hasClass('checked'))
      .ok()
      .expect(cmIncidentsCheckbox.hasClass('checked'))
      .ok()
      .expect(cmFinishedInstancesCheckbox.hasClass('checked'))
      .notOk()
      .expect(cmCompletedCheckbox.hasClass('checked'))
      .notOk()
      .expect(cmCanceledCheckbox.hasClass('checked'))
      .notOk();
  } else {
    await t
      .click(screen.queryByRole('checkbox', {name: 'Incidents'}))
      .expect(
        screen.queryByRole('checkbox', {name: 'Running Instances'}).checked
      )
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
  }

  await t
    .expect(await getPathname())
    .eql('/instances')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
      })
    );

  if (IS_NEW_FILTERS_FORM) {
    await t
      .click(cmFinishedInstancesCheckbox)
      .expect(cmRunningInstancesCheckbox.hasClass('checked'))
      .ok()
      .expect(cmActiveCheckbox.hasClass('checked'))
      .ok()
      .expect(cmIncidentsCheckbox.hasClass('checked'))
      .ok()
      .expect(cmFinishedInstancesCheckbox.hasClass('checked'))
      .ok()
      .expect(cmCompletedCheckbox.hasClass('checked'))
      .ok()
      .expect(cmCanceledCheckbox.hasClass('checked'))
      .ok();
  } else {
    await t
      .click(screen.queryByRole('checkbox', {name: 'Finished Instances'}))
      .expect(
        screen.queryByRole('checkbox', {name: 'Running Instances'}).checked
      )
      .ok()
      .expect(screen.queryByRole('checkbox', {name: 'Active'}).checked)
      .ok()
      .expect(screen.queryByRole('checkbox', {name: 'Incidents'}).checked)
      .ok()
      .expect(
        screen.queryByRole('checkbox', {name: 'Finished Instances'}).checked
      )
      .ok()
      .expect(screen.queryByRole('checkbox', {name: 'Completed'}).checked)
      .ok()
      .expect(screen.queryByRole('checkbox', {name: 'Canceled'}).checked)
      .ok();
  }

  await t
    .expect(await getPathname())
    .eql('/instances')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
        completed: 'true',
        canceled: 'true',
      })
    );

  if (IS_NEW_FILTERS_FORM) {
    await t
      .click(cmCompletedCheckbox)
      .expect(cmRunningInstancesCheckbox.hasClass('checked'))
      .ok()
      .expect(cmActiveCheckbox.hasClass('checked'))
      .ok()
      .expect(cmIncidentsCheckbox.hasClass('checked'))
      .ok()
      .expect(cmFinishedInstancesCheckbox.hasClass('checked'))
      .notOk()
      .expect(cmCompletedCheckbox.hasClass('checked'))
      .notOk()
      .expect(cmCanceledCheckbox.hasClass('checked'))
      .ok();
  } else {
    await t
      .click(screen.queryByRole('checkbox', {name: 'Completed'}))
      .expect(
        screen.queryByRole('checkbox', {name: 'Running Instances'}).checked
      )
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
      .ok();
  }

  await t
    .expect(await getPathname())
    .eql('/instances')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
        canceled: 'true',
      })
    );

  if (IS_NEW_FILTERS_FORM) {
    await t
      .click(cmCanceledCheckbox)
      .expect(cmRunningInstancesCheckbox.hasClass('checked'))
      .ok()
      .expect(cmActiveCheckbox.hasClass('checked'))
      .ok()
      .expect(cmIncidentsCheckbox.hasClass('checked'))
      .ok()
      .expect(cmFinishedInstancesCheckbox.hasClass('checked'))
      .notOk()
      .expect(cmCompletedCheckbox.hasClass('checked'))
      .notOk()
      .expect(cmCanceledCheckbox.hasClass('checked'))
      .notOk();
  } else {
    await t
      .click(screen.queryByRole('checkbox', {name: 'Canceled'}))
      .expect(
        screen.queryByRole('checkbox', {name: 'Running Instances'}).checked
      )
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
  }

  await t
    .expect(await getPathname())
    .eql('/instances')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
      })
    );

  if (IS_NEW_FILTERS_FORM) {
    await t
      .click(cmFinishedInstancesCheckbox)
      .expect(cmRunningInstancesCheckbox.hasClass('checked'))
      .ok()
      .expect(cmActiveCheckbox.hasClass('checked'))
      .ok()
      .expect(cmIncidentsCheckbox.hasClass('checked'))
      .ok()
      .expect(cmFinishedInstancesCheckbox.hasClass('checked'))
      .ok()
      .expect(cmCompletedCheckbox.hasClass('checked'))
      .ok()
      .expect(cmCanceledCheckbox.hasClass('checked'))
      .ok();
  } else {
    await t
      .click(screen.queryByRole('checkbox', {name: 'Finished Instances'}))
      .expect(
        screen.queryByRole('checkbox', {name: 'Running Instances'}).checked
      )
      .ok()
      .expect(screen.queryByRole('checkbox', {name: 'Active'}).checked)
      .ok()
      .expect(screen.queryByRole('checkbox', {name: 'Incidents'}).checked)
      .ok()
      .expect(
        screen.queryByRole('checkbox', {name: 'Finished Instances'}).checked
      )
      .ok()
      .expect(screen.queryByRole('checkbox', {name: 'Completed'}).checked)
      .ok()
      .expect(screen.queryByRole('checkbox', {name: 'Canceled'}).checked)
      .ok();
  }

  await t
    .expect(await getPathname())
    .eql('/instances')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
        completed: 'true',
        canceled: 'true',
      })
    );
  await t.click(screen.queryByRole('button', {name: /reset filters/i}));

  if (IS_NEW_FILTERS_FORM) {
    await t
      .expect(cmRunningInstancesCheckbox.hasClass('checked'))
      .ok()
      .expect(cmActiveCheckbox.hasClass('checked'))
      .ok()
      .expect(cmIncidentsCheckbox.hasClass('checked'))
      .ok()
      .expect(cmFinishedInstancesCheckbox.hasClass('checked'))
      .notOk()
      .expect(cmCompletedCheckbox.hasClass('checked'))
      .notOk()
      .expect(cmCanceledCheckbox.hasClass('checked'))
      .notOk();
  } else {
    await t
      .expect(
        screen.queryByRole('checkbox', {name: 'Running Instances'}).checked
      )
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
  }

  await t
    .expect(await getPathname())
    .eql('/instances')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
      })
    );
});

test('Process Filter', async (t) => {
  const processCombobox = IS_NEW_FILTERS_FORM
    ? screen.queryByTestId('filter-process-name')
    : screen.queryByRole('combobox', {
        name: 'Process',
      });

  const processVersionCombobox = IS_NEW_FILTERS_FORM
    ? screen.queryByTestId('filter-process-version')
    : screen.queryByRole('combobox', {
        name: 'Process Version',
      });

  const flowNodeCombobox = IS_NEW_FILTERS_FORM
    ? screen.queryByTestId('filter-flow-node')
    : screen.queryByRole('combobox', {
        name: /flow node/i,
      });

  // select a process with multiple versions, see that latest version is selected by default, a diagram is displayed and selected instances are removed
  await t.click(processCombobox).click(
    IS_NEW_FILTERS_FORM
      ? within(
          screen.queryByTestId('cm-flyout-process-name').shadowRoot()
        ).queryByText('Process With Multiple Versions')
      : within(processCombobox).queryByRole('option', {
          name: 'Process With Multiple Versions',
        })
  );

  if (IS_NEW_FILTERS_FORM) {
    await t
      .expect(
        within(
          screen.queryByTestId('filter-process-version').shadowRoot()
        ).queryByText('Version 2').exists
      )
      .ok();
  } else {
    await t.expect(processVersionCombobox.value).eql('2');
  }

  await t
    .expect(await getPathname())
    .eql('/instances')
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
  await t.click(processVersionCombobox);

  await t.click(
    IS_NEW_FILTERS_FORM
      ? within(
          screen.queryByTestId('cm-flyout-process-version').shadowRoot()
        ).queryByText('All')
      : screen.queryByRole('option', {name: 'All versions'})
  );

  if (IS_NEW_FILTERS_FORM) {
    await t
      .expect(
        within(
          screen.queryByTestId('filter-process-version').shadowRoot()
        ).queryByText('All').exists
      )
      .ok();
  } else {
    await t
      .expect(screen.queryByRole('combobox', {name: 'Process Version'}).value)
      .eql('all');
  }

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
    .eql('/instances')
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
  await t.click(screen.queryByRole('button', {name: /reset filters/i}));

  // select a process and a flow node
  await t
    .click(processCombobox)
    .click(
      IS_NEW_FILTERS_FORM
        ? within(
            screen.queryByTestId('cm-flyout-process-name').shadowRoot()
          ).queryByText('Process With Multiple Versions')
        : within(processCombobox).queryByRole('option', {
            name: 'Process With Multiple Versions',
          })
    )
    .click(flowNodeCombobox)
    .click(
      IS_NEW_FILTERS_FORM
        ? within(
            screen.queryByTestId('cm-flyout-flow-node').shadowRoot()
          ).queryByText('StartEvent_1')
        : screen.queryByRole('option', {
            name: 'StartEvent_1',
          })
    );

  if (IS_NEW_FILTERS_FORM) {
    await t
      .expect(
        within(flowNodeCombobox.shadowRoot()).queryByText('StartEvent_1').exists
      )
      .ok();
  } else {
    await t.expect(flowNodeCombobox.value).eql('StartEvent_1');
  }

  await t
    .expect(await getPathname())
    .eql('/instances')
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
  await t.click(processCombobox).click(
    IS_NEW_FILTERS_FORM
      ? within(
          screen.queryByTestId('cm-flyout-process-name').shadowRoot()
        ).queryByText('Order process')
      : within(processCombobox).queryByRole('option', {
          name: 'Order process',
        })
  );

  if (IS_NEW_FILTERS_FORM) {
    await t
      .expect(within(flowNodeCombobox.shadowRoot()).queryByText('--').exists)
      .ok();
  } else {
    await t.expect(flowNodeCombobox.value).eql('');
  }

  await t
    .expect(await getPathname())
    .eql('/instances')
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
  const processCombobox = IS_NEW_FILTERS_FORM
    ? screen.queryByTestId('filter-process-name')
    : screen.queryByRole('combobox', {
        name: 'Process',
      });

  const processVersionCombobox = IS_NEW_FILTERS_FORM
    ? screen.queryByTestId('filter-process-version')
    : screen.queryByRole('combobox', {
        name: 'Process Version',
      });

  const flowNodeCombobox = IS_NEW_FILTERS_FORM
    ? screen.queryByTestId('filter-flow-node')
    : screen.queryByRole('combobox', {
        name: /flow node/i,
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

  if (IS_NEW_FILTERS_FORM) {
    await t
      .expect(processVersionCombobox.getAttribute('disabled'))
      .eql('true')
      .expect(flowNodeCombobox.getAttribute('disabled'))
      .eql('true')
      .expect(
        within(processVersionCombobox.shadowRoot()).queryByText('All').exists
      )
      .ok()
      .expect(within(flowNodeCombobox.shadowRoot()).queryByText('--').exists)
      .ok();
  } else {
    await t
      .expect(processVersionCombobox.hasAttribute('disabled'))
      .ok()
      .expect(flowNodeCombobox.hasAttribute('disabled'))
      .ok()
      .expect(processVersionCombobox.value)
      .eql('')
      .expect(flowNodeCombobox.value)
      .eql('');
  }

  await t
    .expect(await getPathname())
    .eql('/instances')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
      })
    );

  // select a process that has only one version
  await t.click(processCombobox).click(
    IS_NEW_FILTERS_FORM
      ? within(
          screen.queryByTestId('cm-flyout-process-name').shadowRoot()
        ).queryByText('Order process')
      : within(processCombobox).queryByRole('option', {
          name: 'Order process',
        })
  );

  await t
    .expect(screen.queryByTestId('diagram').exists)
    .ok()
    .expect(screen.queryByText('There is no Process selected').exists)
    .notOk()
    .expect(
      screen.queryByText(
        'To see a Diagram, select a Process in the Filters panel'
      ).exists
    )
    .notOk();

  if (IS_NEW_FILTERS_FORM) {
    await t
      .expect(processCombobox.getAttribute('disabled'))
      .eql('false')
      .expect(
        within(processVersionCombobox.shadowRoot()).queryByText('Version 1')
          .exists
      )
      .ok()
      .expect(within(flowNodeCombobox.shadowRoot()).queryByText('--').exists)
      .ok();
  } else {
    await t
      .expect(flowNodeCombobox.hasAttribute('disabled'))
      .notOk()
      .expect(processVersionCombobox.value)
      .eql('1')
      .expect(flowNodeCombobox.value)
      .eql('');
  }

  await t
    .expect(await getPathname())
    .eql('/instances')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
        process: 'orderProcess',
        version: '1',
      })
    );

  // select a flow node without an instance from the diagram
  await t
    .click(
      within(screen.queryByTestId('diagram')).queryByText(/ship articles/i)
    )
    .expect(
      screen.queryByText('There are no Instances matching this filter set')
        .exists
    )
    .ok();

  if (IS_NEW_FILTERS_FORM) {
    await t

      .expect(
        within(flowNodeCombobox.shadowRoot()).queryByText('Ship Articles')
          .exists
      )
      .ok();
  } else {
    await t.expect(flowNodeCombobox.value).eql('shipArticles');
  }

  await t
    .expect(await getPathname())
    .eql('/instances')
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

  // select a flow node with an instance from the diagram
  await t
    .click(
      within(screen.queryByTestId('diagram')).queryByText(/check payment/i)
    )
    .expect(
      screen.queryByText('There are no Instances matching this filter set')
        .exists
    )
    .notOk();

  if (IS_NEW_FILTERS_FORM) {
    await t
      .expect(
        within(flowNodeCombobox.shadowRoot()).queryByText('Check payment')
          .exists
      )
      .ok();
  } else {
    await t.expect(flowNodeCombobox.value).eql('checkPayment');
  }

  await t
    .expect(await getPathname())
    .eql('/instances')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
        process: 'orderProcess',
        version: '1',
        flowNodeId: 'checkPayment',
      })
    );

  // select same flow node again and see filter is removed
  await t.click(
    within(screen.queryByTestId('diagram')).queryByText(/check payment/i)
  );

  await t
    .expect(await getPathname())
    .eql('/instances')
    .expect(await getSearch())
    .eql(
      convertToQueryString({
        active: 'true',
        incidents: 'true',
        process: 'orderProcess',
        version: '1',
      })
    );

  if (IS_NEW_FILTERS_FORM) {
    await t
      .expect(within(flowNodeCombobox.shadowRoot()).queryByText('--').exists)
      .ok();
  } else {
    await t.expect(flowNodeCombobox.value).eql('');
  }
});

test('Should set filters from url', async (t) => {
  const variableNameField = IS_NEW_FILTERS_FORM
    ? cmVariableNameField
    : screen.queryByRole('textbox', {name: /variable/i});

  const variableValueField = IS_NEW_FILTERS_FORM
    ? cmVariableValueField
    : screen.queryByRole('textbox', {name: /value/i});

  await t.navigateTo(
    `/instances?${convertToQueryString({
      active: 'true',
      incidents: 'true',
      completed: 'true',
      canceled: 'true',
      ids: '2251799813685255',
      errorMessage: 'some error message',
      startDate: '2020-09-10 18:41:44',
      endDate: '2020-12-12 12:12:12',
      version: '2',
      process: 'processWithMultipleVersions',
      variableName: 'test',
      variableValue: '123',
      operationId: '5be8a137-fbb4-4c54-964c-9c7be98b80e6',
      flowNodeId: 'alwaysFails',
      parentInstanceId: '2251799813685731',
    })}`
  );

  const parentInstanceIdField = IS_NEW_FILTERS_FORM
    ? cmParentInstanceIdField
    : screen.queryByRole('textbox', {name: 'Parent Instance Id'});

  const errorMessageField = IS_NEW_FILTERS_FORM
    ? cmErrorMessageField
    : screen.queryByRole('textbox', {name: /error message/i});

  const startDateField = IS_NEW_FILTERS_FORM
    ? cmStartDateField
    : screen.queryByRole('textbox', {name: /start date/i});

  const endDateField = IS_NEW_FILTERS_FORM
    ? cmEndDateField
    : screen.queryByRole('textbox', {name: /end date/i});

  const operationIdField = IS_NEW_FILTERS_FORM
    ? cmOperationIdField
    : screen.queryByRole('textbox', {name: /operation id/i});

  const instanceIdsField = IS_NEW_FILTERS_FORM
    ? cmInstanceIdsField
    : screen.queryByRole('textbox', {
        name: 'Instance Id(s) separated by space or comma',
      });

  if (IS_NEW_FILTERS_FORM) {
    await t
      .expect(
        within(
          screen.queryByTestId('filter-process-name').shadowRoot()
        ).queryByText('Process With Multiple Versions').exists
      )
      .ok()
      .expect(
        within(
          screen.queryByTestId('filter-process-version').shadowRoot()
        ).queryByText('Version 2').exists
      )
      .ok()
      .expect(
        within(
          screen.queryByTestId('filter-flow-node').shadowRoot()
        ).queryByText('Always fails').exists
      )
      .ok();
  } else {
    await t
      .expect(
        screen.queryByRole('combobox', {
          name: 'Process',
        }).value
      )
      .eql('processWithMultipleVersions')
      .expect(screen.queryByRole('combobox', {name: 'Process Version'}).value)
      .eql('2')
      .expect(screen.queryByRole('combobox', {name: /flow node/i}).value)
      .eql('alwaysFails');
  }

  await t
    .expect(instanceIdsField.value)
    .eql('2251799813685255')
    .expect(parentInstanceIdField.value)
    .eql('2251799813685731')
    .expect(errorMessageField.value)
    .eql('some error message')
    .expect(startDateField.value)
    .eql('2020-09-10 18:41:44')
    .expect(endDateField.value)
    .eql('2020-12-12 12:12:12')

    .expect(variableNameField.value)
    .eql('test')
    .expect(variableValueField.value)
    .eql('123')
    .expect(operationIdField.value)
    .eql('5be8a137-fbb4-4c54-964c-9c7be98b80e6');

  if (IS_NEW_FILTERS_FORM) {
    await t
      .expect(cmRunningInstancesCheckbox.hasClass('checked'))
      .ok()
      .expect(cmActiveCheckbox.hasClass('checked'))
      .ok()
      .expect(cmIncidentsCheckbox.hasClass('checked'))
      .ok()
      .expect(cmFinishedInstancesCheckbox.hasClass('checked'))
      .ok()
      .expect(cmCompletedCheckbox.hasClass('checked'))
      .ok()
      .expect(cmCanceledCheckbox.hasClass('checked'))
      .ok();
  } else {
    await t
      .expect(
        screen.queryByRole('checkbox', {name: 'Running Instances'}).checked
      )
      .ok()
      .expect(screen.queryByRole('checkbox', {name: 'Active'}).checked)
      .ok()
      .expect(screen.queryByRole('checkbox', {name: 'Incidents'}).checked)
      .ok()
      .expect(
        screen.queryByRole('checkbox', {name: 'Finished Instances'}).checked
      )
      .ok()
      .expect(screen.queryByRole('checkbox', {name: 'Completed'}).checked)
      .ok()
      .expect(screen.queryByRole('checkbox', {name: 'Canceled'}).checked)
      .ok();
  }

  // should navigate to dashboard and back, and see filters are still there

  await t.click(
    screen
      .queryAllByRole('link', {
        name: /dashboard/i,
      })
      .nth(0)
  );
  await t.click(
    screen.queryByRole('link', {
      name: /view instances/i,
    })
  );

  if (IS_NEW_FILTERS_FORM) {
    await t
      .expect(
        within(
          screen.queryByTestId('filter-process-name').shadowRoot()
        ).queryByText('Process With Multiple Versions').exists
      )
      .ok()
      .expect(
        within(
          screen.queryByTestId('filter-process-version').shadowRoot()
        ).queryByText('Version 2').exists
      )
      .ok()
      .expect(
        within(
          screen.queryByTestId('filter-flow-node').shadowRoot()
        ).queryByText('Always fails').exists
      )
      .ok();
  } else {
    await t
      .expect(
        screen.queryByRole('combobox', {
          name: 'Process',
        }).value
      )
      .eql('processWithMultipleVersions')
      .expect(screen.queryByRole('combobox', {name: 'Process Version'}).value)
      .eql('2')
      .expect(screen.queryByRole('combobox', {name: /flow node/i}).value)
      .eql('alwaysFails');
  }

  await t
    .expect(instanceIdsField.value)
    .eql('2251799813685255')
    .expect(parentInstanceIdField.value)
    .eql('2251799813685731')
    .expect(errorMessageField.value)
    .eql('some error message')
    .expect(startDateField.value)
    .eql('2020-09-10 18:41:44')
    .expect(endDateField.value)
    .eql('2020-12-12 12:12:12')
    .expect(variableNameField.value)
    .eql('test')
    .expect(variableValueField.value)
    .eql('123')
    .expect(operationIdField.value)
    .eql('5be8a137-fbb4-4c54-964c-9c7be98b80e6');

  if (IS_NEW_FILTERS_FORM) {
    await t
      .expect(cmRunningInstancesCheckbox.hasClass('checked'))
      .ok()
      .expect(cmActiveCheckbox.hasClass('checked'))
      .ok()
      .expect(cmIncidentsCheckbox.hasClass('checked'))
      .ok()
      .expect(cmFinishedInstancesCheckbox.hasClass('checked'))
      .ok()
      .expect(cmCompletedCheckbox.hasClass('checked'))
      .ok()
      .expect(cmCanceledCheckbox.hasClass('checked'))
      .ok();
  } else {
    await t
      .expect(
        screen.queryByRole('checkbox', {name: 'Running Instances'}).checked
      )
      .ok()
      .expect(screen.queryByRole('checkbox', {name: 'Active'}).checked)
      .ok()
      .expect(screen.queryByRole('checkbox', {name: 'Incidents'}).checked)
      .ok()
      .expect(
        screen.queryByRole('checkbox', {name: 'Finished Instances'}).checked
      )
      .ok()
      .expect(screen.queryByRole('checkbox', {name: 'Completed'}).checked)
      .ok()
      .expect(screen.queryByRole('checkbox', {name: 'Canceled'}).checked)
      .ok();
  }
});

(IS_NEW_FILTERS_FORM ? test : test.skip)(
  'Should order optional filters',
  async (t) => {
    await displayOptionalFilter('Error Message');
    await displayOptionalFilter('Parent Instance Id');
    await displayOptionalFilter('End Date');
    await displayOptionalFilter('Variable');
    await displayOptionalFilter('Start Date');
    await displayOptionalFilter('Instance Id(s)');
    await displayOptionalFilter('Operation Id');

    await t
      .expect(
        screen.getByTestId('filter-operation-id').parent().getAttribute('order')
      )
      .eql('6');
    await t
      .expect(
        screen.getByTestId('filter-instance-ids').parent().getAttribute('order')
      )
      .eql('5');
    await t
      .expect(
        screen.getByTestId('filter-start-date').parent().getAttribute('order')
      )
      .eql('4');
    await t
      .expect(
        screen
          .getByTestId('filter-variable-name')
          .parent()
          .getAttribute('order')
      )
      .eql('3');
    await t
      .expect(
        screen.getByTestId('filter-end-date').parent().getAttribute('order')
      )
      .eql('2');
    await t
      .expect(
        screen
          .getByTestId('filter-parent-instance-id')
          .parent()
          .getAttribute('order')
      )
      .eql('1');
    await t
      .expect(
        screen
          .getByTestId('filter-error-message')
          .parent()
          .getAttribute('order')
      )
      .eql('0');

    await t.click(screen.queryByRole('button', {name: /reset filters/i}));

    await displayOptionalFilter('Variable');
    await displayOptionalFilter('Start Date');
    await displayOptionalFilter('Parent Instance Id');
    await displayOptionalFilter('Operation Id');
    await displayOptionalFilter('End Date');
    await displayOptionalFilter('Error Message');
    await displayOptionalFilter('Instance Id(s)');

    await t
      .expect(
        screen.getByTestId('filter-instance-ids').parent().getAttribute('order')
      )
      .eql('6');
    await t
      .expect(
        screen
          .getByTestId('filter-error-message')
          .parent()
          .getAttribute('order')
      )
      .eql('5');
    await t
      .expect(
        screen.getByTestId('filter-end-date').parent().getAttribute('order')
      )
      .eql('4');
    await t
      .expect(
        screen.getByTestId('filter-operation-id').parent().getAttribute('order')
      )
      .eql('3');
    await t
      .expect(
        screen
          .getByTestId('filter-parent-instance-id')
          .parent()
          .getAttribute('order')
      )
      .eql('2');
    await t
      .expect(
        screen.getByTestId('filter-start-date').parent().getAttribute('order')
      )
      .eql('1');
    await t
      .expect(
        screen
          .getByTestId('filter-variable-name')
          .parent()
          .getAttribute('order')
      )
      .eql('0');
  }
);
