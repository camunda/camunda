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

/**
 * Fetches all batch-operation items for the visible instances, so the list can report every item
 * produced by operations such as resolving multiple incidents in one process instance.
 */
function useOperationItemsForInstances(batchOperationKey: string | undefined, processInstanceKeys: string[]) {
	const filter = {
		batchOperationKey: batchOperationKey === undefined ? undefined : {$eq: batchOperationKey},
		processInstanceKey: {$in: processInstanceKeys},
	} satisfies QueryBatchOperationItemsRequestBody['filter'];

	return useQuery({
		queryKey: ['batchOperationItems', filter] as const,
		queryFn: async (): Promise<BatchOperationItem[]> => {
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
		},
		enabled: batchOperationKey !== undefined && processInstanceKeys.length > 0,
		refetchInterval: (query) => {
			const items = query.state.data;
			if (items === undefined) {
				return false;
			}

			const instancesWithItems = new Set(items.map(({processInstanceKey}) => processInstanceKey));
			const hasMissingItems = processInstanceKeys.some((key) => !instancesWithItems.has(key));
			return items.some(({state}) => state === 'ACTIVE') || hasMissingItems ? ACTIVE_ITEMS_REFETCH_INTERVAL_MS : false;
		},
	});
}

export {useOperationItemsForInstances};
