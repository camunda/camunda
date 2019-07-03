/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createIncident, createInstance} from 'modules/testUtils';

const incidentTableProps = {
  count: 2,
  incidents: [
    createIncident({
      errorType: 'Condition error',
      flowNodeId: 'flowNodeId_exclusiveGateway'
    }),
    createIncident({
      errorType: 'Extract value error',
      flowNodeId: 'flowNodeId_alwaysFailingTask'
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
  instance: createInstance(),
  incidents: incidentTableProps.incidents,
  incidentsCount: incidentTableProps.count,
  forceSpinner: false,
  selectedIncidents: ['1', '2', '3'],
  onIncidentOperation: jest.fn(),
  onIncidentSelection: jest.fn(),
  errorTypes: incidentTableProps.errorTypes,
  flowNodes: incidentTableProps.flowNodes
};

const newErrorTypeMap = new Map(incidentTableProps.errorTypes);
const newFlowNodeMap = new Map(incidentTableProps.flowNodes);

newErrorTypeMap.delete('Condition error');
newFlowNodeMap.delete('flowNodeId_exclusiveGateway');

const incidentResolvedProps = {
  ...defaultProps,
  incidents: [incidentTableProps.incidents[0]],
  errorTypes: newErrorTypeMap,
  flowNodes: newFlowNodeMap
};

export const testData = {
  props: {
    default: defaultProps,
    incidentResolved: incidentResolvedProps
  }
};
