/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {isBatchOperationRunning} from './service';

describe('isBatchOperationRunning', () => {
  it('should validate true', () => {
    const isRunning = isBatchOperationRunning({
      id: '123',
      type: 'RESOLVE_INCIDENT',
      endDate: null
    });

    expect(isRunning).toBe(true);
  });

  it('should validate false', () => {
    const isRunning = isBatchOperationRunning({
      id: '123',
      type: 'RESOLVE_INCIDENT',
      endDate: '2020-02-04T10:08:32.059+0100'
    });

    expect(isRunning).toBe(false);
  });
});
