/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
