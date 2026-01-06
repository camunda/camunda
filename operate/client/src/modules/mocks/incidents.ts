/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const mockIncidents = {
  count: 1,
  incidents: [
    {
      id: '2251799813700301',
      errorType: {id: '1', name: 'No more retries left'},
      errorMessage: 'Cannot connect to server delivery05',
      flowNodeId: 'Task_162x79i',
      flowNodeInstanceId: '2251799813699889',
      jobId: '2251799813699901',
      creationTime: '2020-10-08T09:18:58.258+0000',
      hasActiveOperation: false,
      lastOperation: null,
      rootCauseInstance: {
        instanceId: '2251799813695335',
        processDefinitionId: '2251799813687515',
        processDefinitionName: 'Event based gateway with timer start',
      },
    },
  ],
  errorTypes: [
    {
      id: 'NO_MORE_RETRIES',
      name: 'No more retries left',
      count: 1,
    },
  ],
  flowNodes: [
    {
      id: 'Task_162x79i',
      count: 1,
    },
  ],
};

export {mockIncidents};
