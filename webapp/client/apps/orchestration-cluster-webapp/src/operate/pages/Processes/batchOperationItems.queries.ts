/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {queryOptions, useQuery} from '@tanstack/react-query';
import type {
	QueryBatchOperationItemsRequestBody,
	QueryBatchOperationItemsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {request} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {endpoints} from '#/shared/http/endpoints';

function batchOperationItemsOptions(body: QueryBatchOperationItemsRequestBody) {
	return queryOptions({
		queryKey: ['batchOperationItems', body] as const,
		queryFn: async (): Promise<QueryBatchOperationItemsResponseBody> => {
			const {response, error} = await request(endpoints.queryBatchOperationItems(body));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json();
		},
	});
}

/**
 * Fetches the batch-operation item for each visible instance, so the list can show how that
 * instance fared in the batch operation it is filtered by. One item per instance, so the page
 * limit is the number of rows on screen.
 */
function useOperationItemsForInstances(batchOperationKey: string | undefined, processInstanceKeys: string[]) {
	return useQuery({
		...batchOperationItemsOptions({
			filter: {
				batchOperationKey: {$eq: batchOperationKey!},
				processInstanceKey: {$in: processInstanceKeys},
			},
			page: {limit: processInstanceKeys.length},
		}),
		enabled: batchOperationKey !== undefined && processInstanceKeys.length > 0,
	});
}

export {useOperationItemsForInstances};
