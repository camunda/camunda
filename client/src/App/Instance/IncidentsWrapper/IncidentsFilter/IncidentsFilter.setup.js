/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createIncident} from 'modules/testUtils';

const incidentTableProps = {
  count: 2,
  incidents: [
    createIncident({
      errorType: 'Condition error',
      flowNodeId: 'flowNodeId_exclusiveGateway'
    }),
    createIncident({
      errorType: 'Extract value error',
      flowNodeName: 'flowNodeName_alwaysFailingTask'
    })
  ],
  errorTypes: new Map(
    Object.entries({
      'Condition error': {
        errorType: 'Condition error',
        count: 1
      },
      'Extract value error': {
        errorType: 'Extract value error',
        count: 1
      }
    })
  ),
  flowNodes: new Map(
    Object.entries({
      flowNodeId_exclusiveGateway: {
        flowNodeId: 'flowNodeId_exclusiveGateway',
        flowNodeName: 'flowNodeName_exclusiveGateway',
        count: 1
      },
      flowNodeId_alwaysFailingTask: {
        flowNodeId: 'flowNodeId_alwaysFailingTask',
        flowNodeName: 'flowNodeName_alwaysFailingTask',
        count: 1
      }
    })
  )
};

const defaultProps = {
  errorTypes: incidentTableProps.errorTypes,
  flowNodes: incidentTableProps.flowNodes,
  selectedFlowNodes: [],
  selectedErrorTypes: [],
  onFlowNodeSelect: jest.fn(),
  onErrorTypeSelect: jest.fn(),
  onClearAll: jest.fn()
};

const newErrorTypeMap = new Map(incidentTableProps.errorTypes);
newErrorTypeMap
  .set('error type 1', {
    errorType: 'error type 1',
    count: 1
  })
  .set('error type 2', {
    errorType: 'error type 2',
    count: 1
  })
  .set('error type 3', {
    errorType: 'error type 3',
    count: 1
  })
  .set('error type 4', {
    errorType: 'error type 4',
    count: 1
  });

const manyErrorsProps = {
  ...defaultProps,
  errorTypes: newErrorTypeMap
};

const selectedErrorPillProps = {
  ...defaultProps,
  selectedFlowNodes: ['flowNodeId_exclusiveGateway'],
  selectedErrorTypes: ['Condition error']
};

export const testData = {
  props: {
    default: defaultProps,
    manyErrors: manyErrorsProps,
    selectedErrorPill: selectedErrorPillProps
  }
};
