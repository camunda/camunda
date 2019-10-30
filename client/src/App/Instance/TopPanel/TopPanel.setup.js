/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {STATE} from 'modules/constants';

import {
  getActivityIdToActivityInstancesMap,
  getSelectableFlowNodes,
  createNodeMetaDataMap
} from '../service';

import {
  createInstance,
  createRawTreeNode,
  createDefinitions,
  createIncident,
  createDiagramNode
} from 'modules/testUtils';

const createDiagramNodes = () => {
  return {
    StartEvent1234: createDiagramNode({
      $type: 'bpmn:StartEvent',
      name: 'Start the Process',
      $instanceOf: type => type === 'bpmn:FlowNode'
    }),
    Service5678: createDiagramNode({
      $type: 'bpmn:ServiceTask',
      name: 'Do something',
      $instanceOf: type => type === 'bpmn:FlowNode'
    }),
    EndEvent1234: createDiagramNode({
      $type: 'bpmn:EndEvent',
      name: 'End the Process',
      $instanceOf: type => type === 'bpmn:FlowNode'
    })
  };
};

const createRawTree = () => {
  return {
    children: [
      createRawTreeNode({
        activityId: 'StartEvent1234',
        type: 'START_EVENT',
        state: STATE.COMPLETED
      }),
      createRawTreeNode({
        activityId: 'Service5678',
        type: 'SERVICE_TASK',
        state: STATE.COMPLETED
      }),
      createRawTreeNode({
        activityId: 'EndEvent1234',
        type: 'End_Event',
        state: STATE.COMPLETED
      })
    ]
  };
};

const mockIncidents = () => {
  return {
    count: 1,
    incidents: [
      createIncident({
        errorType: 'Condition error',
        flowNodeId: 'Service5678'
      })
    ],
    errorTypes: [
      {
        errorType: 'Condition error',
        count: 1
      }
    ],
    flowNodes: [
      {
        flowNodeId: 'Service5678',
        flowNodeName: 'Do something',
        count: 1
      }
    ]
  };
};

export const mockProps = {
  instance: createInstance(),
  incidents: mockIncidents(),
  diagramDefinitions: createDefinitions(),
  activityIdToActivityInstanceMap: getActivityIdToActivityInstancesMap(
    createRawTree()
  ),
  nodeMetaDataMap: createNodeMetaDataMap(
    getSelectableFlowNodes(createDiagramNodes())
  ),
  selection: {
    treeRowIds: [],
    flowNodeId: null
  },
  getCurrentMetadata: jest.fn(),
  onFlowNodeSelection: jest.fn(),
  onInstanceOperation: jest.fn(),
  onTreeRowSelection: jest.fn()
};

export const instanceWithIncident = {
  ...mockProps,
  instance: createInstance({
    state: STATE.INCIDENT
  })
};
