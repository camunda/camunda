/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createIncident} from 'modules/testUtils';

const mockIncidents = {
  count: 2,
  incidents: [
    createIncident({
      errorType: 'Condition error',
      flowNodeId: 'flowNodeId_exclusiveGateway',
    }),
    createIncident({
      errorType: 'Extract value error',
      flowNodeName: 'flowNodeName_alwaysFailingTask',
    }),
  ],
  errorTypes: [
    {
      errorType: 'Condition error',
      count: 2,
    },
    {errorType: 'Extract value error', count: 1},
  ],
  flowNodes: [
    {
      flowNodeId: 'flowNodeId_exclusiveGateway',
      flowNodeName: 'flowNodeName_exclusiveGateway',
      count: 1,
    },
    {
      flowNodeId: 'flowNodeId_alwaysFailingTask',
      flowNodeName: 'flowNodeName_alwaysFailingTask',
      count: 2,
    },
  ],
};

const mockIncidentsWithManyErrors = {
  ...mockIncidents,
  errorTypes: [
    {
      errorType: 'error type 1',
      count: 1,
    },
    {
      errorType: 'error type 2',
      count: 1,
    },
    {
      errorType: 'error type 3',
      count: 1,
    },
    {
      errorType: 'error type 4',
      count: 1,
    },
    {
      errorType: 'error type 5',
      count: 1,
    },
    {
      errorType: 'error type 6',
      count: 1,
    },
  ],
};

const defaultProps = {
  selectedFlowNodes: [],
  selectedErrorTypes: [],
  onFlowNodeSelect: jest.fn(),
  onErrorTypeSelect: jest.fn(),
  onClearAll: jest.fn(),
};

const selectedErrorPillProps = {
  ...defaultProps,
  selectedFlowNodes: ['flowNodeId_exclusiveGateway'],
  selectedErrorTypes: ['Condition error'],
};

export {
  mockIncidents,
  mockIncidentsWithManyErrors,
  defaultProps,
  selectedErrorPillProps,
};
