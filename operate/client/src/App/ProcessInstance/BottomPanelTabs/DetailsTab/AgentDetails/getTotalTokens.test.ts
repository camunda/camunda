/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getTotalTokens} from './getTotalTokens';

describe('getTotalTokens', () => {
  it('should be 0 when all buckets are missing', () => {
    expect(getTotalTokens({})).toBe(0);
    expect(
      getTotalTokens({
        inputTokens: null,
        outputTokens: null,
        cacheReadTokens: null,
        cacheCreationTokens: null,
      }),
    ).toBe(0);
  });

  it('should sum input, cache read, cache write and output', () => {
    expect(
      getTotalTokens({
        inputTokens: 20,
        outputTokens: 4262,
        cacheReadTokens: 60189,
        cacheCreationTokens: 32385,
      }),
    ).toBe(96856);
  });

  it('should count a cache-only bucket and treat null buckets as 0', () => {
    expect(
      getTotalTokens({
        inputTokens: null,
        outputTokens: null,
        cacheReadTokens: 7,
        cacheCreationTokens: null,
      }),
    ).toBe(7);
  });
});
