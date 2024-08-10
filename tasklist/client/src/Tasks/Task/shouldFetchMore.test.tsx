/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
