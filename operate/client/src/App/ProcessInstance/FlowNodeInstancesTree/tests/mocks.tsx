/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  FlowNodeInstance,
  FlowNodeInstances,
  flowNodeInstanceStore,
} from 'modules/stores/flowNodeInstance';
import {instanceHistoryModificationStore} from 'modules/stores/instanceHistoryModification';
import {modificationsStore} from 'modules/stores/modifications';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {
  createEventSubProcessFlowNodeInstances,
  createInstance,
  createMultiInstanceFlowNodeInstances,
} from 'modules/testUtils';
import {useEffect} from 'react';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {TreeView} from '@carbon/react';

const multiInstanceProcessInstance: ProcessInstanceEntity = Object.freeze(
  createInstance({
    id: '2251799813686118',
    processId: '2251799813686038',
    processName: 'Multi-Instance Process',
    state: 'INCIDENT',
    bpmnProcessId: 'multiInstanceProcess',
  }),
);

const eventSubprocessProcessInstance: ProcessInstanceEntity = Object.freeze(
  createInstance({
    id: '2251799813686118',
    processId: '2251799813686038',
    processName: 'Event subprocess Process',
    state: 'INCIDENT',
    bpmnProcessId: 'eventSubprocessProcess',
  }),
);

const nestedSubProcessesInstance = Object.freeze(
  createInstance({
    id: '227539842356787',
    processId: '39480256723678',
    processName: 'Nested Sub Processes',
    state: 'ACTIVE',
    bpmnProcessId: 'NestedSubProcesses',
  }),
);

const adHocSubProcessesInstance = Object.freeze(
  createInstance({
    id: '222734982389310',
    processId: '12517992348923884',
    processName: 'Ad Hoc Process',
    state: 'ACTIVE',
    bpmnProcessId: 'AdHocProcess',
  }),
);

const nestedSubProcessFlowNodeInstances: FlowNodeInstances = {
  [nestedSubProcessesInstance.id]: {
    running: null,
    children: [
      {
        id: '2251799813686130',
        type: 'START_EVENT',
        state: 'COMPLETED',
        flowNodeId: 'StartEvent_1',
        startDate: '2020-08-18T12:07:33.953+0000',
        endDate: '2020-08-18T12:07:34.034+0000',
        treePath: `${nestedSubProcessesInstance.id}/2251799813686130`,
        sortValues: ['1606300828415', '2251799813686130'],
      },
      {
        id: '2251799813686156',
        type: 'SERVICE_TASK',
        state: 'ACTIVE',
        flowNodeId: 'ServiceTask',
        startDate: '2020-08-18T12:07:33.953+0000',
        endDate: null,
        treePath: `${nestedSubProcessesInstance.id}/2251799813686156`,
        sortValues: ['1606300828415', '2251799813686156'],
      },
    ],
  },
};

const nestedSubProcessFlowNodeInstance: FlowNodeInstance = {
  id: nestedSubProcessesInstance.id,
  type: 'PROCESS',
  state: 'ACTIVE',
  flowNodeId: nestedSubProcessesInstance.bpmnProcessId,
  treePath: nestedSubProcessesInstance.id,
  startDate: '',
  endDate: null,
  sortValues: [],
};

const processId = 'multiInstanceProcess';
const processInstanceId = multiInstanceProcessInstance.id;

const flowNodeInstances =
  createMultiInstanceFlowNodeInstances(processInstanceId);

const eventSubProcessFlowNodeInstances =
  createEventSubProcessFlowNodeInstances(processInstanceId);

