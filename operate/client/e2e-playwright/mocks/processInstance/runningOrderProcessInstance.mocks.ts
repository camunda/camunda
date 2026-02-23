/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {openFile} from '@/utils/openFile';
import {runningInstance} from './';
import type {InstanceMock} from '.';

const runningOrderProcessInstance: InstanceMock = {
  ...runningInstance,
  detail: {
    ...runningInstance.detail,
    id: '225179981395430',
    processName: 'Order process',
    bpmnProcessId: 'orderProcess',
  },
  detailV2: {
    ...runningInstance.detailV2,
    processInstanceKey: '225179981395430',
    processDefinitionName: 'Order process',
    processDefinitionId: 'orderProcess',
  },
  callHierarchy: [],
  xml: openFile('./e2e-playwright/mocks/resources/orderProcess.bpmn'),
  statistics: {
    items: [
      {
        elementId: 'checkPayment',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
    ],
  },
  variables: [
    {
      variableKey: '2251799813687144-signalNumber',
      name: 'orderNumber',
      value: '47',
      isTruncated: false,
      tenantId: '',
      processInstanceKey: '2251799813687144',
      scopeKey: '2251799813687144',
    },
  ],
  sequenceFlows: [
    {
      processInstanceId: '225179981395430',
      activityId: 'SequenceFlow_0j6tsnn',
    },
  ],
  sequenceFlowsV2: {
    items: [
      {
        processInstanceKey: '225179981395430',
        elementId: 'SequenceFlow_0j6tsnn',
        tenantId: '',
        processDefinitionId: '',
        processDefinitionKey: '',
        sequenceFlowId: '',
      },
    ],
  },
  elementInstances: {
    items: [
      {
        elementInstanceKey: '2251799813687146',
        processInstanceKey: '225179981395430',
        processDefinitionKey: '2251799813694848',
        processDefinitionId: 'orderProcess',
        elementId: 'StartEvent_1',
        elementName: 'Order received',
        type: 'START_EVENT',
        state: 'COMPLETED',
        hasIncident: false,
        startDate: '2023-08-14T05:45:17.331+0000',
        endDate: '2023-08-14T05:45:17.331+0000',
        tenantId: '<default>',
      },
      {
        elementInstanceKey: '2251799813687150',
        processInstanceKey: '225179981395430',
        processDefinitionKey: '2251799813694848',
        processDefinitionId: 'orderProcess',
        elementId: 'checkPayment',
        elementName: 'Check payment',
        type: 'SERVICE_TASK',
        state: 'ACTIVE',
        hasIncident: false,
        startDate: '2023-08-14T05:45:17.331+0000',
        tenantId: '<default>',
      },
    ],
    page: {totalItems: 2},
  },
};

export {runningOrderProcessInstance};
