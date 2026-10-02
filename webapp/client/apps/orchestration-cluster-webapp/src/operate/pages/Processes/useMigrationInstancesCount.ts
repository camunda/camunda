/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import type {QueryProcessInstancesResponseBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {endpoints} from '#/shared/http/endpoints';
import {request} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';
import type {MigrationScope} from './getMigrationFilter';

/**
 * Instances can finish while the migration is being mapped, so only a fresh count of the migration
 * filter is shown; a cached or failed one is reported as unknown. The footer enables the query when
 * the summary opens, which refetches the stale count; the details only read it, so opening the
 * confirmation does not refetch.
 */
function useMigrationInstancesCount(filter: MigrationScope['filter'], enabled = true) {
	const {data, status, isFetching} = useQuery({
		queryKey: ['migrationInstancesCount', filter],
		enabled,
		staleTime: 0,
		refetchOnMount: false,
		queryFn: async (): Promise<QueryProcessInstancesResponseBody['page']> => {
			const {response, error} = await request(endpoints.queryProcessInstances({filter, page: {limit: 0}}));
			if (error !== null) {
				throw mapQueryError(error);
			}
			const {page}: QueryProcessInstancesResponseBody = await response.json();
			return page;
		},
	});

	return status === 'success' && !isFetching
		? {status, count: data.totalItems, isCountTruncated: data.hasMoreTotalItems}
		: {status: isFetching ? ('pending' as const) : status};
}

export {useMigrationInstancesCount};
