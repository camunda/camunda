/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export const mockUseDataManager = {
  applyBatchOperation: jest.fn()
};

export const mockUseInstanceSelectionContext = {
  ids: [],
  excludeIds: [],
  reset: jest.fn()
};

export const mockUseFilterContext = {
  query: {
    running: true,
    incidents: true,
    active: true
  }
};

export const expectedQuery = {
  ids: mockUseInstanceSelectionContext.ids,
  excludeIds: mockUseInstanceSelectionContext.excludeIds,
  ...mockUseFilterContext.query
};
