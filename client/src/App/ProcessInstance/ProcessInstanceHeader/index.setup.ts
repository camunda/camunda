/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {createInstance} from 'modules/testUtils';

const mockOperationCreated = {
  id: '2',
  name: null,
  type: 'CANCEL_PROCESS_INSTANCE',
  startDate: '2020-09-29T12:32:54.874+0000',
  endDate: null,
  username: 'demo',
  instancesCount: 2,
  operationsTotalCount: 2,
  operationsFinishedCount: 0,
};

const mockInstanceWithActiveOperation = createInstance({
  hasActiveOperation: true,
});

const mockCanceledInstance = createInstance({
  state: 'CANCELED',
});

const mockInstanceWithParentInstance = createInstance({
  parentInstanceId: '8724390842390124',
});

const mockInstanceWithoutOperations = {
  ...mockInstanceWithActiveOperation,
  hasActiveOperation: false,
  operations: [],
} as const;

export {
  mockOperationCreated,
  mockInstanceWithActiveOperation,
  mockCanceledInstance,
  mockInstanceWithParentInstance,
  mockInstanceWithoutOperations,
};
