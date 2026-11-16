/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import type {QueryProcessInstancesResponseBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {request} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {endpoints} from '#/shared/http/endpoints';
import type {mapProcessInstancesFilter} from './processesFilter';

const EXACT_COUNT_LIMIT = 1_000_000;
const EXACT_COUNT_STALE_TIME_MS = 30_000;

function useExactProcessInstancesCount(filter: ReturnType<typeof mapProcessInstancesFilter>, isRequested: boolean) {
	return useQuery({
		queryKey: ['processInstancesExactCount', filter] as const,
		enabled: isRequested && filter !== undefined,
		staleTime: EXACT_COUNT_STALE_TIME_MS,
		queryFn: async (): Promise<QueryProcessInstancesResponseBody['page']> => {
			const {response, error} = await request(
				endpoints.queryProcessInstances({filter, page: {limit: 0, totalItemsAccuracy: EXACT_COUNT_LIMIT}}),
			);
			if (error !== null) {
				throw mapQueryError(error);
			}
			const body: QueryProcessInstancesResponseBody = await response.json();
			return body.page;
		},
	});
}

export {useExactProcessInstancesCount};
