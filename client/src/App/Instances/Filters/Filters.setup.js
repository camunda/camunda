/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  createFilter,
  groupedWorkflowsMock as defaultGroupedWorkflowsMock
} from 'modules/testUtils';

export const groupedWorkflowsMock = defaultGroupedWorkflowsMock;

// transformed groupedWorkflowsMock in an object structure
export const workflows = {
  demoProcess: {
    ...groupedWorkflowsMock[0]
  },
  orderProcess: {
    ...groupedWorkflowsMock[1]
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
  ...createFilter(),
  ids: 'a, b, c',
  errorMessage: 'This is an error message',
  startDate: '2018-10-08',
  endDate: '2018-10-10',
  workflow: 'demoProcess',
  version: '2',
  activityId: '4'
};
