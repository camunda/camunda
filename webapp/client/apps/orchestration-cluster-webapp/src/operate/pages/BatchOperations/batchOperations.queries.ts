/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {queryOptions} from '@tanstack/react-query';
import type {
	QueryBatchOperationsRequestBody,
	QueryBatchOperationsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {ForbiddenError} from '#/shared/errors';
import {request} from '#/shared/http/request';
import {endpoints} from '#/shared/http/endpoints';

type SortField = NonNullable<QueryBatchOperationsRequestBody['sort']>[number]['field'];

type BatchOperationsSearch = {
	page: number;
	pageSize: number;
	sort?: string;
};

const DEFAULT_SORT = 'endDate+desc';

function getRequestBody({page, pageSize, sort}: BatchOperationsSearch): QueryBatchOperationsRequestBody {
	const [sortField, sortOrder] = (sort ?? DEFAULT_SORT).split('+');

	return {
		sort: [{field: sortField as SortField, order: (sortOrder ?? 'desc') as 'asc' | 'desc'}],
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
				if (error.variant === 'failed-response' && error.response.status === 403) {
					throw new ForbiddenError();
				}
				throw error;
			}
			return response.json();
		},
	});
}

export {batchOperationsOptions};
