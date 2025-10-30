/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
        ids: ['2251799813685594'],
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
        ids: ['2251799813685594'],
        excludeIds: [],
        processIds: ['demoProcess1'],
      },
    },
    mockOperationCreated: createBatchOperation({id: '5'}),
  },
};

export {mockData};
