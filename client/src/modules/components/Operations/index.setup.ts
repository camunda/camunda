/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createInstance, createOperation} from 'modules/testUtils';

const INSTANCE = createInstance({
  id: 'instance_1',
  operations: [createOperation({state: 'FAILED'})],
  hasActiveOperation: false,
});
const ACTIVE_INSTANCE = createInstance({
  id: 'instance_1',
  operations: [createOperation({state: 'SENT'})],
  hasActiveOperation: true,
});

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

export {INSTANCE, ACTIVE_INSTANCE, mockOperationCreated};
