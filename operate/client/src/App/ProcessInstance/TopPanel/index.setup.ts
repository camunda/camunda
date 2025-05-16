/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {SequenceFlow} from '@vzeta/camunda-api-zod-schemas/operate';
import {createIncident, createSequenceFlows} from 'modules/testUtils';

const mockIncidents = {
  count: 1,
  incidents: [
    createIncident({
      errorType: {
        name: 'Condition error',
        id: 'CONDITION_ERROR',
      },
      flowNodeId: 'Service5678',
    }),
  ],
  errorTypes: [
    {
      id: 'Condition error',
      name: 'Condition error',
      count: 1,
    },
  ],
  flowNodes: [
    {
      id: 'Service5678',
      name: 'Do something',
      count: 1,
    },
  ],
};

const mockSequenceFlows = createSequenceFlows();
const mockSequenceFlowsV2: SequenceFlow[] = [
  {
    processInstanceKey: '2251799813693731',
    sequenceFlowId: 'SequenceFlow_0drux68',
    processDefinitionId: '123',
    processDefinitionKey: '123',
    tenantId: '',
    elementId: '',
  },
  {
    processInstanceKey: '2251799813693731',
    sequenceFlowId: 'SequenceFlow_0j6tsnn',
    processDefinitionId: '123',
    processDefinitionKey: '123',
    tenantId: '',
    elementId: '',
  },
  {
    processInstanceKey: '2251799813693731',
    sequenceFlowId: 'SequenceFlow_1dwqvrt',
    processDefinitionId: '123',
    processDefinitionKey: '123',
    tenantId: '',
    elementId: '',
  },
  {
    processInstanceKey: '2251799813693731',
    sequenceFlowId: 'SequenceFlow_1fgekwd',
    processDefinitionId: '123',
    processDefinitionKey: '123',
    tenantId: '',
    elementId: '',
  },
];

export {mockIncidents, mockSequenceFlows, mockSequenceFlowsV2};
