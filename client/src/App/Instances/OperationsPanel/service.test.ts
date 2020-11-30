/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {isOperationRunning, hasRunningOperations} from './service';
import {mockOperationFinished, mockOperationRunning} from './index.setup';

describe('isOperationRunning', () => {
  it('should be true when operation is running', () => {
    expect(isOperationRunning(mockOperationRunning)).toBe(true);
  });

  it('should be false when operation is finished', () => {
    expect(isOperationRunning(mockOperationFinished)).toBe(false);
  });
});

describe('hasRunningOperations', () => {
  it('should be true if it contains running operation', () => {
    expect(
      hasRunningOperations([mockOperationFinished, mockOperationRunning])
    ).toBe(true);
  });

  it('should be false if it only contains finished operations', () => {
    expect(
      hasRunningOperations([mockOperationFinished, mockOperationFinished])
    ).toBe(false);
  });

  it('should be false if it does not contain anything', () => {
    expect(hasRunningOperations([])).toBe(false);
  });
});
