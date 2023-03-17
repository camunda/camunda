/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
