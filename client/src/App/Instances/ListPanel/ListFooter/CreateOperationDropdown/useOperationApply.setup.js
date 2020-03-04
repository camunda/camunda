/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const baseQuery = {
  running: true,
  incidents: true,
  active: true
};

export const mockUseDataManager = {
  applyBatchOperation: jest.fn()
};

export const mockData = {
  // in this case all instances are selected and no filter is set
  noFilterSelectAll: {
    instanceSelectionContext: {
      ids: [],
      excludeIds: [],
      reset: jest.fn()
    },
    filterContext: {
      query: baseQuery
    },
    expectedQuery: {
      ...baseQuery,
      ids: [],
      excludeIds: []
    }
  },
  // in this case all instances are selected and an id filter is set
  setFilterSelectAll: {
    instanceSelectionContext: {
      ids: [],
      excludeIds: [],
      reset: jest.fn()
    },
    filterContext: {
      query: {
        ...baseQuery,
        ids: ['1']
      }
    },
    expectedQuery: {
      ...baseQuery,
      ids: ['1'],
      excludeIds: []
    }
  },
  // in this case one instance is selected and an id filter is set
  setFilterSelectOne: {
    instanceSelectionContext: {
      ids: ['1'],
      excludeIds: [],
      reset: jest.fn()
    },
    filterContext: {
      query: {
        ...baseQuery,
        ids: ['1', '2']
      }
    },
    expectedQuery: {
      ...baseQuery,
      ids: ['1'],
      excludeIds: []
    }
  },
  // in this case one instance is excluded and an id filter is set
  setFilterExcludeOne: {
    instanceSelectionContext: {
      ids: [],
      excludeIds: ['1'],
      reset: jest.fn()
    },
    filterContext: {
      query: {
        ...baseQuery,
        ids: ['1', '2']
      }
    },
    expectedQuery: {
      ...baseQuery,
      ids: ['1', '2'],
      excludeIds: ['1']
    }
  },
  // in this case all instances are selected and a workflow filter is set
  setWorkflowFilterSelectOne: {
    instanceSelectionContext: {
      ids: ['1'],
      excludeIds: [],
      reset: jest.fn()
    },
    filterContext: {
      query: {
        ...baseQuery,
        workflowIds: ['A'],
        ids: []
      }
    },
    expectedQuery: {
      ...baseQuery,
      ids: ['1'],
      excludeIds: [],
      workflowIds: ['A']
    }
  }
};
