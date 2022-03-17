/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  createInstance,
  createDiagramNode,
  createIncident,
} from 'modules/testUtils';

const createDiagramNodes = () => {
  return {
    StartEvent1234: createDiagramNode({
      $type: 'bpmn:StartEvent',
      name: 'Start the Process',
      $instanceOf: (type: any) => type === 'bpmn:FlowNode',
    }),
    Service5678: createDiagramNode({
      $type: 'bpmn:ServiceTask',
      name: 'Do something',
      $instanceOf: (type: any) => type === 'bpmn:FlowNode',
    }),
    EndEvent1234: createDiagramNode({
      $type: 'bpmn:EndEvent',
      name: 'End the Process',
      $instanceOf: (type: any) => type === 'bpmn:FlowNode',
    }),
  };
};

const mockIncidents = () => {
  return {
    count: 1,
    incidents: [
      createIncident({
        errorType: 'Condition error',
        flowNodeId: 'Service5678',
      }),
    ],
    errorTypes: [
      {
        errorType: 'Condition error',
        count: 1,
      },
    ],
    flowNodes: [
      {
        flowNodeId: 'Service5678',
        flowNodeName: 'Do something',
        count: 1,
      },
    ],
  };
};

const noIncidents = {count: 0, incidents: [], errorTypes: [], flowNodes: []};

const processInstance = createInstance({
  id: '4294980768',
  state: 'ACTIVE',
  processId: 'processId',
});

const processInstanceWithIncident = createInstance({
  id: '4294980768',
  state: 'INCIDENT',
});

export const testData = {
  fetch: {
    onPageLoad: {
      processXML: '<foo />',
      processInstance,
      processInstanceWithIncident,
      diagramNodes: createDiagramNodes(),
      incidents: mockIncidents(),
      noIncidents,
    },
  },
};
