/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {open} from 'modules/mocks/diagrams';
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
  xml: open('orderProcess.bpmn'),
  statistics: [
    {
      activityId: 'checkPayment',
      active: 1,
      canceled: 0,
      incidents: 0,
      completed: 0,
    },
  ],
  variables: [
    {
      id: '2251799813687144-signalNumber',
      name: 'orderNumber',
      value: '47',
      isPreview: false,
      hasActiveOperation: false,
      isFirst: true,
      sortValues: [''],
    },
  ],
  sequenceFlows: [
    {
      processInstanceId: '225179981395430',
      activityId: 'SequenceFlow_0j6tsnn',
    },
  ],
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
          state: 'ACTIVE',
          flowNodeId: 'checkPayment',
          startDate: '2023-08-14T05:45:17.331+0000',
          endDate: null,
          treePath: '225179981395430/2251799813687150',
          sortValues: ['', ''],
        },
      ],
      running: null,
    },
  },
};

export {runningOrderProcessInstance};
