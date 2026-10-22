/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryClient, QueryFunction, QueryKey} from '@tanstack/react-query';
import {requestErrorSchema} from './request';

type WaitUntilReadyOptions<TData> = {
	isReady?: (data: TData) => boolean;
	retry?: number;
	retryDelay?: number;
};

type WaitUntilGoneOptions = {
	retry?: number;
	retryDelay?: number;
};

const DEFAULT_RETRY = 5;
const DEFAULT_RETRY_DELAY = 1000;

function isNotFoundError(error: unknown): boolean {
	const requestError = requestErrorSchema.safeParse(error);
	return (
		requestError.success && requestError.data.variant === 'failed-response' && requestError.data.response.status === 404
	);
}

// Retries run under a private key: a retrying query re-reads its options on every attempt, so an
// observer of the real key re-rendering mid-wait would swap in its own `queryFn` and the readiness
// check would silently stop being applied.
async function waitUntilReady<TData, TQueryKey extends QueryKey = QueryKey>(
	queryClient: QueryClient,
	query: {queryKey: TQueryKey; queryFn?: QueryFunction<TData, TQueryKey, never> | undefined},
	{isReady = () => true, retry = DEFAULT_RETRY, retryDelay = DEFAULT_RETRY_DELAY}: WaitUntilReadyOptions<TData> = {},
): Promise<TData | undefined> {
	const {queryKey, queryFn} = query;
	if (queryFn === undefined) {
		return undefined;
	}

	const cachedBeforeWait = queryClient.getQueryData(queryKey);

	try {
		const data = await queryClient.query({
			queryKey: ['waitUntilReady', ...queryKey],
			queryFn: async (context) => {
				const data = await queryFn({...context, queryKey});
				if (!isReady(data)) {
					throw new Error('Not ready yet');
				}
				return data;
			},
			retry,
			retryDelay,
			gcTime: 0,
		});
		// Skip the write if the real key was updated meanwhile: that entry is newer than this snapshot.
		if (queryClient.getQueryData(queryKey) === cachedBeforeWait) {
			queryClient.setQueryData<TData>(queryKey as QueryKey, data as TData);
		}
		return data;
	} catch {
		return undefined;
	}
}

async function waitUntilGone(
	queryClient: QueryClient,
	query: {queryKey: QueryKey; queryFn?: (() => Promise<unknown>) | undefined},
	{retry = DEFAULT_RETRY, retryDelay = DEFAULT_RETRY_DELAY}: WaitUntilGoneOptions = {},
): Promise<boolean> {
	const {queryFn} = query;
	if (queryFn === undefined) {
		return false;
	}

	try {
		await queryClient.query({
			queryKey: query.queryKey,
			queryFn: async () => {
				try {
					await queryFn();
				} catch (error) {
					if (isNotFoundError(error)) {
						return true;
					}
					throw error;
				}
				throw new Error('Still visible');
			},
			retry,
			retryDelay,
		});
		return true;
	} catch {
		return false;
	}
}

export {waitUntilReady, waitUntilGone};
