/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createIncident} from 'modules/testUtils';

// TODO: remove when IS_NEXT_INCIDENTS is removed
const mockIncidentsLegacy = {
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

const mockIncidents = {
  count: 2,
  incidents: [
    createIncident({
      errorType: {id: 'CONDITION_ERROR', name: 'Condition error'},
      flowNodeId: 'flowNodeId_exclusiveGateway',
    }),
    createIncident({
      errorType: {id: 'EXTRACT_VALUE_ERROR', name: 'Extract value error'},
      flowNodeName: 'flowNodeName_alwaysFailingTask',
    }),
  ],
  errorTypes: [
    {
      id: 'CONDITION_ERROR',
      name: 'Condition error',
      count: 2,
    },
    {id: 'EXTRACT_VALUE_ERROR', name: 'Extract value error', count: 1},
  ],
  flowNodes: [
    {
      id: 'flowNodeId_exclusiveGateway',
      name: 'flowNodeName_exclusiveGateway',
      count: 1,
    },
    {
      id: 'flowNodeId_alwaysFailingTask',
      name: 'flowNodeName_alwaysFailingTask',
      count: 2,
    },
  ],
};

const mockIncidentsWithManyErrors = {
  ...mockIncidents,
  errorTypes: [
    {
      name: 'error type 1',
      id: 'ERROR_TYPE_1',
      count: 1,
    },
    {
      name: 'error type 2',
      id: 'ERROR_TYPE_2',
      count: 1,
    },
    {
      name: 'error type 3',
      id: 'ERROR_TYPE_3',
      count: 1,
    },
    {
      name: 'error type 4',
      id: 'ERROR_TYPE_4',
      count: 1,
    },
    {
      name: 'error type 5',
      id: 'ERROR_TYPE_5',
      count: 1,
    },
    {
      name: 'error type 6',
      id: 'ERROR_TYPE_6',
      count: 1,
    },
  ],
};

// TODO: remove when IS_NEXT_INCIDENTS is removed
const mockIncidentsWithManyErrorsLegacy = {
  ...mockIncidentsLegacy,
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

// TODO: remove when IS_NEXT_INCIDENTS is removed
const defaultProps = {
  selectedFlowNodes: [],
  selectedErrorTypes: [],
  onFlowNodeSelect: jest.fn(),
  onErrorTypeSelect: jest.fn(),
  onClearAll: jest.fn(),
};

// TODO: remove when IS_NEXT_INCIDENTS is removed
const selectedErrorPillProps = {
  ...defaultProps,
  selectedFlowNodes: ['flowNodeId_exclusiveGateway'],
  selectedErrorTypes: ['Condition error'],
};

export {
  mockIncidents,
  mockIncidentsLegacy,
  mockIncidentsWithManyErrors,
  mockIncidentsWithManyErrorsLegacy,
  defaultProps,
  selectedErrorPillProps,
};
