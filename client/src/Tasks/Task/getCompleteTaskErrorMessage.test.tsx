/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getCompleteTaskErrorMessage} from './getCompleteTaskErrorMessage';

describe('getCompleteTaskErrorMessage', () => {
  it('should return an error message for unassigned task', () => {
    expect(getCompleteTaskErrorMessage('Task is not assigned')).toBe(
      'Task is not claimed',
    );
  });

  it('should return an error message for a task assigned to other user', () => {
    expect(getCompleteTaskErrorMessage('Task is not assigned to demo')).toBe(
      'Task claimed by another user',
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
