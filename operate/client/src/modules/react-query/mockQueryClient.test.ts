/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryObserver} from '@tanstack/react-query';
import {getMockQueryClient} from './mockQueryClient';

describe('getMockQueryClient', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('should disable query refetch intervals', async () => {
    vi.useFakeTimers();

    const queryFn = vi.fn().mockResolvedValue('result');
    const queryObserver = new QueryObserver(getMockQueryClient(), {
      queryKey: ['polling-query'],
      queryFn,
      refetchInterval: () => 10,
      refetchIntervalInBackground: true,
    });

    const unsubscribe = queryObserver.subscribe(() => {});
    expect(queryFn).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(50);
    expect(queryFn).toHaveBeenCalledTimes(1);

    unsubscribe();
    vi.useRealTimers();
  });

  it('should allow explicitly enabling query refetch intervals', async () => {
    vi.useFakeTimers();

    const queryFn = vi.fn().mockResolvedValue('result');
    const queryObserver = new QueryObserver(
      getMockQueryClient({forceNoRetchInterval: false}),
      {
        queryKey: ['polling-query'],
        queryFn,
        refetchInterval: () => 10,
        refetchIntervalInBackground: true,
      },
    );

    const unsubscribe = queryObserver.subscribe(() => {});
    expect(queryFn).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(50);
    expect(queryFn).toHaveBeenCalledTimes(6);

    unsubscribe();
    vi.useRealTimers();
  });
});
