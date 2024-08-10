/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
