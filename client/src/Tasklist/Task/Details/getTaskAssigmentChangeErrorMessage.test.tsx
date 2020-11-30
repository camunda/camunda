/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {getTaskAssigmentChangeErrorMessage} from './getTaskAssigmentChangeErrorMessage';

describe('getTaskAssigmentChangeErrorMessage', () => {
  it('should return an error message for unassigned task', () => {
    expect(
      getTaskAssigmentChangeErrorMessage('Task is not assigned'),
    ).toBeUndefined();
  });

  it('should return an error message for an inactive task', () => {
    expect(
      getTaskAssigmentChangeErrorMessage('Task is not active'),
    ).toBeUndefined();
  });

  it('should return an error message for a task which is already assigned', () => {
    expect(
      getTaskAssigmentChangeErrorMessage('Task is already assigned'),
    ).toBeUndefined();
  });

  it('should return a generic error message', () => {
    expect(getTaskAssigmentChangeErrorMessage('generic error')).toBe(
      'Service is not reachable',
    );
  });
});
