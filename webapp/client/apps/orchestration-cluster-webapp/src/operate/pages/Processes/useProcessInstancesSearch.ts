/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useInfiniteQuery} from '@tanstack/react-query';
import type {QueryProcessInstancesResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {request} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {endpoints} from '#/shared/http/endpoints';
import {mapProcessInstancesFilter, mapProcessInstancesSort, type ProcessesSearch} from './processesFilter';

const PAGE_LIMIT = 50;
const RUNNING_INSTANCES_REFETCH_INTERVAL_MS = 5000;

/**
 * Fetches process instances matching the current filter/sort with infinite scroll. Disabled (no
 * request sent) when nothing narrows the result set, mirroring legacy's
 * `useProcessInstancesPaginated`. Polls only while a running-instance state is selected — a list
 * of finished instances cannot change underneath the user.
 */
function useProcessInstancesSearch(search: ProcessesSearch) {
	const filter = mapProcessInstancesFilter(search);
	const sort = mapProcessInstancesSort(search.sort);
	const isShowingRunningInstances = search.active || search.incidents;

	const query = useInfiniteQuery({
		queryKey: ['processInstances', filter, sort] as const,
		enabled: filter !== undefined,
		staleTime: 5000,
		refetchInterval: isShowingRunningInstances ? RUNNING_INSTANCES_REFETCH_INTERVAL_MS : false,
		queryFn: async ({pageParam}): Promise<QueryProcessInstancesResponseBody> => {
			const {response, error} = await request(
				endpoints.queryProcessInstances({filter, sort, page: {from: pageParam, limit: PAGE_LIMIT}}),
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

	return {
		status,
		isFetching,
		isFetchingPreviousPage,
		hasPreviousPage,
		fetchPreviousPage,
		isFetchingNextPage,
		hasNextPage,
		fetchNextPage,
		processInstances: data?.pages.flatMap((page) => page.items) ?? [],
		totalCount: data?.pages.at(0)?.page.totalItems ?? 0,
		hasMoreTotalItems: data?.pages.at(0)?.page.hasMoreTotalItems ?? false,
		filter,
	};
}

export {useProcessInstancesSearch};
