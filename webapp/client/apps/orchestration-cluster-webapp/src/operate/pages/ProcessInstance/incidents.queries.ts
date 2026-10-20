/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {infiniteQueryOptions, queryOptions, skipToken, type InfiniteData} from '@tanstack/react-query';
import type {
	IncidentErrorType,
	QueryDecisionInstancesRequestBody,
	QueryDecisionInstancesResponseBody,
	QueryProcessInstanceIncidentsRequestBody,
	QueryProcessInstanceIncidentsResponseBody,
	QueryProcessInstancesRequestBody,
	QueryProcessInstancesResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {endpoints} from '#/shared/http/endpoints';
import {POLLING_INTERVAL_MS, instanceRequest} from './processInstance.queries';

const INCIDENTS_PAGE_SIZE = 50;
const PROCESS_INSTANCE_INCIDENTS_KEY = 'processInstanceIncidents';

type IncidentsSort = NonNullable<QueryProcessInstanceIncidentsRequestBody['sort']>;

function incidentsQuery({
	processInstanceKey,
	elementInstanceKey,
	elementId,
	errorTypes,
	sort,
	enabled,
	isPolling,
}: {
	processInstanceKey: string;
	elementInstanceKey?: string;
	elementId?: string;
	errorTypes: IncidentErrorType[];
	sort: IncidentsSort;
	enabled: boolean;
	isPolling: boolean;
}) {
	const payload = {
		filter: {state: 'ACTIVE' as const, errorType: errorTypes.length > 0 ? {$in: errorTypes} : undefined, elementId},
		sort,
	};

	return infiniteQueryOptions({
		queryKey: [PROCESS_INSTANCE_INCIDENTS_KEY, processInstanceKey, elementInstanceKey ?? null, payload] as const,
		queryFn: !enabled
			? skipToken
			: ({pageParam, signal}) =>
					instanceRequest<QueryProcessInstanceIncidentsResponseBody>(
						elementInstanceKey === undefined
							? endpoints.queryProcessInstanceIncidents(processInstanceKey, {
									...payload,
									page: {from: pageParam, limit: INCIDENTS_PAGE_SIZE},
								})
							: endpoints.queryElementInstanceIncidents(elementInstanceKey, {
									...payload,
									page: {from: pageParam, limit: INCIDENTS_PAGE_SIZE},
								}),
						signal,
					),
		initialPageParam: 0,
		getNextPageParam: (lastPage, _, lastPageParam) => {
			const nextPageParam = lastPageParam + INCIDENTS_PAGE_SIZE;
			const hasMore =
				lastPage.items.length === INCIDENTS_PAGE_SIZE &&
				(lastPage.page.hasMoreTotalItems || nextPageParam < lastPage.page.totalItems);
			return hasMore ? nextPageParam : undefined;
		},
		getPreviousPageParam: (_, __, firstPageParam) => {
			const previousPageParam = firstPageParam - INCIDENTS_PAGE_SIZE;
			return previousPageParam < 0 ? undefined : previousPageParam;
		},
		maxPages: 2,
		staleTime: POLLING_INTERVAL_MS,
		refetchInterval: isPolling ? POLLING_INTERVAL_MS : false,
	});
}

// Once paging has dropped the first page, a poll refetches from a later offset. If incidents were resolved
// meanwhile, that window comes back short and may not scroll, leaving the earlier incidents unreachable.
function hasShrunkRetainedPages({pages, pageParams}: InfiniteData<QueryProcessInstanceIncidentsResponseBody, unknown>) {
	return Number(pageParams[0] ?? 0) > 0 && (pages[0]?.items.length ?? 0) < INCIDENTS_PAGE_SIZE;
}

function incidentProcessInstancesQuery(filter: QueryProcessInstancesRequestBody['filter'], limit?: number) {
	return queryOptions({
		queryKey: ['incidentProcessInstances', filter, limit] as const,
		queryFn: ({signal}) =>
			instanceRequest<QueryProcessInstancesResponseBody>(
				endpoints.queryProcessInstances({filter, ...(limit === undefined ? {} : {page: {limit}})}),
				signal,
			),
	});
}

function incidentDecisionInstancesQuery(filter: QueryDecisionInstancesRequestBody['filter']) {
	return queryOptions({
		queryKey: ['incidentDecisionInstances', filter] as const,
		queryFn: ({signal}) =>
			instanceRequest<QueryDecisionInstancesResponseBody>(endpoints.queryDecisionInstances({filter}), signal),
	});
}

export {
	PROCESS_INSTANCE_INCIDENTS_KEY,
	incidentsQuery,
	hasShrunkRetainedPages,
	incidentProcessInstancesQuery,
	incidentDecisionInstancesQuery,
	type IncidentsSort,
};
