/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createFlowNodeInstance} from 'modules/testUtils';

const startEventInstance = createFlowNodeInstance({
  id: 'StartEventId',
  type: 'START_EVENT'
});

const flowNodeInstances = [
  startEventInstance,
  createFlowNodeInstance({
    id: 'SubProcessId',
    type: 'SUB_PROCESS',
    children: [
      createFlowNodeInstance({
        id: 'ServiceTaskId',
        type: 'TASK'
      })
    ]
  }),
  createFlowNodeInstance({id: 'EndEventId', type: 'END_EVENT'})
];

const parentNode = {
  id: 'ParentNodeId',
  type: 'WORKFLOW',
  state: 'ACTIVE',
  children: flowNodeInstances
};

export const testData = {
  singleSelectedTreeRows: ['ParentNodeId'],
  multipleSelectedTreeRowIds: ['ParentNodeId', 'StartEventId', 'ServiceTaskId'],
  parentNode,
  startEventInstance
};
