/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  createWorkflow,
  createIncidentsByError,
  createInstanceByError,
} from 'modules/testUtils';

const mockIncidentsByError = createIncidentsByError([
  createInstanceByError({
    workflows: [createWorkflow()],
  }),
  createInstanceByError({
    errorMessage: 'No space left on device.',
    workflows: [
      createWorkflow({name: 'workflowA', version: 42}),
      createWorkflow({name: 'workflowB', version: 23}),
    ],
  }),
]);
const mockErrorResponse = {error: 'an error occured'};
const mockEmptyResponse: any = [];
export {mockIncidentsByError, mockErrorResponse, mockEmptyResponse};
