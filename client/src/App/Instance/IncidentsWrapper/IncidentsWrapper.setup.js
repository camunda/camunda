/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createIncident, createInstance} from 'modules/testUtils';

const incidents = [
  createIncident({
    errorType: 'Condition error',
    flowNodeId: 'flowNodeId_exclusiveGateway'
  }),
  createIncident({
    errorType: 'Extract value error',
    flowNodeId: 'flowNodeId_alwaysFailingTask'
  })
];

const errorTypes = {
  'Condition error': {
    errorType: 'Condition error',
    count: 1
  },
  'Extract value error': {
    errorType: 'Extract value error',
    count: 1
  }
};
const flowNodes = {
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
};

const defaultProps = {
  instance: createInstance(),
  incidents: incidents,
  incidentsCount: 2,
  forceSpinner: false,
  selectedIncidents: ['1', '2', '3'],
  onIncidentOperation: jest.fn(),
  onIncidentSelection: jest.fn(),
  errorTypes: new Map(Object.entries(errorTypes)),
  flowNodes: new Map(Object.entries(flowNodes))
};

const incidentResolvedProps = {
  ...defaultProps,
  incidents: [incidents[0]],
  errorTypes: new Map(
    Object.entries({'Extract value error': errorTypes['Extract value error']})
  ),
  flowNodes: new Map(
    Object.entries({
      flowNodeId_alwaysFailingTask: flowNodes['flowNodeId_alwaysFailingTask']
    })
  )
};

export const testData = {
  props: {
    default: defaultProps,
    incidentResolved: incidentResolvedProps
  }
};
