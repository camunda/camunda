/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient} from '@tanstack/react-query';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {waitUntilReady} from './waitUntilReady';

describe('waitUntilReady', () => {
	afterEach(() => {
		vi.useRealTimers();
	});

	it('should resolve immediately when the read succeeds and no readiness check is given', async () => {
		// given
		const queryClient = new QueryClient();

		// when
		const data = await waitUntilReady(queryClient, {
			queryKey: ['waitUntilReady-test-a'],
			queryFn: () => Promise.resolve({value: 1}),
		});

		// then
		expect(data).toEqual({value: 1});
	});

	it('should retry the read until isReady reports true', async () => {
		// given
		vi.useFakeTimers();
		const queryClient = new QueryClient();
		let attempt = 0;
		const queryFn = vi.fn(() => {
			attempt += 1;
			return Promise.resolve({attempt});
		});

		// when
		const promise = waitUntilReady(
			queryClient,
			{queryKey: ['waitUntilReady-test-b'], queryFn},
			{isReady: (data) => data.attempt >= 3, retry: 5, retryDelay: 100},
		);
		await vi.advanceTimersByTimeAsync(1000);
		const data = await promise;

		// then
		expect(data).toEqual({attempt: 3});
	});

	it('should give up quietly once the retry budget is exhausted', async () => {
		// given
		vi.useFakeTimers();
		const queryClient = new QueryClient();

		// when
		const promise = waitUntilReady(
			queryClient,
			{queryKey: ['waitUntilReady-test-c'], queryFn: () => Promise.resolve({ready: false})},
			{isReady: () => false, retry: 2, retryDelay: 10},
		);
		await vi.advanceTimersByTimeAsync(1000);
		const data = await promise;

		// then
		expect(data).toBeUndefined();
	});

	it('should give up quietly when the underlying read keeps failing', async () => {
		// given
		vi.useFakeTimers();
		const queryClient = new QueryClient();

		// when
		const promise = waitUntilReady(
			queryClient,
			{
				queryKey: ['waitUntilReady-test-d'],
				queryFn: () => Promise.reject(new Error('not found yet')),
			},
			{retry: 2, retryDelay: 10},
		);
		await vi.advanceTimersByTimeAsync(1000);
		const data = await promise;

		// then
		expect(data).toBeUndefined();
	});
});
