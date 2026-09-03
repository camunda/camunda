/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {infiniteQueryOptions, queryOptions} from '@tanstack/react-query';
import type {
	GetProcessDefinitionInstanceStatisticsRequestBody,
	GetProcessDefinitionInstanceStatisticsResponseBody,
	GetProcessDefinitionInstanceVersionStatisticsResponseBody,
	ProcessDefinition,
	QueryProcessDefinitionsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {request} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {endpoints} from '#/shared/http/endpoints';

const PAGE_SIZE = 50;
const MAX_PAGES = 5;
const DRAINING_REFETCH_INTERVAL = 5000;
const DRAINING_PAGE_LIMIT = 1000;

type DrainingLookup = {
	byId: Set<string>;
	byKey: Set<string>;
};

async function fetchAllDrainingProcessDefinitions(): Promise<ProcessDefinition[]> {
	const items: ProcessDefinition[] = [];
	let after: string | undefined;

	do {
		const {response, error} = await request(
			endpoints.queryProcessDefinitions({
				filter: {state: 'DRAINING'},
				page: {limit: DRAINING_PAGE_LIMIT, after},
			}),
		);
		if (error !== null) {
			throw mapQueryError(error);
		}
		const page: QueryProcessDefinitionsResponseBody = await response.json();
		items.push(...page.items);
		after = page.page.hasMoreTotalItems && page.page.endCursor !== null ? page.page.endCursor : undefined;
	} while (after !== undefined);

	return items;
}

const drainingProcessDefinitionsQuery = () =>
	queryOptions({
		queryKey: ['drainingProcessDefinitions'] as const,
		staleTime: DRAINING_REFETCH_INTERVAL,
		refetchInterval: DRAINING_REFETCH_INTERVAL,
		queryFn: fetchAllDrainingProcessDefinitions,
		select: (items): DrainingLookup => ({
			byId: new Set(items.map((item) => item.processDefinitionId)),
			byKey: new Set(items.map((item) => item.processDefinitionKey)),
		}),
	});

const DEFAULT_SORT: Pick<GetProcessDefinitionInstanceStatisticsRequestBody, 'sort'> = {
	sort: [
		{field: 'activeInstancesWithIncidentCount', order: 'desc'},
		{field: 'activeInstancesWithoutIncidentCount', order: 'desc'},
	],
};

const instancesByProcessInfiniteQuery = () =>
	infiniteQueryOptions({
		queryKey: ['instancesByProcess'] as const,
		queryFn: async ({pageParam}): Promise<GetProcessDefinitionInstanceStatisticsResponseBody> => {
			const {response, error} = await request(
				endpoints.getProcessDefinitionInstanceStatistics({
					...DEFAULT_SORT,
					page: {from: pageParam, limit: PAGE_SIZE},
				}),
			);
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json();
		},
		initialPageParam: 0,
		getNextPageParam: (lastPage, _, lastPageParam) => {
			const next = lastPageParam + PAGE_SIZE;
			return next >= lastPage.page.totalItems ? undefined : next;
		},
		getPreviousPageParam: (_, __, firstPageParam) => {
			const prev = firstPageParam - PAGE_SIZE;
			return prev < 0 ? undefined : prev;
		},
		maxPages: MAX_PAGES,
	});

const instancesByProcessVersionsQuery = (processDefinitionId: string, tenantId: string | null) =>
	queryOptions({
		queryKey: ['instancesByProcessVersions', processDefinitionId, tenantId] as const,
		queryFn: async (): Promise<GetProcessDefinitionInstanceVersionStatisticsResponseBody> => {
			const {response, error} = await request(
				endpoints.getProcessDefinitionInstanceVersionStatistics({
					filter: {processDefinitionId, tenantId},
					sort: [{field: 'processDefinitionVersion', order: 'desc'}],
				}),
			);
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json();
		},
	});

export {
	instancesByProcessInfiniteQuery,
	instancesByProcessVersionsQuery,
	drainingProcessDefinitionsQuery,
	PAGE_SIZE,
	type DrainingLookup,
};
