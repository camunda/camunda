/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryClient, QueryFunction, QueryKey} from '@tanstack/react-query';

type WaitUntilReadyOptions<TData> = {
	isReady?: (data: TData) => boolean;
	retry?: number;
	retryDelay?: number;
};

const DEFAULT_RETRY = 5;
const DEFAULT_RETRY_DELAY = 1000;

async function waitUntilReady<TData, TQueryKey extends QueryKey = QueryKey>(
	queryClient: QueryClient,
	query: {queryKey: TQueryKey; queryFn?: QueryFunction<TData, TQueryKey, never> | undefined},
	{isReady = () => true, retry = DEFAULT_RETRY, retryDelay = DEFAULT_RETRY_DELAY}: WaitUntilReadyOptions<TData> = {},
): Promise<TData | undefined> {
	const {queryFn} = query;
	if (queryFn === undefined) {
		return undefined;
	}

	try {
		return await queryClient.query({
			queryKey: query.queryKey,
			queryFn: async (context) => {
				const data = await queryFn(context);
				if (!isReady(data)) {
					throw new Error('Not ready yet');
				}
				return data;
			},
			retry,
			retryDelay,
		});
	} catch {
		return undefined;
	}
}

export {waitUntilReady};
