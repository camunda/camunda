/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const mockOperationRunning = {
  id: '1234',
  type: 'RESOLVE_INCIDENT',
  endDate: null,
  instancesCount: 1,
  operationsTotalCount: 1,
  operationsFinishedCount: 0,
};

const mockExistingOperationFinished = {
  id: '1234',
  type: 'RESOLVE_INCIDENT',
  endDate: '2020-02-06T15:37:29.699+0100',
  instancesCount: 1,
};

const mockOperationFinished = {
  id: '5678',
  type: 'CANCEL_PROCESS_INSTANCE',
  endDate: '2020-02-06T15:37:29.699+0100',
  instancesCount: 2,
  operationsTotalCount: 2,
  operationsFinishedCount: 2,
};

const mockProps = {
  onInstancesClick: jest.fn(),
};

export {
  mockOperationRunning,
  mockExistingOperationFinished,
  mockOperationFinished,
  mockProps,
};
