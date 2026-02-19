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
  statistics: {
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
        state: 'COMPLETED',
        hasIncident: false,
        startDate: '2023-08-14T05:45:17.331+0000',
        endDate: '2023-08-14T05:45:17.331+0000',
        tenantId: '<default>',
      },
      {
        elementInstanceKey: '2251799813687151',
        processInstanceKey: '225179981395430',
        processDefinitionKey: '2251799813694848',
        processDefinitionId: 'orderProcess',
        elementId: 'ExclusiveGateway_1qqmrb8',
        elementName: 'Payment OK?',
        type: 'EXCLUSIVE_GATEWAY',
        state: 'COMPLETED',
        hasIncident: false,
        startDate: '2023-08-14T05:45:17.331+0000',
        endDate: '2023-08-14T05:45:17.331+0000',
        tenantId: '<default>',
      },
      {
        elementInstanceKey: '2251799813687153',
        processInstanceKey: '225179981395430',
        processDefinitionKey: '2251799813694848',
        processDefinitionId: 'orderProcess',
        elementId: 'EndEvent_042s0oc',
        elementName: 'EndEvent_042s0oc',
        type: 'END_EVENT',
        state: 'COMPLETED',
        hasIncident: false,
        startDate: '2023-08-14T05:45:17.331+0000',
        endDate: '2023-08-14T05:45:17.331+0000',
        tenantId: '<default>',
      },
    ],
    page: {totalItems: 4},
  },
};

export {completedOrderProcessInstance};
