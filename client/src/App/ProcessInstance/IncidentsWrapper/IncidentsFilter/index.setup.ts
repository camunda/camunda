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
      errorType: {id: 'CONDITION_ERROR', name: 'Condition error'},
      flowNodeId: 'flowNodeId_exclusiveGateway',
    }),
    createIncident({
      errorType: {id: 'EXTRACT_VALUE_ERROR', name: 'Extract value error'},
      flowNodeId: 'flowNodeName_alwaysFailingTask',
    }),
  ],
  errorTypes: [
    {
      id: 'CONDITION_ERROR',
      name: 'Condition error',
      count: 2,
    },
    {id: 'EXTRACT_VALUE_ERROR', name: 'Extract value error', count: 1},
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
      count: 2,
    },
  ],
};

export {mockIncidents};
