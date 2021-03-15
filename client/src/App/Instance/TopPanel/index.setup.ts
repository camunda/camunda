/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {STATE} from 'modules/constants';
import {
  createIncident,
  createRawTreeNode,
  createSequenceFlows,
  createEvents,
} from 'modules/testUtils';

const mockIncidents = {
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

const createRawTree = () => {
  return {
    children: [
      createRawTreeNode({
        activityId: 'StartEvent1234',
        type: 'START_EVENT',
        state: STATE.COMPLETED,
      }),
      createRawTreeNode({
        activityId: 'Service5678',
        type: 'SERVICE_TASK',
        state: STATE.COMPLETED,
      }),
      createRawTreeNode({
        activityId: 'EndEvent1234',
        type: 'End_Event',
        state: STATE.COMPLETED,
      }),
    ],
  };
};

const mockSequenceFlows = createSequenceFlows();

const mockEvents = createEvents(createRawTree().children);

export {mockIncidents, mockSequenceFlows, mockEvents, createRawTree};
