/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {createBatchOperation, createInstance} from 'modules/testUtils';

const mockOperationCreated = createBatchOperation();

const mockInstanceWithActiveOperation = createInstance({
  hasActiveOperation: true,
});

const mockCanceledInstance = createInstance({
  state: 'CANCELED',
});

const mockInstanceWithParentInstance = createInstance({
  parentInstanceId: '8724390842390124',
});

const mockInstanceWithoutOperations = createInstance({
  operations: [],
});

export {
  mockOperationCreated,
  mockInstanceWithActiveOperation,
  mockCanceledInstance,
  mockInstanceWithParentInstance,
  mockInstanceWithoutOperations,
};
