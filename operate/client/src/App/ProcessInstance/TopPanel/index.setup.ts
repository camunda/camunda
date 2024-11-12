/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

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

export {mockIncidents, mockSequenceFlows};
