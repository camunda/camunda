/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  isOperationRunning,
  hasRunningOperations,
  hasOperations,
} from './service';
import {mockOperationFinished, mockOperationRunning} from './index.setup';

describe('isOperationRunning', () => {
  it('should be true when operation is running', () => {
    const isRunning = isOperationRunning(mockOperationRunning);

    expect(isRunning).toBe(true);
  });

  it('should be false when operation is finished', () => {
    const isRunning = isOperationRunning(mockOperationFinished);

    expect(isRunning).toBe(false);
  });

  it('should be false when no param', () => {
    const isRunning = isOperationRunning();

    expect(isRunning).toBe(false);
  });
});

describe('hasRunningOperations', () => {
  it('should be true if it contains running operation', () => {
    const hasRunning = hasRunningOperations([
      mockOperationFinished,
      mockOperationRunning,
    ]);

    expect(hasRunning).toBe(true);
  });

  it('should be false if it only contains finished operations', () => {
    const hasRunning = hasRunningOperations([
      mockOperationFinished,
      mockOperationFinished,
    ]);

    expect(hasRunning).toBe(false);
  });

  it('should be false if it does not contain anything', () => {
    const hasRunning = hasRunningOperations([]);

    expect(hasRunning).toBe(false);
  });
});

describe('Operations', () => {
  it('should be true if has operations', () => {
    const result = hasOperations([mockOperationRunning]);

    expect(result).toBe(true);
  });

  it('should be false if it is null', () => {
    const result = hasOperations(null);

    expect(result).toBe(false);
  });

  it('should be false if it does not contain anything', () => {
    const result = hasOperations([]);

    expect(result).toBe(false);
  });
});
