/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {createIncident} from 'modules/testUtils';

const mockIncidents = {
  count: 2,
  incidents: [
    createIncident({
      errorType: {name: 'Condition errortype', id: 'CONDITION_ERROR'},
      flowNodeId: 'flowNodeId_exclusiveGateway',
    }),
    createIncident({
      errorType: {name: 'Extract value errortype', id: 'EXTRACT_VALUE_ERROR'},
      flowNodeId: 'flowNodeId_alwaysFailingTask',
    }),
  ],
  errorTypes: [
    {
      id: 'CONDITION_ERROR',
      name: 'Condition errortype',
      count: 1,
    },
    {
      id: 'EXTRACT_VALUE_ERROR',
      name: 'Extract value errortype',
      count: 1,
    },
  ],

  flowNodes: [
    {
      id: 'flowNodeId_exclusiveGateway',
      name: 'flowNodeName_exclusiveGateway',
      count: 1,
    },
    {
      id: 'flowNodeId_alwaysFailingTask',
      name: 'flowNodeName_alwaysFailingTask',
      count: 1,
    },
  ],
};

const mockResolvedIncidents = {
  count: 1,
  incidents: [
    createIncident({
      errorType: {name: 'Extract value errortype', id: 'EXTRACT_VALUE_ERROR'},
      flowNodeId: 'flowNodeId_alwaysFailingTask',
    }),
  ],
  errorTypes: [
    {
      id: 'EXTRACT_VALUE_ERROR',
      name: 'Extract value errortype',
      count: 1,
    },
  ],
  flowNodes: [
    {
      id: 'flowNodeId_alwaysFailingTask',
      name: 'flowNodeName_alwaysFailingTask',
      count: 1,
    },
  ],
};

export {mockIncidents, mockResolvedIncidents};
