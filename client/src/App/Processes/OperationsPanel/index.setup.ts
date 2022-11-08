/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const mockOperationRunning: OperationEntity = {
  id: '1234',
  type: 'RESOLVE_INCIDENT',
  startDate: '2020-02-06T14:37:29.699+0100',
  endDate: null,
  instancesCount: 1,
  operationsTotalCount: 1,
  operationsFinishedCount: 0,
  sortValues: ['1234', '213'],
  name: null,
};

const mockOperationFinished: OperationEntity = {
  id: '5678',
  type: 'CANCEL_PROCESS_INSTANCE',
  startDate: '2020-02-06T14:37:29.699+0100',
  endDate: '2020-02-06T15:37:29.699+0100',
  instancesCount: 2,
  operationsTotalCount: 2,
  operationsFinishedCount: 2,
  sortValues: ['1234', '213'],
  name: null,
};

export {mockOperationRunning, mockOperationFinished};
