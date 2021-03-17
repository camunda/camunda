/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createIncident, createSequenceFlows} from 'modules/testUtils';

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

const mockSequenceFlows = createSequenceFlows();

export {mockIncidents, mockSequenceFlows};
