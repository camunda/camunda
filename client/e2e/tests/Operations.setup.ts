/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {deploy, createInstances, createSingleInstance} from '../setup-utils';
import {createOperation} from './api';
import {wait} from './utils/wait';
import {within, screen} from '@testing-library/testcafe';

const cmOperationIdField = within(
  screen.queryByTestId('filter-operation-id').shadowRoot()
).queryByRole('textbox');

async function setup() {
  await deploy([
    './e2e/tests/resources/operationsProcessA.bpmn',
    './e2e/tests/resources/operationsProcessB.bpmn',
  ]);

  const [singleOperationInstance, batchOperationInstances] = await Promise.all([
    createSingleInstance('operationsProcessA', 1),
    createInstances('operationsProcessB', 1, 10),
  ]);

  await wait();

  await Promise.all(
    [...new Array(40)].map(() =>
      createOperation({
        id: singleOperationInstance.processInstanceKey,
        operationType: 'RESOLVE_INCIDENT',
      })
    )
  );

  return {singleOperationInstance, batchOperationInstances};
}

export {setup, cmOperationIdField};
