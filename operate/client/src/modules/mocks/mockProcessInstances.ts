/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const mockProcessInstances = [
  {
    id: '0000000000000001',
    processId: '2251799813685611',
    processName: 'Instance Name',
    processVersion: 1,
    startDate: '2024-02-28T18:31:33.019+0000',
    endDate: null,
    state: 'ACTIVE',
    bpmnProcessId: 'signalEventProcess',
    hasActiveOperation: false,
    operations: [
      {
        state: 'SUCCESS',
        batchOperationId: 'f4be6304-a0e0-4976-b81b-7a07fb4e96e5',
      },
    ],
    parentInstanceId: null,
    rootInstanceId: null,
    callHierarchy: null,
    tenantId: '<default>',
    sortValues: [
      {
        value: '1709145093019',
        valueType: 'java.lang.Long',
      },
      {
        value: '2251799813777489',
        valueType: 'java.lang.Long',
      },
    ],
    permissions: [],
  },
  {
    id: '0000000000000002',
    processId: '2251799813685612',
    processName: 'Instance Name',
    processVersion: 1,
    startDate: '2024-02-28T18:31:33.019+0000',
    endDate: null,
    state: 'ACTIVE',
    bpmnProcessId: 'signalEventProcess',
    hasActiveOperation: false,
    operations: [
      {
        state: 'FAILED',
        batchOperationId: 'f4be6304-a0e0-4976-b81b-7a07fb4e96e5',
        errorMessage:
          'FIRST TEST ERROR: Unable to process operation: Command ‘MIGRATE’ rejected with code ‘INVALID_STATE’: Expected to migrate process instance ‘6567474937364’ byt active element with id ‘filterMapSubProcess’ has an unsupported type. The migration of a MULTI_INSTANCE_BODY is not supported. ',
      },
    ],
    parentInstanceId: null,
    rootInstanceId: null,
    callHierarchy: null,
    tenantId: '<default>',
    sortValues: [
      {
        value: '1709145093019',
        valueType: 'java.lang.Long',
      },
      {
        value: '2251799813777489',
        valueType: 'java.lang.Long',
      },
    ],
    permissions: [],
  },
  {
    id: '0000000000000003',
    processId: '2251799813685611',
    processName: 'Instance Name',
    processVersion: 1,
    startDate: '2024-02-28T18:31:33.019+0000',
    endDate: null,
    state: 'ACTIVE',
    bpmnProcessId: 'signalEventProcess',
    hasActiveOperation: false,
    operations: [
      {
        state: 'SUCCESS',
        batchOperationId: 'f4be6304-a0e0-4976-b81b-7a07fb4e96e5',
      },
    ],
    parentInstanceId: null,
    rootInstanceId: null,
    callHierarchy: null,
    tenantId: '<default>',
    sortValues: [
      {
        value: '1709145093019',
        valueType: 'java.lang.Long',
      },
      {
        value: '2251799813777489',
        valueType: 'java.lang.Long',
      },
    ],
    permissions: [],
  },
  {
    id: '0000000000000004',
    processId: '2251799813685612',
    processName: 'Instance Name',
    processVersion: 1,
    startDate: '2024-02-28T18:31:33.019+0000',
    endDate: null,
    state: 'ACTIVE',
    bpmnProcessId: 'signalEventProcess',
    hasActiveOperation: false,
    operations: [
      {
        state: 'FAILED',
        batchOperationId: 'f4be6304-a0e0-4976-b81b-7a07fb4e96e5',
        errorMessage:
          'SECOND TEST ERROR: Unable to process operation: Command ‘MIGRATE’ rejected with code ‘INVALID_STATE’: Expected to migrate process instance ‘6567474937364’ byt active element with id ‘filterMapSubProcess’ has an unsupported type. The migration of a MULTI_INSTANCE_BODY is not supported. ',
      },
    ],
    parentInstanceId: null,
    rootInstanceId: null,
    callHierarchy: null,
    tenantId: '<default>',
    sortValues: [
      {
        value: '1709145093019',
        valueType: 'java.lang.Long',
      },
      {
        value: '2251799813777489',
        valueType: 'java.lang.Long',
      },
    ],
    permissions: [],
  },
  {
    id: '0000000000000005',
    processId: '2251799813685611',
    processName: 'Instance Name',
    processVersion: 1,
    startDate: '2024-02-28T18:31:33.019+0000',
    endDate: null,
    state: 'ACTIVE',
    bpmnProcessId: 'signalEventProcess',
    hasActiveOperation: false,
    operations: [
      {
        state: 'SUCCESS',
        batchOperationId: 'f4be6304-a0e0-4976-b81b-7a07fb4e96e5',
      },
    ],
    parentInstanceId: null,
    rootInstanceId: null,
    callHierarchy: null,
    tenantId: '<default>',
    sortValues: [
      {
        value: '1709145093019',
        valueType: 'java.lang.Long',
      },
      {
        value: '2251799813777489',
        valueType: 'java.lang.Long',
      },
    ],
    permissions: [],
  },
];

export {mockProcessInstances};
