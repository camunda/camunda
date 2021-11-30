/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {deploy, createInstances, createSingleInstance} from '../setup-utils';
import {within, screen} from '@testing-library/testcafe';

const cmParentInstanceIdField = within(
  screen.queryByTestId('parentInstanceId').shadowRoot()
).queryByRole('textbox');
const cmErrorMessageField = within(
  screen.queryByTestId('errorMessage').shadowRoot()
).queryByRole('textbox');
const cmStartDateField = within(
  screen.queryByTestId('startDate').shadowRoot()
).queryByRole('textbox');
const cmEndDateField = within(
  screen.queryByTestId('endDate').shadowRoot()
).queryByRole('textbox');
const cmOperationIdField = within(
  screen.queryByTestId('operationId').shadowRoot()
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
  cmParentInstanceIdField,
  cmErrorMessageField,
  cmOperationIdField,
  cmStartDateField,
  cmEndDateField,
};
