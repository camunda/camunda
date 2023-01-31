/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shouldDisplayNotification} from './shouldDisplayNotification';

describe('shouldDisplayNotification', () => {
  it('should display notification', () => {
    expect(shouldDisplayNotification('task is already assigned')).toBe(true);
    expect(shouldDisplayNotification('Task is already assigned')).toBe(true);
    expect(shouldDisplayNotification('task is not active')).toBe(true);
    expect(shouldDisplayNotification('Task is not active')).toBe(true);
    expect(shouldDisplayNotification('foo')).toBe(true);
    expect(shouldDisplayNotification('')).toBe(true);
  });

  it('should not display notification', () => {
    expect(shouldDisplayNotification('task is not assigned')).toBe(false);
    expect(shouldDisplayNotification('Task is not assigned')).toBe(false);
  });
});
