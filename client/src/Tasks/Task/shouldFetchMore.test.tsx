/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shouldFetchMore} from './shouldFetchMore';

describe('shouldFetchMore', () => {
  it('should fetch more', () => {
    expect(shouldFetchMore('task is not assigned')).toBe(true);
    expect(shouldFetchMore('Task is not assigned')).toBe(true);
    expect(shouldFetchMore('task is not assigned to demo')).toBe(true);
    expect(shouldFetchMore('Task is not assigned to demo')).toBe(true);
    expect(shouldFetchMore('task is not active')).toBe(true);
    expect(shouldFetchMore('Task is not active')).toBe(true);
  });

  it('should not fetch more', () => {
    expect(shouldFetchMore('')).toBe(false);
    expect(shouldFetchMore('foo')).toBe(false);
  });
});
