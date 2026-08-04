/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {infiniteQueryOptions, useInfiniteQuery} from '@tanstack/react-query';
import type {QueryAuditLogsRequestBody, QueryAuditLogsResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {request} from '#/shared/http/request';
import {endpoints} from '#/shared/http/endpoints';

const PAGE_LIMIT = 50;
const MAX_PAGES = 2;

function auditLogsInfiniteQuery(
	filter: NonNullable<QueryAuditLogsRequestBody['filter']>,
	sort: NonNullable<QueryAuditLogsRequestBody['sort']>,
) {
	return infiniteQueryOptions({
		queryKey: ['operationsLogAuditLogs', filter, sort] as const,
		queryFn: async ({pageParam}): Promise<QueryAuditLogsResponseBody> => {
			const {response, error} = await request(
				endpoints.queryAuditLogs({filter, sort, page: {from: pageParam, limit: PAGE_LIMIT}}),
			);
			if (error !== null) {
				throw error;
			}
			return response.json();
		},
		initialPageParam: 0,
		getNextPageParam: (lastPage, _, lastPageParam) => {
			const nextPage = lastPageParam + PAGE_LIMIT;
			return nextPage >= lastPage.page.totalItems ? undefined : nextPage;
		},
		getPreviousPageParam: (_, __, firstPageParam) => {
			const previousPage = firstPageParam - PAGE_LIMIT;
			return previousPage < 0 ? undefined : previousPage;
		},
		maxPages: MAX_PAGES,
		staleTime: 5000,
	});
}

function useAuditLogs(
	filter: NonNullable<QueryAuditLogsRequestBody['filter']>,
	sort: NonNullable<QueryAuditLogsRequestBody['sort']>,
) {
	return useInfiniteQuery({
		...auditLogsInfiniteQuery(filter, sort),
		refetchInterval: 5000,
		placeholderData: (previousData) => previousData,
	});
}

export {useAuditLogs};
