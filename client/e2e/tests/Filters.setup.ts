/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {deploy, createInstances, createSingleInstance} from '../setup-utils';
import {within, screen} from '@testing-library/testcafe';

const cmRunningInstancesCheckbox = within(
  screen.queryByTestId('filter-running-instances').shadowRoot()
).queryByRole('checkbox');

const cmActiveCheckbox = within(
  screen.queryByTestId('filter-active').shadowRoot()
).queryByRole('checkbox');

const cmIncidentsCheckbox = within(
  screen.queryByTestId('filter-incidents').shadowRoot()
).queryByRole('checkbox');

const cmFinishedInstancesCheckbox = within(
  screen.queryByTestId('filter-finished-instances').shadowRoot()
).queryByRole('checkbox');

const cmCompletedCheckbox = within(
  screen.queryByTestId('filter-completed').shadowRoot()
).queryByRole('checkbox');

const cmCanceledCheckbox = within(
  screen.queryByTestId('filter-canceled').shadowRoot()
).queryByRole('checkbox');

const cmParentInstanceIdField = within(
  screen.queryByTestId('filter-parent-instance-id').shadowRoot()
).queryByRole('textbox');

const cmErrorMessageField = within(
  screen.queryByTestId('filter-error-message').shadowRoot()
).queryByRole('textbox');

const cmStartDateField = within(
  screen.queryByTestId('filter-start-date').shadowRoot()
).queryByRole('textbox');

const cmEndDateField = within(
  screen.queryByTestId('filter-end-date').shadowRoot()
).queryByRole('textbox');

const cmOperationIdField = within(
  screen.queryByTestId('filter-operation-id').shadowRoot()
).queryByRole('textbox');

const cmVariableNameField = within(
  screen.queryByTestId('filter-variable-name').shadowRoot()
).queryByRole('textbox');

const cmVariableValueField = within(
  screen.queryByTestId('filter-variable-value').shadowRoot()
).queryByRole('textbox');

const cmInstanceIdsField = within(
  screen.queryByTestId('filter-instance-ids').shadowRoot()
).queryByRole('textbox');

const setup = async () => {
  await deploy([
    './e2e/tests/resources/Filters/processWithMultipleVersions_v_1.bpmn',
  ]);
  await deploy([
    './e2e/tests/resources/Filters/processWithMultipleVersions_v_2.bpmn',
  ]);
  await deploy(['./e2e/tests/resources/Filters/processWithAnError.bpmn']);

  await createInstances('processWithMultipleVersions', 1, 1);
  await createInstances('processWithMultipleVersions', 2, 1);

  await createInstances('processWithAnError', 1, 1);

  await deploy(['./e2e/tests/resources/orderProcess_v_1.bpmn']);

  const instanceToCancel = await createSingleInstance('orderProcess', 1);
  await createSingleInstance('orderProcess', 1, {
    filtersTest: 123,
  });

  const instanceToCancelForOperations = await createSingleInstance(
    'orderProcess',
    1
  );

  await deploy([
    './e2e/tests/resources/callActivityProcess.bpmn',
    './e2e/tests/resources/calledProcess.bpmn',
  ]);

  const callActivityProcessInstance = await createSingleInstance(
    'CallActivityProcess',
    1
  );

  return {
    instanceToCancel,
    instanceToCancelForOperations,
    callActivityProcessInstance,
  };
};

export {
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
};
