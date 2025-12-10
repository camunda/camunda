/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {instanceHistoryModificationStore} from 'modules/stores/instanceHistoryModification';
import {modificationsStore} from 'modules/stores/modifications';
import {createInstance} from 'modules/testUtils';
import {useEffect} from 'react';
import {TreeView} from '@carbon/react';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {
  type ProcessInstance,
  type QueryElementInstancesResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import type {ProcessInstanceEntity} from 'modules/types/operate';

const multiInstanceProcessInstance: ProcessInstanceEntity = Object.freeze(
  createInstance({
    id: '2251799813686118',
    processId: '2251799813686038',
    processName: 'Multi-Instance Process',
    state: 'INCIDENT',
    bpmnProcessId: 'multiInstanceProcess',
  }),
);

const mockMultiInstanceProcessInstance: ProcessInstance = {
  processInstanceKey: '2251799813686118',
  state: 'ACTIVE',
  startDate: '2018-06-21',
  processDefinitionKey: '2251799813686038',
  processDefinitionVersion: 1,
  processDefinitionId: 'multiInstanceProcess',
  tenantId: '<default>',
  processDefinitionName: 'Multi-Instance Process',
  hasIncident: true,
};

const eventSubprocessProcessInstance: ProcessInstanceEntity = Object.freeze(
  createInstance({
    id: '2251799813686118',
    processId: '2251799813686038',
    processName: 'Event subprocess Process',
    state: 'INCIDENT',
    bpmnProcessId: 'eventSubprocessProcess',
  }),
);

const mockEventSubprocessInstance: ProcessInstance = {
  processInstanceKey: '2251799813686118',
  state: 'ACTIVE',
  startDate: '2018-06-21',
  processDefinitionKey: '2251799813686038',
  processDefinitionVersion: 1,
  processDefinitionId: 'eventSubprocessProcess',
  tenantId: '<default>',
  processDefinitionName: 'Event subprocess Process',
  hasIncident: true,
};

const nestedSubProcessesInstance = Object.freeze(
  createInstance({
    id: '227539842356787',
    processId: '39480256723678',
    processName: 'Nested Sub Processes',
    state: 'ACTIVE',
    bpmnProcessId: 'NestedSubProcesses',
  }),
);

const mockNestedSubProcessesInstance: ProcessInstance = {
  processInstanceKey: '227539842356787',
  state: 'ACTIVE',
  startDate: '2018-06-21',
  processDefinitionKey: '39480256723678',
  processDefinitionVersion: 1,
  processDefinitionId: 'NestedSubProcesses',
  tenantId: '<default>',
  processDefinitionName: 'Nested Sub Processes',
  hasIncident: false,
};

const adHocSubProcessesInstance = Object.freeze(
  createInstance({
    id: '222734982389310',
    processId: '12517992348923884',
    processName: 'Ad Hoc Process',
    state: 'ACTIVE',
    bpmnProcessId: 'AdHocProcess',
  }),
);

const mockAdHocSubProcessesInstance: ProcessInstance = {
  processInstanceKey: '222734982389310',
  state: 'ACTIVE',
  startDate: '2018-06-21',
  processDefinitionKey: '12517992348923884',
  processDefinitionVersion: 1,
  processDefinitionId: 'AdHocProcess',
  tenantId: '<default>',
  processDefinitionName: 'Ad Hoc Process',
  hasIncident: false,
};

const mockNestedSubProcessInstance: ProcessInstance = {
  processInstanceKey: '2251799813686118',
  state: 'ACTIVE',
  startDate: '2022-09-23T10:59:43.096+0000',
  processDefinitionKey: '123456789',
  processDefinitionVersion: 1,
  processDefinitionId: 'nested_sub_process',
  tenantId: '<default>',
  processDefinitionName: 'nested_sub_process',
  hasIncident: false,
};

const processInstanceId = multiInstanceProcessInstance.id;

const multipleSubprocessesWithOneRunningScopeMock: Record<
  string,
  QueryElementInstancesResponseBody
> = {
  firstLevel: {
    items: [
      {
        elementInstanceKey: '1',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'COMPLETED',
        type: 'SUB_PROCESS',
        elementId: 'parent_sub_process',
        elementName: 'parent_sub_process',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.096+0000',
        endDate: '2022-09-23T11:00:42.508+0000',
      },
      {
        elementInstanceKey: '2',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'ACTIVE',
        type: 'SUB_PROCESS',
        elementId: 'parent_sub_process',
        elementName: 'parent_sub_process',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.822+0000',
      },
    ],
    page: {totalItems: 2},
  },
  secondLevel1: {
    items: [
      {
        elementInstanceKey: '1_1',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'COMPLETED',
        type: 'START_EVENT',
        elementId: 'Event_0oi4pw0',
        elementName: 'Event_0oi4pw0',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.096+0000',
        endDate: '2022-09-23T11:00:42.508+0000',
      },
      {
        elementInstanceKey: '1_2',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'COMPLETED',
        type: 'SUB_PROCESS',
        elementId: 'inner_sub_process',
        elementName: 'inner_sub_process',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.822+0000',
        endDate: '2022-09-23T11:00:42.508+0000',
      },
      {
        elementInstanceKey: '1_3',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'COMPLETED',
        type: 'END_EVENT',
        elementId: 'Event_1k2dpf7',
        elementName: 'Event_1k2dpf7',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.822+0000',
        endDate: '2022-09-23T11:00:42.508+0000',
      },
    ],
    page: {totalItems: 3},
  },
  secondLevel2: {
    items: [
      {
        elementInstanceKey: '2_1',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'COMPLETED',
        type: 'START_EVENT',
        elementId: 'Event_0oi4pw0',
        elementName: 'Event_0oi4pw0',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.096+0000',
        endDate: '2022-09-23T11:00:42.508+0000',
      },
      {
        elementInstanceKey: '2_2',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'ACTIVE',
        type: 'SUB_PROCESS',
        elementId: 'inner_sub_process',
        elementName: 'inner_sub_process',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.096+0000',
      },
    ],
    page: {totalItems: 2},
  },
  thirdLevel1: {
    items: [
      {
        elementInstanceKey: '1_2_1',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'COMPLETED',
        type: 'START_EVENT',
        elementId: 'Event_1rw6vny',
        elementName: 'Event_1rw6vny',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.096+0000',
        endDate: '2022-09-23T11:00:42.508+0000',
      },
      {
        elementInstanceKey: '1_2_2',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'COMPLETED',
        type: 'USER_TASK',
        elementId: 'user_task',
        elementName: 'user_task',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.822+0000',
        endDate: '2022-09-23T11:00:42.508+0000',
      },
      {
        elementInstanceKey: '1_2_3',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'COMPLETED',
        type: 'END_EVENT',
        elementId: 'Event_0ypvz5p',
        elementName: 'Event_0ypvz5p',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.822+0000',
        endDate: '2022-09-23T11:00:42.508+0000',
      },
    ],
    page: {totalItems: 3},
  },
  thirdLevel2: {
    items: [
      {
        elementInstanceKey: '2_2_1',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'COMPLETED',
        type: 'START_EVENT',
        elementId: 'Event_1rw6vny',
        elementName: 'Event_1rw6vny',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.096+0000',
        endDate: '2022-09-23T11:00:42.508+0000',
      },
      {
        elementInstanceKey: '2_2_2',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'ACTIVE',
        type: 'USER_TASK',
        elementId: 'user_task',
        elementName: 'user_task',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.822+0000',
      },
    ],
    page: {totalItems: 2},
  },
};

const multipleSubprocessesWithNoRunningScopeMock: Record<
  string,
  QueryElementInstancesResponseBody
> = {
  firstLevel: {
    items: [
      {
        elementInstanceKey: '1',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'COMPLETED',
        type: 'SUB_PROCESS',
        elementId: 'parent_sub_process',
        elementName: 'parent_sub_process',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.096+0000',
        endDate: '2022-09-23T11:00:42.508+0000',
      },
      {
        elementInstanceKey: '2',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'ACTIVE',
        type: 'SUB_PROCESS',
        elementId: 'parent_sub_process',
        elementName: 'parent_sub_process',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.822+0000',
      },
    ],
    page: {totalItems: 2},
  },
  secondLevel1: {
    items: [
      {
        elementInstanceKey: '1_1',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'COMPLETED',
        type: 'START_EVENT',
        elementId: 'Event_0oi4pw0',
        elementName: 'Event_0oi4pw0',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.096+0000',
        endDate: '2022-09-23T11:00:42.508+0000',
      },
      {
        elementInstanceKey: '1_2',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'COMPLETED',
        type: 'SUB_PROCESS',
        elementId: 'inner_sub_process',
        elementName: 'inner_sub_process',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.822+0000',
        endDate: '2022-09-23T11:00:42.508+0000',
      },
      {
        elementInstanceKey: '1_3',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'COMPLETED',
        type: 'END_EVENT',
        elementId: 'Event_1k2dpf7',
        elementName: 'Event_1k2dpf7',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.822+0000',
        endDate: '2022-09-23T11:00:42.508+0000',
      },
    ],
    page: {totalItems: 3},
  },
  secondLevel2: {
    items: [
      {
        elementInstanceKey: '2_1',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'COMPLETED',
        type: 'START_EVENT',
        elementId: 'Event_0oi4pw0',
        elementName: 'Event_0oi4pw0',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.096+0000',
        endDate: '2022-09-23T11:00:42.508+0000',
      },
      {
        elementInstanceKey: '2_2',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'COMPLETED',
        type: 'SUB_PROCESS',
        elementId: 'inner_sub_process',
        elementName: 'inner_sub_process',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.822+0000',
        endDate: '2022-09-23T11:00:42.508+0000',
      },
      {
        elementInstanceKey: '2_3',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'COMPLETED',
        type: 'END_EVENT',
        elementId: 'Event_1k2dpf7',
        elementName: 'Event_1k2dpf7',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.822+0000',
        endDate: '2022-09-23T11:00:42.508+0000',
      },
    ],
    page: {totalItems: 3},
  },
  thirdLevel1: {
    items: [
      {
        elementInstanceKey: '1_2_1',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'COMPLETED',
        type: 'START_EVENT',
        elementId: 'Event_1rw6vny',
        elementName: 'Event_1rw6vny',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.096+0000',
        endDate: '2022-09-23T11:00:42.508+0000',
      },
      {
        elementInstanceKey: '1_2_2',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'COMPLETED',
        type: 'USER_TASK',
        elementId: 'user_task',
        elementName: 'user_task',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.822+0000',
        endDate: '2022-09-23T11:00:42.508+0000',
      },
      {
        elementInstanceKey: '1_2_3',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'COMPLETED',
        type: 'END_EVENT',
        elementId: 'Event_0ypvz5p',
        elementName: 'Event_0ypvz5p',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.822+0000',
        endDate: '2022-09-23T11:00:42.508+0000',
      },
    ],
    page: {totalItems: 3},
  },
  thirdLevel2: {
    items: [
      {
        elementInstanceKey: '2_2_1',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'COMPLETED',
        type: 'START_EVENT',
        elementId: 'Event_1rw6vny',
        elementName: 'Event_1rw6vny',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.096+0000',
        endDate: '2022-09-23T11:00:42.508+0000',
      },
      {
        elementInstanceKey: '2_2_2',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'COMPLETED',
        type: 'USER_TASK',
        elementId: 'user_task',
        elementName: 'user_task',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.822+0000',
        endDate: '2022-09-23T11:00:42.508+0000',
      },
      {
        elementInstanceKey: '2_2_3',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'COMPLETED',
        type: 'END_EVENT',
        elementId: 'Event_0ypvz5p',
        elementName: 'Event_0ypvz5p',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.822+0000',
        endDate: '2022-09-23T11:00:42.508+0000',
      },
    ],
    page: {totalItems: 3},
  },
};

const adHocNodeElementInstances: Record<
  string,
  QueryElementInstancesResponseBody
> = {
  level1: {
    items: [
      {
        elementInstanceKey: '2251799813686130',
        processInstanceKey: '222734982389310',
        processDefinitionKey: '12517992348923884',
        processDefinitionId: 'AdHocProcess',
        state: 'COMPLETED',
        type: 'START_EVENT',
        elementId: 'StartEvent_1',
        elementName: 'StartEvent_1',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2020-08-18T12:07:33.953+0000',
        endDate: '2020-08-18T12:07:34.034+0000',
      },
      {
        elementInstanceKey: '1241799813612356',
        processInstanceKey: '222734982389310',
        processDefinitionKey: '12517992348923884',
        processDefinitionId: 'AdHocProcess',
        state: 'COMPLETED',
        type: 'AD_HOC_SUB_PROCESS',
        elementId: 'AdHocSubProcess',
        elementName: 'Ad Hoc Sub Process',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2020-08-18T12:07:33.953+0000',
        endDate: '2020-08-18T12:07:34.034+0000',
      },
      {
        elementInstanceKey: '2251799813686156',
        processInstanceKey: '222734982389310',
        processDefinitionKey: '12517992348923884',
        processDefinitionId: 'AdHocProcess',
        state: 'COMPLETED',
        type: 'END_EVENT',
        elementId: 'EndEvent_1',
        elementName: 'EndEvent_1',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2020-08-18T12:07:33.953+0000',
        endDate: '2020-08-18T12:07:34.034+0000',
      },
    ],
    page: {totalItems: 3},
  },
  level2: {
    items: [
      {
        elementInstanceKey: '22345625376130',
        processInstanceKey: '222734982389310',
        processDefinitionKey: '12517992348923884',
        processDefinitionId: 'AdHocProcess',
        state: 'COMPLETED',
        type: 'TASK',
        elementId: 'TaskA',
        elementName: 'Task A',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2020-08-18T12:07:33.953+0000',
        endDate: '2020-08-18T12:07:34.034+0000',
      },
    ],
    page: {totalItems: 1},
  },
};

const eventSubProcessElementInstances: Record<
  string,
  QueryElementInstancesResponseBody
> = {
  level1: {
    items: [
      {
        elementInstanceKey: '6755399441057427',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '2251799813686038',
        processDefinitionId: 'eventSubprocessProcess',
        state: 'COMPLETED',
        type: 'START_EVENT',
        elementId: 'StartEvent_1vnazga',
        elementName: 'StartEvent_1vnazga',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2021-06-22T13:43:59.698+0000',
        endDate: '2021-06-22T13:43:59.701+0000',
      },
      {
        elementInstanceKey: '6755399441057429',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '2251799813686038',
        processDefinitionId: 'eventSubprocessProcess',
        state: 'TERMINATED',
        type: 'SERVICE_TASK',
        elementId: 'ServiceTask_1daop2o',
        elementName: 'Parent process task',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2021-06-22T13:43:59.707+0000',
        endDate: '2021-06-22T13:46:59.705+0000',
      },
      {
        elementInstanceKey: '6755399441063916',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '2251799813686038',
        processDefinitionId: 'eventSubprocessProcess',
        state: 'ACTIVE',
        type: 'EVENT_SUB_PROCESS',
        elementId: 'SubProcess_1ip6c6s',
        elementName: 'Event Subprocess',
        hasIncident: true,
        tenantId: '<default>',
        startDate: '2021-06-22T13:46:59.705+0000',
      },
    ],
    page: {totalItems: 3},
  },
  level2: {
    items: [
      {
        elementInstanceKey: '6755399441063918',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '2251799813686038',
        processDefinitionId: 'eventSubprocessProcess',
        state: 'COMPLETED',
        type: 'START_EVENT',
        elementId: 'StartEvent_1u9mwoj',
        elementName: 'Interrupting timer',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2021-06-22T13:46:59.714+0000',
        endDate: '2021-06-22T13:46:59.719+0000',
      },
      {
        elementInstanceKey: '6755399441063920',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '2251799813686038',
        processDefinitionId: 'eventSubprocessProcess',
        state: 'ACTIVE',
        type: 'SERVICE_TASK',
        elementId: 'ServiceTask_0h8cwwl',
        elementName: 'Event Subprocess task',
        hasIncident: true,
        tenantId: '<default>',
        startDate: '2021-06-22T13:46:59.722+0000',
      },
    ],
    page: {totalItems: 2},
  },
};

const multipleSubprocessesWithTwoRunningScopesMock: Record<
  string,
  QueryElementInstancesResponseBody
> = {
  firstLevel: {
    items: [
      {
        elementInstanceKey: '1',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'ACTIVE',
        type: 'SUB_PROCESS',
        elementId: 'parent_sub_process',
        elementName: 'parent_sub_process',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.096+0000',
      },
      {
        elementInstanceKey: '2',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'ACTIVE',
        type: 'SUB_PROCESS',
        elementId: 'parent_sub_process',
        elementName: 'parent_sub_process',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.822+0000',
      },
    ],
    page: {totalItems: 2},
  },
  secondLevel1: {
    items: [
      {
        elementInstanceKey: '1_1',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'COMPLETED',
        type: 'START_EVENT',
        elementId: 'Event_0oi4pw0',
        elementName: 'Event_0oi4pw0',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.096+0000',
        endDate: '2022-09-23T11:00:42.508+0000',
      },
      {
        elementInstanceKey: '1_2',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'ACTIVE',
        type: 'SUB_PROCESS',
        elementId: 'inner_sub_process',
        elementName: 'inner_sub_process',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.822+0000',
      },
      {
        elementInstanceKey: '1_3',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'ACTIVE',
        type: 'END_EVENT',
        elementId: 'Event_1k2dpf7',
        elementName: 'Event_1k2dpf7',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.822+0000',
      },
    ],
    page: {totalItems: 3},
  },
  secondLevel2: {
    items: [
      {
        elementInstanceKey: '2_1',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'COMPLETED',
        type: 'START_EVENT',
        elementId: 'Event_0oi4pw0',
        elementName: 'Event_0oi4pw0',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.096+0000',
        endDate: '2022-09-23T11:00:42.508+0000',
      },
      {
        elementInstanceKey: '2_2',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'ACTIVE',
        type: 'SUB_PROCESS',
        elementId: 'inner_sub_process',
        elementName: 'inner_sub_process',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.822+0000',
      },
      {
        elementInstanceKey: '2_3',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'ACTIVE',
        type: 'END_EVENT',
        elementId: 'Event_1k2dpf7',
        elementName: 'Event_1k2dpf7',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.822+0000',
      },
    ],
    page: {totalItems: 3},
  },
  thirdLevel1: {
    items: [
      {
        elementInstanceKey: '1_2_1',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'COMPLETED',
        type: 'START_EVENT',
        elementId: 'Event_1rw6vny',
        elementName: 'Event_1rw6vny',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.096+0000',
        endDate: '2022-09-23T11:00:42.508+0000',
      },
      {
        elementInstanceKey: '1_2_2',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'ACTIVE',
        type: 'USER_TASK',
        elementId: 'user_task',
        elementName: 'user_task',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.822+0000',
      },
      {
        elementInstanceKey: '1_2_3',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'ACTIVE',
        type: 'END_EVENT',
        elementId: 'Event_0ypvz5p',
        elementName: 'Event_0ypvz5p',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.822+0000',
      },
    ],
    page: {totalItems: 3},
  },
  thirdLevel2: {
    items: [
      {
        elementInstanceKey: '2_2_1',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'COMPLETED',
        type: 'START_EVENT',
        elementId: 'Event_1rw6vny',
        elementName: 'Event_1rw6vny',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.096+0000',
        endDate: '2022-09-23T11:00:42.508+0000',
      },
      {
        elementInstanceKey: '2_2_2',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'ACTIVE',
        type: 'USER_TASK',
        elementId: 'user_task',
        elementName: 'user_task',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.822+0000',
      },
      {
        elementInstanceKey: '2_2_3',
        processInstanceKey: '2251799813686118',
        processDefinitionKey: '123456789',
        processDefinitionId: 'nested_sub_process',
        state: 'ACTIVE',
        type: 'END_EVENT',
        elementId: 'Event_0ypvz5p',
        elementName: 'Event_0ypvz5p',
        hasIncident: false,
        tenantId: '<default>',
        startDate: '2022-09-23T10:59:43.822+0000',
      },
    ],
    page: {totalItems: 3},
  },
};

const Wrapper = ({children}: {children?: React.ReactNode}) => {
  useEffect(() => {
    return () => {
      modificationsStore.reset();
      instanceHistoryModificationStore.reset();
    };
  });

  return (
    <ProcessDefinitionKeyContext.Provider value="123">
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
          <Routes>
            <Route
              path={Paths.processInstance()}
              element={
                <TreeView label={'instance history'} hideLabel>
                  {children}
                </TreeView>
              }
            />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </ProcessDefinitionKeyContext.Provider>
  );
};

export {
  mockMultiInstanceProcessInstance,
  multiInstanceProcessInstance,
  nestedSubProcessesInstance,
  mockNestedSubProcessesInstance,
  mockNestedSubProcessInstance,
  adHocSubProcessesInstance,
  processInstanceId,
  eventSubProcessElementInstances,
  adHocNodeElementInstances,
  mockAdHocSubProcessesInstance,
  multipleSubprocessesWithOneRunningScopeMock,
  multipleSubprocessesWithNoRunningScopeMock,
  multipleSubprocessesWithTwoRunningScopesMock,
  eventSubprocessProcessInstance,
  mockEventSubprocessInstance,
  Wrapper,
};
