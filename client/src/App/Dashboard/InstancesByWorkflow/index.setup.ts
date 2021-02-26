/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createWorkflow, createInstanceByWorkflow} from 'modules/testUtils';

const mockWithSingleVersion = [
  createInstanceByWorkflow({
    workflows: [createWorkflow()],
  }),
];

const mockWithMultipleVersions = [
  createInstanceByWorkflow({
    instancesWithActiveIncidentsCount: 65,
    activeInstancesCount: 136,
    workflowName: 'Order process',
    bpmnProcessId: 'orderProcess',
    workflows: [
      createWorkflow({name: 'First Version', version: 1}),
      createWorkflow({name: 'Second Version', version: 2}),
    ],
  }),
];

const mockErrorResponse = {error: 'an error occured'};
const mockEmptyResponse: any = [];

export {
  mockWithSingleVersion,
  mockWithMultipleVersions,
  mockErrorResponse,
  mockEmptyResponse,
};
