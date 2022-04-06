/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
