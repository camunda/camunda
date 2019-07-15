/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {DEFAULT_FILTER} from 'modules/constants';

export const groupedWorkflowsMock = [
  {
    bpmnProcessId: 'demoProcess',
    name: 'New demo process',
    workflows: [
      {
        id: '6',
        name: 'New demo process',
        version: 3,
        bpmnProcessId: 'demoProcess'
      },
      {
        id: '4',
        name: 'Demo process',
        version: 2,
        bpmnProcessId: 'demoProcess'
      },
      {
        id: '1',
        name: 'Demo process',
        version: 1,
        bpmnProcessId: 'demoProcess'
      }
    ]
  },
  {
    bpmnProcessId: 'orderProcess',
    name: 'Order',
    workflows: []
  }
];

// transformed groupedWorkflowsMock in an object structure
export const workflows = {
  demoProcess: {
    bpmnProcessId: 'demoProcess',
    name: 'New demo process',
    workflows: [
      {
        id: '6',
        name: 'New demo process',
        version: 3,
        bpmnProcessId: 'demoProcess'
      },
      {
        id: '4',
        name: 'Demo process',
        version: 2,
        bpmnProcessId: 'demoProcess'
      },
      {
        id: '1',
        name: 'Demo process',
        version: 1,
        bpmnProcessId: 'demoProcess'
      }
    ]
  },
  orderProcess: {
    bpmnProcessId: 'orderProcess',
    name: 'Order',
    workflows: []
  }
};

export const mockProps = {
  onFilterChange: jest.fn(),
  onFilterReset: jest.fn(),
  filterCount: 1,
  selectableFlowNodes: []
};

export const mockPropsWithSelectableFlowNodes = {
  onFilterChange: jest.fn(),
  onFilterReset: jest.fn(),
  filterCount: 1,
  selectableFlowNodes: [
    {id: 'TaskA', $type: 'bpmn:StartEvent', name: 'task A'},
    {id: 'TaskB', $type: 'bpmn:EndEvent'}
  ]
};

export const COMPLETE_FILTER = {
  ...DEFAULT_FILTER,
  ids: 'a, b, c',
  errorMessage: 'This is an error message',
  startDate: '08 October 2018',
  endDate: '10-10-2018',
  workflow: 'demoProcess',
  version: '2',
  activityId: '4'
};
