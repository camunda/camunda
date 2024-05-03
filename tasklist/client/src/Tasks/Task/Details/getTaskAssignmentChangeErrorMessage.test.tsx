/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getTaskAssignmentChangeErrorMessage} from './getTaskAssignmentChangeErrorMessage';

describe('getTaskAssignmentChangeErrorMessage', () => {
  it('should return an error message for unassigned task', () => {
    expect(
      getTaskAssignmentChangeErrorMessage('Task is not assigned'),
    ).toBeUndefined();
  });

  it('should return an error message for an inactive task', () => {
    expect(
      getTaskAssignmentChangeErrorMessage('Task is not active'),
    ).toBeUndefined();
  });

  it('should return an error message for a task which is already assigned', () => {
    expect(
      getTaskAssignmentChangeErrorMessage('Task is already assigned'),
    ).toBe('Task has been assigned to another user');
  });

  it('should return a generic error message', () => {
    expect(getTaskAssignmentChangeErrorMessage('generic error')).toBe(
      'Service is not reachable',
    );
  });
});
