/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import type {
	BatchOperationItem,
	QueryBatchOperationItemsRequestBody,
	QueryBatchOperationItemsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {request} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {endpoints} from '#/shared/http/endpoints';

const PAGE_LIMIT = 100;
const ACTIVE_ITEMS_REFETCH_INTERVAL_MS = 5000;

async function queryBatchOperationItems(
	body: QueryBatchOperationItemsRequestBody,
): Promise<QueryBatchOperationItemsResponseBody> {
	const {response, error} = await request(endpoints.queryBatchOperationItems(body));
	if (error !== null) {
		throw mapQueryError(error);
	}
	return response.json();
}

async function queryAllBatchOperationItems(
	filter: QueryBatchOperationItemsRequestBody['filter'],
): Promise<BatchOperationItem[]> {
	const items: BatchOperationItem[] = [];
	let after: string | undefined;

	do {
		const result = await queryBatchOperationItems({
			filter,
			page: {limit: PAGE_LIMIT, ...(after === undefined ? {} : {after})},
		});
		items.push(...result.items);

		if (
			result.items.length === 0 ||
			result.page.endCursor === null ||
			(!result.page.hasMoreTotalItems && items.length >= result.page.totalItems)
		) {
			break;
		}
		after = result.page.endCursor;
	} while (after !== undefined);

	return items;
}

/**
 * Fetches all batch-operation items for the currently loaded process-instance rows, so the list
 * can report every item produced by operations such as resolving multiple incidents in one
 * process instance.
 */
function useOperationItemsForInstances(batchOperationKey: string | undefined, processInstanceKeys: string[]) {
	const filter = {
		batchOperationKey: batchOperationKey === undefined ? undefined : {$eq: batchOperationKey},
		processInstanceKey: {$in: processInstanceKeys},
	} satisfies QueryBatchOperationItemsRequestBody['filter'];

	return useQuery({
		queryKey: ['batchOperationItems', filter] as const,
		queryFn: () => queryAllBatchOperationItems(filter),
		enabled: batchOperationKey !== undefined && processInstanceKeys.length > 0,
		refetchInterval: (query) => {
			const items = query.state.data;
			return items?.some(({state}) => state === 'ACTIVE') ? ACTIVE_ITEMS_REFETCH_INTERVAL_MS : false;
		},
	});
}

/**
 * Fetches the operations currently running against the visible instances, so each row can
 * disable the actions already in flight. Polls only while something is running.
 */
function useActiveOperationItemsForInstances(processInstanceKeys: string[]) {
	const filter = {
		processInstanceKey: {$in: processInstanceKeys},
		state: {$eq: 'ACTIVE'},
	} satisfies QueryBatchOperationItemsRequestBody['filter'];

	return useQuery({
		queryKey: ['batchOperationItems', filter] as const,
		queryFn: () => queryAllBatchOperationItems(filter),
		enabled: processInstanceKeys.length > 0,
		refetchInterval: (query) => ((query.state.data?.length ?? 0) > 0 ? ACTIVE_ITEMS_REFETCH_INTERVAL_MS : false),
	});
}

export {useOperationItemsForInstances, useActiveOperationItemsForInstances};
