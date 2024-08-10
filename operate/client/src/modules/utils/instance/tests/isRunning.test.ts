/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {isRunning} from '..';
import {
  mockActiveInstance,
  mockCanceledInstance,
  mockCompletedInstance,
  mockIncidentInstance,
} from './mocks';

describe('isRunning', () => {
  it('should return true if an instance is running', () => {
    expect(isRunning(mockIncidentInstance)).toBe(true);
    expect(isRunning(mockActiveInstance)).toBe(true);

    expect(isRunning(mockCompletedInstance)).toBe(false);
    expect(isRunning(mockCanceledInstance)).toBe(false);
  });
});
