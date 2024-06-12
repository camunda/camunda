/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getCompleteTaskErrorMessage} from './getCompleteTaskErrorMessage';

describe('getCompleteTaskErrorMessage', () => {
  it('should return an error message for unassigned task', () => {
    expect(getCompleteTaskErrorMessage('Task is not assigned')).toBe(
      'Task is not assigned',
    );
  });

  it('should return an error message for a task assigned to other user', () => {
    expect(getCompleteTaskErrorMessage('Task is not assigned to demo')).toBe(
      'Task assigned to another user',
    );
  });

  it('should return an error message for an inactive task', () => {
    expect(getCompleteTaskErrorMessage('Task is not active')).toBeUndefined();
  });

  it('should return a generic error message', () => {
    expect(getCompleteTaskErrorMessage('generic error')).toBe(
      'Service is not reachable',
    );
  });
});
