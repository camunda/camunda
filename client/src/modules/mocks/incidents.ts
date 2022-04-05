/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const mockIncidents = {
  count: 1,
  incidents: [
    {
      id: '2251799813700301',
      errorType: 'No more retries left',
      errorMessage: 'Cannot connect to server delivery05',
      flowNodeId: 'Task_162x79i',
      flowNodeInstanceId: '2251799813699889',
      jobId: '2251799813699901',
      creationTime: '2020-10-08T09:18:58.258+0000',
      hasActiveOperation: false,
      lastOperation: null,
    },
  ],
  errorTypes: [
    {
      id: 'NO_MORE_RETRIES',
      type: 'No more retries left',
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
