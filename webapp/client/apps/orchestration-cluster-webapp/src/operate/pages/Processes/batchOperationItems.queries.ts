/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {queryOptions} from '@tanstack/react-query';
import type {
	BatchOperationItem,
	QueryBatchOperationItemsRequestBody,
	QueryBatchOperationItemsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {request} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {endpoints} from '#/shared/http/endpoints';

const PAGE_LIMIT = 10_000;

async function fetchBatchOperationItems(
	body: QueryBatchOperationItemsRequestBody,
	signal: AbortSignal,
): Promise<QueryBatchOperationItemsResponseBody> {
	const {response, error} = await request(new Request(endpoints.queryBatchOperationItems(body), {signal}));
	if (error !== null) {
		throw mapQueryError(error);
	}
	signal.throwIfAborted();
	const data: QueryBatchOperationItemsResponseBody = await response.json();
	signal.throwIfAborted();
	return data;
}

function batchOperationItemsQueryOptions(body: QueryBatchOperationItemsRequestBody) {
	return queryOptions({
		queryKey: ['batchOperationItems', body] as const,
		queryFn: ({signal}) => fetchBatchOperationItems(body, signal),
	});
}

function batchOperationItemsForInstancesQueryOptions(body: QueryBatchOperationItemsRequestBody) {
	return queryOptions({
		queryKey: ['batchOperationItems', 'forInstances', body] as const,
		queryFn: async ({signal}): Promise<{items: BatchOperationItem[]}> => {
			const items: BatchOperationItem[] = [];
			let after: string | undefined;
			for (;;) {
				signal.throwIfAborted();
				const page = await fetchBatchOperationItems(
					{
						...body,
						sort: [{field: 'itemKey', order: 'asc'}],
						page: {after, limit: PAGE_LIMIT},
					},
					signal,
				);
				items.push(...page.items);
				const next = page.page.endCursor;
				if (next === null) {
					if (items.length < page.page.totalItems) {
						throw new Error('Batch operation items search ended before all results were returned');
					}
					return {items};
				}
				const hasMore = page.page.hasMoreTotalItems || items.length < page.page.totalItems;
				if (!hasMore) {
					return {items};
				}
				if (next === after) {
					throw new Error('Batch operation items search did not provide a new page cursor');
				}
				after = next;
			}
		},
	});
}

export {batchOperationItemsQueryOptions, batchOperationItemsForInstancesQueryOptions};
