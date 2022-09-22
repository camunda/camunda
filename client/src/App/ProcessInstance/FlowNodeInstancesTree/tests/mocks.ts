/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {FlowNodeInstance} from 'modules/stores/flowNodeInstance';
import {
  createEventSubProcessFlowNodeInstances,
  createMultiInstanceFlowNodeInstances,
} from 'modules/testUtils';

const CURRENT_INSTANCE = Object.freeze({
  id: '2251799813686118',
  processId: '2251799813686038',
  processName: 'Multi-Instance Process',
  version: 1,
  startDate: '2020-08-18T12:07:33.854+0000',
  endDate: null,
  state: 'INCIDENT',
  bpmnProcessId: 'multiInstanceProcess',
  hasActiveOperation: false,
  operations: [],
});

const processId = 'multiInstanceProcess';
const processInstanceId = CURRENT_INSTANCE.id;

const flowNodeInstances =
  createMultiInstanceFlowNodeInstances(processInstanceId);

const eventSubProcessFlowNodeInstances =
  createEventSubProcessFlowNodeInstances(processInstanceId);

const multipleFlowNodeInstances = {
  [processInstanceId]: {
    running: null,
    children: [
      {
        id: '2251799813686130',
        type: 'EXCLUSIVE_GATEWAY',
        state: 'COMPLETED',
        flowNodeId: 'peterJoin',
        startDate: '2020-08-18T12:07:33.953+0000',
        endDate: '2020-08-18T12:07:34.034+0000',
        treePath: `${processInstanceId}/2251799813686130`,
        sortValues: ['1606300828415', '2251799813686130'],
      },
      {
        id: '2251799813686156',
        type: 'EXCLUSIVE_GATEWAY',
        state: 'INCIDENT',
        flowNodeId: 'peterJoin',
        startDate: '2020-08-18T12:07:33.953+0000',
        endDate: null,
        treePath: `${processInstanceId}/2251799813686156`,
        sortValues: ['1606300828415', '2251799813686156'],
      },
    ],
  },
};

const mockFlowNodeInstance: FlowNodeInstance = {
  id: processInstanceId,
  type: 'PROCESS',
  state: 'COMPLETED',
  flowNodeId: processId,
  treePath: processInstanceId,
  startDate: '',
  endDate: null,
  sortValues: [],
};

export {
  CURRENT_INSTANCE,
  processId,
  processInstanceId,
  flowNodeInstances,
  eventSubProcessFlowNodeInstances,
  mockFlowNodeInstance,
  multipleFlowNodeInstances,
};
