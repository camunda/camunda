/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
    ).toBeUndefined();
  });

  it('should return a generic error message', () => {
    expect(getTaskAssignmentChangeErrorMessage('generic error')).toBe(
      'Service is not reachable',
    );
  });
});
