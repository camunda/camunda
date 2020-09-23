/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createInstance, createOperation} from 'modules/testUtils';
import {EXPAND_STATE} from 'modules/constants';

// mock props
const INSTANCE = createInstance({
  id: '1',
  operations: [createOperation({state: 'FAILED'})],
  hasActiveOperation: false,
});
export const ACTIVE_INSTANCE = createInstance({
  id: '2',
  operations: [createOperation({state: 'SENT'})],
  hasActiveOperation: true,
});

export const mockProps = {
  expandState: EXPAND_STATE.DEFAULT,
  instances: [],
};

export const mockPropsWithEmptyInstances = {
  ...mockProps,
  isInitialLoadComplete: true,
  isLoading: false,
};

export const mockPropsBeforeDataLoaded = {
  ...mockProps,
  isInitialLoadComplete: false,
  isLoading: true,
};

export const mockPropsWithInstances = {
  ...mockProps,
  instances: [INSTANCE, ACTIVE_INSTANCE],
  isInitialLoadComplete: true,
  isLoading: false,
};

export const mockPropsWithNoOperation = {
  ...mockProps,
  instances: [INSTANCE],
  isInitialLoadComplete: true,
  isLoading: false,
};

export const mockPropsWithPoll = {
  ...mockPropsWithNoOperation,
  polling: {
    active: new Set([]),
    complete: new Set([]),
    addIds: jest.fn(),
    removeIds: jest.fn(),
  },
};
