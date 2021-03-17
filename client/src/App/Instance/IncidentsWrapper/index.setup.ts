/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createIncident} from 'modules/testUtils';
import {EXPAND_STATE} from 'modules/constants';

export const mockIncidents = {
  count: 2,
  incidents: [
    createIncident({
      errorType: 'Condition errortype',
      flowNodeId: 'flowNodeId_exclusiveGateway',
    }),
    createIncident({
      errorType: 'Extract value errortype',
      flowNodeId: 'flowNodeId_alwaysFailingTask',
    }),
  ],
  errorTypes: [
    {
      errorType: 'Condition errortype',
      count: 1,
    },
    {
      errorType: 'Extract value errortype',
      count: 1,
    },
  ],

  flowNodes: [
    {
      flowNodeId: 'flowNodeId_exclusiveGateway',
      flowNodeName: 'flowNodeName_exclusiveGateway',
      count: 1,
    },
    {
      flowNodeId: 'flowNodeId_alwaysFailingTask',
      flowNodeName: 'flowNodeName_alwaysFailingTask',
      count: 1,
    },
  ],
};

const defaultProps = {
  expandState: EXPAND_STATE.DEFAULT,
};

export const mockResolvedIncidents = {
  count: 1,
  incidents: [
    createIncident({
      errorType: 'Extract value error',
      flowNodeId: 'flowNodeId_alwaysFailingTask',
    }),
  ],
  errorTypes: [
    {
      errorType: 'Extract value error',
      count: 1,
    },
  ],
  flowNodes: [
    {
      flowNodeId: 'flowNodeId_alwaysFailingTask',
      flowNodeName: 'flowNodeName_alwaysFailingTask',
      count: 1,
    },
  ],
};

export const testData = {
  props: {
    default: defaultProps,
  },
};
