/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  DEFAULT_SORTING,
  DEFAULT_FILTER_CONTROLLED_VALUES
} from 'modules/constants';

import {
  groupedWorkflowsMock,
  createMockInstancesObject
} from 'modules/testUtils';

import {parsedDiagram} from 'modules/utils/bpmn';
import {formatGroupedWorkflows} from 'modules/utils/instance';

// props mocks
export const filterMock = {
  ...DEFAULT_FILTER_CONTROLLED_VALUES,
  active: true,
  incidents: true,
  completed: true,
  finished: true,
  ids: '424242, 434343',
  errorMessage: 'No%20data%20found%20for%20query%20$.foo.',
  startDate: '28 December 2018',
  endDate: '28 December 2018',
  workflow: 'demoProcess',
  version: '1',
  activityId: 'taskD'
};
export const mockInstances = createMockInstancesObject();

export const mockProps = {
  filter: filterMock,
  groupedWorkflows: formatGroupedWorkflows(groupedWorkflowsMock),
  workflowInstances: mockInstances.workflowInstances,
  filterCount: mockInstances.totalCount,
  workflowInstancesLoaded: true,
  firstElement: 1,
  onFirstElementChange: jest.fn(),
  sorting: DEFAULT_SORTING,
  onSort: jest.fn(),
  onFilterChange: jest.fn(),
  onFilterReset: jest.fn(),
  onFlowNodeSelection: jest.fn(),
  diagramModel: parsedDiagram,
  statistics: []
};
