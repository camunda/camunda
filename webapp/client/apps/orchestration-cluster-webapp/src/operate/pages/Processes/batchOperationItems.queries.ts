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

function useOperationItemsForInstances(batchOperationKey: string | undefined, processInstanceKeys: string[]) {
	const requestBody = {
		filter: {
			batchOperationKey: batchOperationKey === undefined ? undefined : {$eq: batchOperationKey},
			processInstanceKey: {$in: processInstanceKeys},
		},
		page: {limit: processInstanceKeys.length},
	} satisfies QueryBatchOperationItemsRequestBody;

	return useQuery({
		queryKey: ['batchOperationItems', requestBody] as const,
		queryFn: async (): Promise<BatchOperationItem[]> => {
			const result = await queryBatchOperationItems(requestBody);
			return result.items;
		},
		enabled: batchOperationKey !== undefined && processInstanceKeys.length > 0,
		refetchInterval: (query) => {
			const items = query.state.data;
			return items?.some(({state}) => state === 'ACTIVE') ? ACTIVE_ITEMS_REFETCH_INTERVAL_MS : false;
		},
	});
}

export {useOperationItemsForInstances};