const multipleFlowNodeInstances: FlowNodeInstances = {
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

const mockRunningNodeInstance: FlowNodeInstance = {
  id: processInstanceId,
  type: 'PROCESS',
  state: 'ACTIVE',
  flowNodeId: 'nested_sub_process',
  treePath: processInstanceId,
  startDate: '',
  endDate: null,
  sortValues: [],
};

const multipleSubprocessesWithOneRunningScopeMock: {
  firstLevel: FlowNodeInstances;
  secondLevel1: FlowNodeInstances;
  secondLevel2: FlowNodeInstances;
  thirdLevel1: FlowNodeInstances;
  thirdLevel2: FlowNodeInstances;
} = {
  firstLevel: {
    [processInstanceId]: {
      children: [
        {
          id: '1',
          type: 'SUB_PROCESS',
          state: 'COMPLETED',
          flowNodeId: 'parent_sub_process',
          startDate: '2022-09-23T10:59:43.096+0000',
          endDate: '2022-09-23T11:00:42.508+0000',
          treePath: `${processInstanceId}/1`,
          sortValues: ['1664017183097', '6755399441065192'],
        },
        {
          id: '2',
          type: 'SUB_PROCESS',
          state: 'ACTIVE',
          flowNodeId: 'parent_sub_process',
          startDate: '2022-09-23T10:59:43.822+0000',
          endDate: null,
          treePath: `${processInstanceId}/2`,
          sortValues: ['1664017183823', '6755399441065673'],
        },
      ],
      running: null,
    },
  },
  secondLevel1: {
    [`${processInstanceId}/1`]: {
      children: [
        {
          id: '1_1',
          type: 'START_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'Event_0oi4pw0',
          startDate: '2022-09-23T10:59:43.096+0000',
          endDate: '2022-09-23T11:00:42.508+0000',
          treePath: `${processInstanceId}/1/1_1`,
          sortValues: ['1664017183097', '6755399441065192'],
        },
        {
          id: '1_2',
          type: 'SUB_PROCESS',
          state: 'COMPLETED',
          flowNodeId: 'inner_sub_process',
          startDate: '2022-09-23T10:59:43.822+0000',
          endDate: '2022-09-23T11:00:42.508+0000',
          treePath: `${processInstanceId}/1/1_2`,
          sortValues: ['1664017183823', '6755399441065673'],
        },
        {
          id: '1_3',
          type: 'END_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'Event_1k2dpf7',
          startDate: '2022-09-23T10:59:43.822+0000',
          endDate: '2022-09-23T11:00:42.508+0000',
          treePath: `${processInstanceId}/1/1_3`,
          sortValues: ['1664017183823', '6755399441065673'],
        },
      ],
      running: null,
    },
  },
  secondLevel2: {
    [`${processInstanceId}/2`]: {
      children: [
        {
          id: '2_1',
          type: 'START_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'Event_0oi4pw0',
          startDate: '2022-09-23T10:59:43.096+0000',
          endDate: '2022-09-23T11:00:42.508+0000',
          treePath: `${processInstanceId}/2/2_1`,
          sortValues: ['1664017183097', '6755399441065192'],
        },
        {
          id: '2_2',
          type: 'SUB_PROCESS',
          state: 'ACTIVE',
          flowNodeId: 'inner_sub_process',
          startDate: '2022-09-23T10:59:43.096+0000',
          endDate: '2022-09-23T11:00:42.508+0000',
          treePath: `${processInstanceId}/2/2_2`,
          sortValues: ['1664017183823', '6755399441065673'],
        },
      ],
      running: null,
    },
  },
  thirdLevel1: {
    [`${processInstanceId}/1/1_2`]: {
      children: [
        {
          id: '1_2_1',
          type: 'START_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'Event_1rw6vny',
          startDate: '2022-09-23T10:59:43.096+0000',
          endDate: '2022-09-23T11:00:42.508+0000',
          treePath: `${processInstanceId}/1/1_2/1_2_1`,
          sortValues: ['1664017183097', '6755399441065192'],
        },
        {
          id: '1_2_2',
          type: 'USER_TASK',
          state: 'COMPLETED',
          flowNodeId: 'user_task',
          startDate: '2022-09-23T10:59:43.822+0000',
          endDate: '2022-09-23T11:00:42.508+0000',
          treePath: `${processInstanceId}/1/1_2/1_2_2`,
          sortValues: ['1664017183823', '6755399441065673'],
        },
        {
          id: '1_2_3',
          type: 'END_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'Event_0ypvz5p',
          startDate: '2022-09-23T10:59:43.822+0000',
          endDate: '2022-09-23T11:00:42.508+0000',
          treePath: `${processInstanceId}/1/1_2/1_2_3`,
          sortValues: ['1664017183823', '6755399441065673'],
        },
      ],
      running: null,
    },
  },
  thirdLevel2: {
    [`${processInstanceId}/2/2_2`]: {
      children: [
        {
          id: '2_2_1',
          type: 'START_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'Event_1rw6vny',
          startDate: '2022-09-23T10:59:43.096+0000',
          endDate: '2022-09-23T11:00:42.508+0000',
          treePath: `${processInstanceId}/2/2_2/2_2_1`,
          sortValues: ['1664017183097', '6755399441065192'],
        },
        {
          id: '2_2_2',
          type: 'USER_TASK',
          state: 'ACTIVE',
          flowNodeId: 'user_task',
          startDate: '2022-09-23T10:59:43.822+0000',
          endDate: null,
          treePath: `${processInstanceId}/2/2_2/2_2_2`,
          sortValues: ['1664017183823', '6755399441065673'],
        },
      ],
      running: null,
    },
  },
};

const multipleSubprocessesWithNoRunningScopeMock: {
  firstLevel: FlowNodeInstances;
  secondLevel1: FlowNodeInstances;
  secondLevel2: FlowNodeInstances;
  thirdLevel1: FlowNodeInstances;
  thirdLevel2: FlowNodeInstances;
} = {
  firstLevel: {
    [processInstanceId]: {
      children: [
        {
          id: '1',
          type: 'SUB_PROCESS',
          state: 'COMPLETED',
          flowNodeId: 'parent_sub_process',
          startDate: '2022-09-23T10:59:43.096+0000',
          endDate: '2022-09-23T11:00:42.508+0000',
          treePath: `${processInstanceId}/1`,
          sortValues: ['1664017183097', '6755399441065192'],
        },
        {
          id: '2',
          type: 'SUB_PROCESS',
          state: 'COMPLETED',
          flowNodeId: 'parent_sub_process',
          startDate: '2022-09-23T10:59:43.822+0000',
          endDate: '2022-09-23T11:00:42.508+0000',
          treePath: `${processInstanceId}/2`,
          sortValues: ['1664017183823', '6755399441065673'],
        },
      ],
      running: null,
    },
  },
  secondLevel1: {
    [`${processInstanceId}/1`]: {
      children: [
        {
          id: '1_1',
          type: 'START_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'Event_0oi4pw0',
          startDate: '2022-09-23T10:59:43.096+0000',
          endDate: '2022-09-23T11:00:42.508+0000',
          treePath: `${processInstanceId}/1/1_1`,
          sortValues: ['1664017183097', '6755399441065192'],
        },
        {
          id: '1_2',
          type: 'SUB_PROCESS',
          state: 'COMPLETED',
          flowNodeId: 'inner_sub_process',
          startDate: '2022-09-23T10:59:43.822+0000',
          endDate: '2022-09-23T11:00:42.508+0000',
          treePath: `${processInstanceId}/1/1_2`,
          sortValues: ['1664017183823', '6755399441065673'],
        },
        {
          id: '1_3',
          type: 'END_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'Event_1k2dpf7',
          startDate: '2022-09-23T10:59:43.822+0000',
          endDate: '2022-09-23T11:00:42.508+0000',
          treePath: `${processInstanceId}/1/1_3`,
          sortValues: ['1664017183823', '6755399441065673'],
        },
      ],
      running: null,
    },
  },
  secondLevel2: {
    [`${processInstanceId}/2`]: {
      children: [
        {
          id: '2_1',
          type: 'START_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'Event_0oi4pw0',
          startDate: '2022-09-23T10:59:43.096+0000',
          endDate: '2022-09-23T11:00:42.508+0000',
          treePath: `${processInstanceId}/2/2_1`,
          sortValues: ['1664017183097', '6755399441065192'],
        },
        {
          id: '2_2',
          type: 'SUB_PROCESS',
          state: 'COMPLETED',
          flowNodeId: 'inner_sub_process',
          startDate: '2022-09-23T10:59:43.822+0000',
          endDate: '2022-09-23T11:00:42.508+0000',
          treePath: `${processInstanceId}/2/2_2`,
          sortValues: ['1664017183823', '6755399441065673'],
        },
        {
          id: '2_3',
          type: 'END_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'Event_1k2dpf7',
          startDate: '2022-09-23T10:59:43.822+0000',
          endDate: '2022-09-23T11:00:42.508+0000',
          treePath: `${processInstanceId}/2/2_3`,
          sortValues: ['1664017183823', '6755399441065673'],
        },
      ],
      running: null,
    },
  },
  thirdLevel1: {
    [`${processInstanceId}/1/1_2`]: {
      children: [
        {
          id: '1_2_1',
          type: 'START_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'Event_1rw6vny',
          startDate: '2022-09-23T10:59:43.096+0000',
          endDate: '2022-09-23T11:00:42.508+0000',
          treePath: `${processInstanceId}/1/1_2/1_2_1`,
          sortValues: ['1664017183097', '6755399441065192'],
        },
        {
          id: '1_2_2',
          type: 'USER_TASK',
          state: 'COMPLETED',
          flowNodeId: 'user_task',
          startDate: '2022-09-23T10:59:43.822+0000',
          endDate: '2022-09-23T11:00:42.508+0000',
          treePath: `${processInstanceId}/1/1_2/1_2_2`,
          sortValues: ['1664017183823', '6755399441065673'],
        },
        {
          id: '1_2_3',
          type: 'END_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'Event_0ypvz5p',
          startDate: '2022-09-23T10:59:43.822+0000',
          endDate: '2022-09-23T11:00:42.508+0000',
          treePath: `${processInstanceId}/1/1_2/1_2_3`,
          sortValues: ['1664017183823', '6755399441065673'],
        },
      ],
      running: null,
    },
  },
  thirdLevel2: {
    [`${processInstanceId}/2/2_2`]: {
      children: [
        {
          id: '2_2_1',
          type: 'START_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'Event_1rw6vny',
          startDate: '2022-09-23T10:59:43.096+0000',
          endDate: '2022-09-23T11:00:42.508+0000',
          treePath: `${processInstanceId}/2/2_2/2_2_1`,
          sortValues: ['1664017183097', '6755399441065192'],
        },
        {
          id: '2_2_2',
          type: 'USER_TASK',
          state: 'COMPLETED',
          flowNodeId: 'user_task',
          startDate: '2022-09-23T10:59:43.822+0000',
          endDate: '2022-09-23T11:00:42.508+0000',
          treePath: `${processInstanceId}/2/2_2/2_2_2`,
          sortValues: ['1664017183823', '6755399441065673'],
        },
        {
          id: '2_2_3',
          type: 'END_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'Event_0ypvz5p',
          startDate: '2022-09-23T10:59:43.822+0000',
          endDate: '2022-09-23T11:00:42.508+0000',
          treePath: `${processInstanceId}/2/2_2/2_2_3`,
          sortValues: ['1664017183823', '6755399441065673'],
        },
      ],
      running: null,
    },
  },
};

const multipleSubprocessesWithTwoRunningScopesMock: {
  firstLevel: FlowNodeInstances;
  secondLevel1: FlowNodeInstances;
  secondLevel2: FlowNodeInstances;
  thirdLevel1: FlowNodeInstances;
  thirdLevel2: FlowNodeInstances;
} = {
  firstLevel: {
    [processInstanceId]: {
      children: [
        {
          id: '1',
          type: 'SUB_PROCESS',
          state: 'ACTIVE',
          flowNodeId: 'parent_sub_process',
          startDate: '2022-09-23T10:59:43.096+0000',
          endDate: null,
          treePath: `${processInstanceId}/1`,
          sortValues: ['1664017183097', '6755399441065192'],
        },
        {
          id: '2',
          type: 'SUB_PROCESS',
          state: 'ACTIVE',
          flowNodeId: 'parent_sub_process',
          startDate: '2022-09-23T10:59:43.822+0000',
          endDate: null,
          treePath: `${processInstanceId}/2`,
          sortValues: ['1664017183823', '6755399441065673'],
        },
      ],
      running: null,
    },
  },
  secondLevel1: {
    [`${processInstanceId}/1`]: {
      children: [
        {
          id: '1_1',
          type: 'START_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'Event_0oi4pw0',
          startDate: '2022-09-23T10:59:43.096+0000',
          endDate: '2022-09-23T11:00:42.508+0000',
          treePath: `${processInstanceId}/1/1_1`,
          sortValues: ['1664017183097', '6755399441065192'],
        },
        {
          id: '1_2',
          type: 'SUB_PROCESS',
          state: 'ACTIVE',
          flowNodeId: 'inner_sub_process',
          startDate: '2022-09-23T10:59:43.822+0000',
          endDate: null,
          treePath: `${processInstanceId}/1/1_2`,
          sortValues: ['1664017183823', '6755399441065673'],
        },
        {
          id: '1_3',
          type: 'END_EVENT',
          state: 'ACTIVE',
          flowNodeId: 'Event_1k2dpf7',
          startDate: '2022-09-23T10:59:43.822+0000',
          endDate: null,
          treePath: `${processInstanceId}/1/1_3`,
          sortValues: ['1664017183823', '6755399441065673'],
        },
      ],
      running: null,
    },
  },
  secondLevel2: {
    [`${processInstanceId}/2`]: {
      children: [
        {
          id: '2_1',
          type: 'START_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'Event_0oi4pw0',
          startDate: '2022-09-23T10:59:43.096+0000',
          endDate: '2022-09-23T11:00:42.508+0000',
          treePath: `${processInstanceId}/2/2_1`,
          sortValues: ['1664017183097', '6755399441065192'],
        },
        {
          id: '2_2',
          type: 'SUB_PROCESS',
          state: 'ACTIVE',
          flowNodeId: 'inner_sub_process',
          startDate: '2022-09-23T10:59:43.822+0000',
          endDate: null,
          treePath: `${processInstanceId}/2/2_2`,
          sortValues: ['1664017183823', '6755399441065673'],
        },
        {
          id: '2_3',
          type: 'END_EVENT',
          state: 'ACTIVE',
          flowNodeId: 'Event_1k2dpf7',
          startDate: '2022-09-23T10:59:43.822+0000',
          endDate: null,
          treePath: `${processInstanceId}/2/2_3`,
          sortValues: ['1664017183823', '6755399441065673'],
        },
      ],
      running: null,
    },
  },
  thirdLevel1: {
    [`${processInstanceId}/1/1_2`]: {
      children: [
        {
          id: '1_2_1',
          type: 'START_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'Event_1rw6vny',
          startDate: '2022-09-23T10:59:43.096+0000',
          endDate: '2022-09-23T11:00:42.508+0000',
          treePath: `${processInstanceId}/1/1_2/1_2_1`,
          sortValues: ['1664017183097', '6755399441065192'],
        },
        {
          id: '1_2_2',
          type: 'USER_TASK',
          state: 'ACTIVE',
          flowNodeId: 'user_task',
          startDate: '2022-09-23T10:59:43.822+0000',
          endDate: null,
          treePath: `${processInstanceId}/1/1_2/1_2_2`,
          sortValues: ['1664017183823', '6755399441065673'],
        },
        {
          id: '1_2_3',
          type: 'END_EVENT',
          state: 'ACTIVE',
          flowNodeId: 'Event_0ypvz5p',
          startDate: '2022-09-23T10:59:43.822+0000',
          endDate: null,
          treePath: `${processInstanceId}/1/1_2/1_2_3`,
          sortValues: ['1664017183823', '6755399441065673'],
        },
      ],
      running: null,
    },
  },
  thirdLevel2: {
    [`${processInstanceId}/2/2_2`]: {
      children: [
        {
          id: '2_2_1',
          type: 'START_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'Event_1rw6vny',
          startDate: '2022-09-23T10:59:43.096+0000',
          endDate: '2022-09-23T11:00:42.508+0000',
          treePath: `${processInstanceId}/2/2_2/2_2_1`,
          sortValues: ['1664017183097', '6755399441065192'],
        },
        {
          id: '2_2_2',
          type: 'USER_TASK',
          state: 'ACTIVE',
          flowNodeId: 'user_task',
          startDate: '2022-09-23T10:59:43.822+0000',
          endDate: null,
          treePath: `${processInstanceId}/2/2_2/2_2_2`,
          sortValues: ['1664017183823', '6755399441065673'],
        },
        {
          id: '2_2_3',
          type: 'END_EVENT',
          state: 'ACTIVE',
          flowNodeId: 'Event_0ypvz5p',
          startDate: '2022-09-23T10:59:43.822+0000',
          endDate: null,
          treePath: `${processInstanceId}/2/2_2/2_2_3`,
          sortValues: ['1664017183823', '6755399441065673'],
        },
      ],
      running: null,
    },
  },
};

const adHocNodeFlowNodeInstances: {
  level1: FlowNodeInstances;
  level2: FlowNodeInstances;
} = {
  level1: {
    [adHocSubProcessesInstance.id]: {
      running: null,
      children: [
        {
          id: '2251799813686130',
          type: 'START_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'StartEvent_1',
          startDate: '2020-08-18T12:07:33.953+0000',
          endDate: '2020-08-18T12:07:34.034+0000',
          treePath: `${adHocSubProcessesInstance.id}/2251799813686130`,
          sortValues: ['1606300828415', '2251799813686130'],
        },
        {
          id: '1241799813612356',
          type: 'AD_HOC_SUB_PROCESS',
          state: 'COMPLETED',
          flowNodeId: 'AdHocSubProcess',
          startDate: '2020-08-18T12:07:33.953+0000',
          endDate: '2020-08-18T12:07:34.034+0000',
          treePath: `${adHocSubProcessesInstance.id}/1241799813612356`,
          sortValues: ['1606300828415', '2251799813686156'],
        },
        {
          id: '2251799813686156',
          type: 'END_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'EndEvent_1',
          startDate: '2020-08-18T12:07:33.953+0000',
          endDate: '2020-08-18T12:07:34.034+0000',
          treePath: `${adHocSubProcessesInstance.id}/2251799813686156`,
          sortValues: ['1606300828415', '2251799813686156'],
        },
      ],
    },
  },
  level2: {
    [`${adHocSubProcessesInstance.id}/1241799813612356`]: {
      running: null,
      children: [
        {
          id: '22345625376130',
          type: 'TASK',
          state: 'COMPLETED',
          flowNodeId: 'Task_A',
          startDate: '2020-08-18T12:07:33.953+0000',
          endDate: '2020-08-18T12:07:34.034+0000',
          treePath: `${adHocSubProcessesInstance.id}/1241799813612356/22345625376130`,
          sortValues: ['1606300828415', '2251799813686130'],
        },
      ],
    },
  },
};

const Wrapper = ({children}: {children?: React.ReactNode}) => {
  useEffect(() => {
    return () => {
      processInstanceDetailsStore.reset();
      processInstanceDetailsDiagramStore.reset();
      flowNodeInstanceStore.reset();
      modificationsStore.reset();
      processInstanceDetailsStatisticsStore.reset();
      instanceHistoryModificationStore.reset();
    };
  }, []);

  return (
    <TreeView label={'instance history'} hideLabel>
      {children}
    </TreeView>
  );
};

export {
  multiInstanceProcessInstance,
  nestedSubProcessesInstance,
  adHocSubProcessesInstance,
  processId,
  processInstanceId,
  flowNodeInstances,
  eventSubProcessFlowNodeInstances,
  nestedSubProcessFlowNodeInstances,
  nestedSubProcessFlowNodeInstance,
  adHocNodeFlowNodeInstances,
  mockFlowNodeInstance,
  multipleFlowNodeInstances,
  multipleSubprocessesWithOneRunningScopeMock,
  multipleSubprocessesWithNoRunningScopeMock,
  multipleSubprocessesWithTwoRunningScopesMock,
  mockRunningNodeInstance,
  eventSubprocessProcessInstance,
  Wrapper,
};
