/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const baseQuery = {
  running: true,
  incidents: true,
  active: true,
};

export const mockUseDataManager = {
  applyBatchOperation: jest.fn(),
};

export const mockData = {
  // in this case all instances are selected and no filter is set
  noFilterSelectAll: {
    expectedQuery: {
      ...baseQuery,
      ids: [],
      excludeIds: [],
    },
  },
  // in this case all instances are selected and an id filter is set
  setFilterSelectAll: {
    expectedQuery: {
      ...baseQuery,
      ids: ['1'],
      excludeIds: [],
    },
  },
  // in this case one instance is selected and an id filter is set
  setFilterSelectOne: {
    expectedQuery: {
      ...baseQuery,
      ids: ['1'],
      excludeIds: [],
    },
  },
  // in this case one instance is excluded and an id filter is set
  setFilterExcludeOne: {
    expectedQuery: {
      ...baseQuery,
      ids: ['1', '2'],
      excludeIds: ['1'],
    },
  },
  // in this case all instances are selected and a workflow filter is set
  setWorkflowFilterSelectOne: {
    expectedQuery: {
      ...baseQuery,
      ids: ['1'],
      excludeIds: [],
      workflowIds: ['demoProcess1'],
    },
  },
};

export const mockUseInstancesPollContext = {
  addAllVisibleIds: jest.fn(),
  addIds: jest.fn(),
};
