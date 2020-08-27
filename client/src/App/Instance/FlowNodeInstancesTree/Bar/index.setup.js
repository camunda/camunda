/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createFlowNodeInstance} from 'modules/testUtils';
import {TYPE} from 'modules/constants';

const mockStartNode = createFlowNodeInstance({
  type: 'START_EVENT',
  id: 'someflowNodeIde',
  name: 'Some Name',
  typeDetails: {
    elementType: TYPE.EVENT_START,
    eventType: undefined,
  },
});

const mockMultiInstanceBody = createFlowNodeInstance({
  type: 'MULTI_INSTANCE_BODY',
  id: 'someflowNodeIde',
  name: 'Some Name',
  typeDetails: {
    elementType: TYPE.EVENT_START,
    eventType: undefined,
  },
});

export {mockStartNode, mockMultiInstanceBody};
