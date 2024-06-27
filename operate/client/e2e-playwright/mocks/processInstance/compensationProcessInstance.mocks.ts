/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {open} from 'modules/mocks/diagrams';
import {InstanceMock} from '.';

const compensationProcessInstance: InstanceMock = {
  detail: {
    id: '2551799813954282',
    processId: '2251799813694848',
    processName: 'Compensation Process',
    processVersion: 1,
    startDate: '2023-10-02T06:10:47.979+0000',
    endDate: '2023-10-02T06:15:48.042+0000',
    state: 'COMPLETED',
    bpmnProcessId: 'CompensationProcess',
    hasActiveOperation: false,
    operations: [],
    parentInstanceId: null,
    rootInstanceId: null,
    callHierarchy: [],
    sortValues: [''],
    tenantId: '<default>',
  },
  flowNodeInstances: {
    '9007199254744341': {
      children: [
        {
          id: '9007199254744343',
          type: 'START_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'StartEvent_1',
          startDate: '2024-06-27T07:58:44.796+0000',
          endDate: '2024-06-27T07:58:44.796+0000',
          treePath: '9007199254744341/9007199254744343',
          sortValues: [],
        },
        {
          id: '9007199254744345',
          type: 'EXCLUSIVE_GATEWAY',
          state: 'COMPLETED',
          flowNodeId: 'Gateway_1',
          startDate: '2024-06-27T07:58:44.796+0000',
          endDate: '2024-06-27T07:58:44.796+0000',
          treePath: '9007199254744341/9007199254744345',
          sortValues: [],
        },
        {
          id: '9007199254744347',
          type: 'TASK',
          state: 'COMPLETED',
          flowNodeId: 'Task_1',
          startDate: '2024-06-27T07:58:44.796+0000',
          endDate: '2024-06-27T07:58:44.796+0000',
          treePath: '9007199254744341/9007199254744347',
          sortValues: [],
        },
        {
          id: '9007199254744350',
          type: 'INTERMEDIATE_THROW_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'CompensationEvent_1',
          startDate: '2024-06-27T07:58:44.796+0000',
          endDate: '2024-06-27T07:58:44.796+0000',
          treePath: '9007199254744341/9007199254744350',
          sortValues: [],
        },
        {
          id: '9007199254744351',
          type: 'BOUNDARY_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'CompensationBoundaryEvent_1',
          startDate: '2024-06-27T07:58:44.796+0000',
          endDate: '2024-06-27T07:58:44.796+0000',
          treePath: '9007199254744341/9007199254744351',
          sortValues: [],
        },
        {
          id: '9007199254744352',
          type: 'TASK',
          state: 'COMPLETED',
          flowNodeId: 'CompensationTask_1',
          startDate: '2024-06-27T07:58:44.796+0000',
          endDate: '2024-06-27T07:58:44.796+0000',
          treePath: '9007199254744341/9007199254744352',
          sortValues: [],
        },
        {
          id: '9007199254744354',
          type: 'EXCLUSIVE_GATEWAY',
          state: 'COMPLETED',
          flowNodeId: 'Gateway_2',
          startDate: '2024-06-27T07:58:44.796+0000',
          endDate: '2024-06-27T07:58:44.796+0000',
          treePath: '9007199254744341/9007199254744354',
          sortValues: [],
        },
        {
          id: '9007199254744356',
          type: 'END_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'EndEvent_1',
          startDate: '2024-06-27T07:58:44.796+0000',
          endDate: '2024-06-27T07:58:44.796+0000',
          treePath: '9007199254744341/9007199254744356',
          sortValues: [],
        },
      ],
      running: null,
    },
  },
  statistics: [
    {
      activityId: 'CompensationBoundaryEvent_1',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      activityId: 'CompensationEvent_1',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      activityId: 'CompensationTask_1',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      activityId: 'EndEvent_1',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      activityId: 'Gateway_1',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      activityId: 'Gateway_2',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      activityId: 'StartEvent_1',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {activityId: 'Task_1', active: 0, canceled: 0, incidents: 0, completed: 1},
  ],
  xml: open('CompensationProcess.bpmn'),
  sequenceFlows: [
    {processInstanceId: '9007199254744341', activityId: 'Flow_1'},
    {processInstanceId: '9007199254744341', activityId: 'Flow_2'},
    {processInstanceId: '9007199254744341', activityId: 'Flow_4'},
    {processInstanceId: '9007199254744341', activityId: 'Flow_6'},
    {processInstanceId: '9007199254744341', activityId: 'Flow_8'},
  ],
  variables: [],
};

export {compensationProcessInstance};
