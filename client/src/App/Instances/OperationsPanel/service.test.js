/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {isBatchOperationRunning, hasRunningBatchOperations} from './service';
import {
  mockOperationFinished,
  mockOperationRunning
} from './OperationsPanel.setup';

describe('isBatchOperationRunning', () => {
  it('should be true when operation is running', () => {
    const isRunning = isBatchOperationRunning(mockOperationRunning);

    expect(isRunning).toBe(true);
  });

  it('should be false when operation is finished', () => {
    const isRunning = isBatchOperationRunning(mockOperationFinished);

    expect(isRunning).toBe(false);
  });

  it('should be false when no param', () => {
    const isRunning = isBatchOperationRunning();

    expect(isRunning).toBe(false);
  });
});

describe('hasRunningBatchOperations', () => {
  it('should be true if it contains running operation', () => {
    const hasRunning = hasRunningBatchOperations([
      mockOperationFinished,
      mockOperationRunning
    ]);

    expect(hasRunning).toBe(true);
  });

  it('should be false if it only contains finished operations', () => {
    const hasRunning = hasRunningBatchOperations([
      mockOperationFinished,
      mockOperationFinished
    ]);

    expect(hasRunning).toBe(false);
  });

  it('should be false if it does not contain anything', () => {
    const hasRunning = hasRunningBatchOperations([]);

    expect(hasRunning).toBe(false);
  });
});
