/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {createBatchOperation} from 'modules/testUtils';

const baseQuery = {
  running: true,
  incidents: true,
  active: true,
};

const mockData = {
  // in this case all instances are selected and no filter is set
  noFilterSelectAll: {
    expectedBody: {
      operationType: 'RESOLVE_INCIDENT',
      query: {
        ...baseQuery,
        ids: [],
        excludeIds: [],
      },
    },
    mockOperationCreated: createBatchOperation({id: '1'}),
  },
  // in this case all instances are selected and an id filter is set
  setFilterSelectAll: {
    expectedBody: {
      operationType: 'RESOLVE_INCIDENT',
      query: {
        ...baseQuery,
        ids: ['1'],
        excludeIds: [],
      },
    },
    mockOperationCreated: createBatchOperation({id: '2'}),
  },
  // in this case one instance is selected and an id filter is set
  setFilterSelectOne: {
    expectedBody: {
      operationType: 'RESOLVE_INCIDENT',
      query: {
        ...baseQuery,
        ids: ['1'],
        excludeIds: [],
      },
    },
    mockOperationCreated: createBatchOperation({id: '3'}),
  },
  // in this case one instance is excluded and an id filter is set
  setFilterExcludeOne: {
    expectedBody: {
      operationType: 'RESOLVE_INCIDENT',
      query: {
        ...baseQuery,
        ids: ['1', '2'],
        excludeIds: ['1'],
      },
    },
    mockOperationCreated: createBatchOperation({id: '4'}),
  },
  // in this case all instances are selected and a process filter is set
  setProcessFilterSelectOne: {
    expectedBody: {
      operationType: 'RESOLVE_INCIDENT',
      query: {
        ...baseQuery,
        ids: ['1'],
        excludeIds: [],
        processIds: ['demoProcess1'],
      },
    },
    mockOperationCreated: createBatchOperation({id: '5'}),
  },
};

export {mockData};
