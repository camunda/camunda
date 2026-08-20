/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useInfiniteQuery} from '@tanstack/react-query';
import type {QueryDecisionInstancesResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {request} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {endpoints} from '#/shared/http/endpoints';
import {mapDecisionInstancesFilter, mapDecisionInstancesSort, type DecisionsSearch} from './decisionsFilter';

const PAGE_LIMIT = 50;

/**
 * Fetches decision instances matching the current filter/sort with infinite scroll. Disabled
 * (no request sent) when no instance-state checkbox is selected, mirroring legacy's
 * `useDecisionInstancesSearchPaginated`.
 */
function useDecisionInstancesSearch(search: DecisionsSearch) {
	const filter = mapDecisionInstancesFilter(search);
	const sort = mapDecisionInstancesSort(search.sort);

	const query = useInfiniteQuery({
		queryKey: ['decisionInstances', filter, sort] as const,
		enabled: filter !== undefined,
		staleTime: 5000,
		queryFn: async ({pageParam}): Promise<QueryDecisionInstancesResponseBody> => {
			const {response, error} = await request(
				endpoints.queryDecisionInstances({filter, sort, page: {from: pageParam, limit: PAGE_LIMIT}}),
			);
			if (error !== null) {
				throw mapQueryError(error);
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
		placeholderData: (previousData) => previousData,
		maxPages: 2,
	});

	const {
		data,
		status,
		isFetching,
		isFetchingPreviousPage,
		hasPreviousPage,
		fetchPreviousPage,
		isFetchingNextPage,
		hasNextPage,
		fetchNextPage,
	} = query;

	const decisionInstances = data?.pages.flatMap((page) => page.items) ?? [];
	const totalCount = data?.pages.at(0)?.page.totalItems ?? 0;
	const hasMoreTotalItems = data?.pages.at(0)?.page.hasMoreTotalItems ?? false;

	return {
		status,
		isFetching,
		isFetchingPreviousPage,
		hasPreviousPage,
		fetchPreviousPage,
		isFetchingNextPage,
		hasNextPage,
		fetchNextPage,
		decisionInstances,
		totalCount,
		hasMoreTotalItems,
		filter,
	};
}

export {useDecisionInstancesSearch};
