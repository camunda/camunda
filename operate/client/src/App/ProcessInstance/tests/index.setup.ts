/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createInstance, createDiagramNode} from 'modules/testUtils';

const createDiagramNodes = () => {
  return {
    StartEvent1234: createDiagramNode({
      $type: 'bpmn:StartEvent',
      name: 'Start the Process',
      $instanceOf: (type: string) => type === 'bpmn:FlowNode',
    }),
    Service5678: createDiagramNode({
      $type: 'bpmn:ServiceTask',
      name: 'Do something',
      $instanceOf: (type: string) => type === 'bpmn:FlowNode',
    }),
    EndEvent1234: createDiagramNode({
      $type: 'bpmn:EndEvent',
      name: 'End the Process',
      $instanceOf: (type: string) => type === 'bpmn:FlowNode',
    }),
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

const completedProcessInstance = createInstance({
  id: '4294980768',
  state: 'COMPLETED',
});

export const testData = {
  fetch: {
    onPageLoad: {
      processXML: '<foo />',
      processInstance,
      processInstanceWithIncident,
      diagramNodes: createDiagramNodes(),
      noIncidents,
      completedProcessInstance,
    },
  },
};
