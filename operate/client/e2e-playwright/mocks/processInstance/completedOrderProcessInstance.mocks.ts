/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {openFile} from '@/utils/openFile';
import {completedInstance, type InstanceMock} from './index';

const completedOrderProcessInstance: InstanceMock = {
  ...completedInstance,
  detail: {
    ...completedInstance.detail,
    id: '225179981395430',
    processName: 'Order process',
    bpmnProcessId: 'orderProcess',
    processVersion: 1,
  },
  detailV2: {
    ...completedInstance.detailV2,
    processInstanceKey: '225179981395430',
    processDefinitionName: 'Order process',
    processDefinitionVersion: 1,
    processDefinitionId: 'orderProcess',
  },
  callHierarchy: [],
  xml: openFile('./e2e-playwright/mocks/resources/orderProcess.bpmn'),
  statisticsV2: {
    items: [
      {
        elementId: 'EndEvent_042s0oc',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
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
    {
      processInstanceId: '225179981395430',
      activityId: 'SequenceFlow_1s6g17c',
    },
    {
      processInstanceId: '225179981395430',
      activityId: 'SequenceFlow_1dq2rqw',
    },
    {
      processInstanceId: '225179981395430',
      activityId: 'SequenceFlow_19klrd3',
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
      {
        processInstanceKey: '225179981395430',
        elementId: 'SequenceFlow_1s6g17c',
        tenantId: '',
        processDefinitionId: '',
        processDefinitionKey: '',
        sequenceFlowId: '',
      },
      {
        processInstanceKey: '225179981395430',
        elementId: 'SequenceFlow_1dq2rqw',
        tenantId: '',
        processDefinitionId: '',
        processDefinitionKey: '',
        sequenceFlowId: '',
      },
      {
        processInstanceKey: '225179981395430',
        elementId: 'SequenceFlow_19klrd3',
        tenantId: '',
        processDefinitionId: '',
        processDefinitionKey: '',
        sequenceFlowId: '',
      },
    ],
  },
  flowNodeInstances: {
    '225179981395430': {
      children: [
        {
          id: '2251799813687146',
          type: 'START_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'StartEvent_1',
          startDate: '2023-08-14T05:45:17.331+0000',
          endDate: '2023-08-14T05:45:17.331+0000',
          treePath: '225179981395430/2251799813687146',
          sortValues: ['', ''],
        },
        {
          id: '2251799813687150',
          type: 'SERVICE_TASK',
          state: 'COMPLETED',
          flowNodeId: 'checkPayment',
          startDate: '2023-08-14T05:45:17.331+0000',
          endDate: '2023-08-14T05:45:17.331+0000',
          treePath: '225179981395430/2251799813687150',
          sortValues: ['', ''],
        },
        {
          id: '2251799813687150',
          type: 'EXCLUSIVE_GATEWAY',
          state: 'COMPLETED',
          flowNodeId: 'ExclusiveGateway_1qqmrb8',
          startDate: '2023-08-14T05:45:17.331+0000',
          endDate: '2023-08-14T05:45:17.331+0000',
          treePath: '225179981395430/2251799813687151',
          sortValues: ['', ''],
        },
        {
          id: '2251799813687150',
          type: 'END_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'EndEvent_042s0oc',
          startDate: '2023-08-14T05:45:17.331+0000',
          endDate: '2023-08-14T05:45:17.331+0000',
          treePath: '225179981395430/2251799813687153',
          sortValues: ['', ''],
        },
      ],
      running: null,
    },
  },
};

export {completedOrderProcessInstance};
