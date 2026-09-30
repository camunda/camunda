/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {queryOptions} from '@tanstack/react-query';
import {z} from 'zod';
import {
	batchOperationSortFieldEnum,
	type QueryBatchOperationsRequestBody,
	type QueryBatchOperationsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {request} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {endpoints} from '#/shared/http/endpoints';

type BatchOperationsSearch = {
	page: number;
	pageSize: number;
	sort: string;
};

const DEFAULT_SORT = 'endDate+DESC';
const SORT_SCHEMA = z.object({field: batchOperationSortFieldEnum, order: z.enum(['ASC', 'DESC'])});

function parseBatchOperationsSort(value: string | undefined) {
	const [field, order, ...remaining] = (value ?? DEFAULT_SORT).split('+');
	const result = SORT_SCHEMA.safeParse({field, order});
	return remaining.length === 0 && order !== undefined && result.success
		? result.data
		: SORT_SCHEMA.parse({field: 'endDate', order: 'DESC'});
}

const batchOperationsSearchSchema = z.object({
	page: z.number().int().positive().default(1),
	pageSize: z.number().int().positive().default(20),
	sort: z
		.unknown()
		.optional()
		.transform((value) => {
			const {field, order} = parseBatchOperationsSort(typeof value === 'string' ? value : undefined);
			return `${field}+${order}`;
		}),
});

function getRequestBody({page, pageSize, sort}: BatchOperationsSearch): QueryBatchOperationsRequestBody {
	return {
		sort: [parseBatchOperationsSort(sort)],
		page: {from: (page - 1) * pageSize, limit: pageSize},
	};
}

function batchOperationsOptions(search: BatchOperationsSearch) {
	const body = getRequestBody(search);

	return queryOptions({
		queryKey: ['batchOperations', body] as const,
		queryFn: async (): Promise<QueryBatchOperationsResponseBody> => {
			const {response, error} = await request(endpoints.queryBatchOperations(body));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json();
		},
	});
}

export {batchOperationsOptions, batchOperationsSearchSchema};
