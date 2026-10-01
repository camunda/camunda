/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient} from '@tanstack/react-query';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {waitUntilGone, waitUntilReady} from './waitUntilReady';

function notFoundError() {
	return {variant: 'failed-response' as const, response: new Response(null, {status: 404}), networkError: null};
}

function serverError() {
	return {variant: 'failed-response' as const, response: new Response(null, {status: 500}), networkError: null};
}

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

describe('waitUntilGone', () => {
	afterEach(() => {
		vi.useRealTimers();
	});

	it('should resolve true immediately when the read already 404s', async () => {
		// given
		const queryClient = new QueryClient();

		// when
		const gone = await waitUntilGone(queryClient, {
			queryKey: ['waitUntilGone-test-a'],
			queryFn: () => Promise.reject(notFoundError()),
		});

		// then
		expect(gone).toBe(true);
	});

	it('should retry while the read keeps succeeding, until it 404s', async () => {
		// given
		vi.useFakeTimers();
		const queryClient = new QueryClient();
		let attempt = 0;
		const queryFn = vi.fn(() => {
			attempt += 1;
			return attempt < 3 ? Promise.resolve({attempt}) : Promise.reject(notFoundError());
		});

		// when
		const promise = waitUntilGone(queryClient, {queryKey: ['waitUntilGone-test-b'], queryFn}, {retryDelay: 100});
		await vi.advanceTimersByTimeAsync(1000);
		const gone = await promise;

		// then
		expect(gone).toBe(true);
		expect(attempt).toBe(3);
	});

	it('should retry on an unrelated error rather than treating it as confirmation', async () => {
		// given
		vi.useFakeTimers();
		const queryClient = new QueryClient();
		let attempt = 0;
		const queryFn = vi.fn(() => {
			attempt += 1;
			return attempt < 2 ? Promise.reject(serverError()) : Promise.reject(notFoundError());
		});

		// when
		const promise = waitUntilGone(queryClient, {queryKey: ['waitUntilGone-test-c'], queryFn}, {retryDelay: 10});
		await vi.advanceTimersByTimeAsync(1000);
		const gone = await promise;

		// then
		expect(gone).toBe(true);
		expect(attempt).toBe(2);
	});

	it('should give up quietly once the retry budget is exhausted while the read still succeeds', async () => {
		// given
		vi.useFakeTimers();
		const queryClient = new QueryClient();

		// when
		const promise = waitUntilGone(
			queryClient,
			{queryKey: ['waitUntilGone-test-d'], queryFn: () => Promise.resolve({stillHere: true})},
			{retry: 2, retryDelay: 10},
		);
		await vi.advanceTimersByTimeAsync(1000);
		const gone = await promise;

		// then
		expect(gone).toBe(false);
	});
});
