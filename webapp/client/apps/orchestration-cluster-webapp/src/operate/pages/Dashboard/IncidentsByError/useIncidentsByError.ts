/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {infiniteQueryOptions, useSuspenseInfiniteQuery, useQuery} from '@tanstack/react-query';
import type {
	GetIncidentProcessInstanceStatisticsByErrorResponseBody,
	GetIncidentProcessInstanceStatisticsByDefinitionResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {request} from '#/shared/http/request';
import {endpoints} from '#/shared/http/endpoints';

const PAGE_SIZE = 50;
const MAX_PAGES = 5;

const incidentsByErrorInfiniteQuery = () =>
	infiniteQueryOptions({
		queryKey: ['incidentsByError'] as const,
		queryFn: async ({pageParam}): Promise<GetIncidentProcessInstanceStatisticsByErrorResponseBody> => {
			const {response, error} = await request(
				endpoints.getIncidentProcessInstanceStatisticsByError({
					page: {from: pageParam, limit: PAGE_SIZE},
				}),
			);
			if (error !== null) {
				throw error;
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

function useIncidentsByError() {
	return useSuspenseInfiniteQuery({
		...incidentsByErrorInfiniteQuery(),
		refetchInterval: 5000,
	});
}

function useIncidentsByErrorDefinitions(errorHashCode: number) {
	return useQuery({
		queryKey: ['incidentsByErrorDefinitions', errorHashCode] as const,
		queryFn: async (): Promise<GetIncidentProcessInstanceStatisticsByDefinitionResponseBody> => {
			const {response, error} = await request(
				endpoints.getIncidentProcessInstanceStatisticsByDefinition({
					filter: {errorHashCode},
				}),
			);
			if (error !== null) {
				throw error;
			}
			return response.json();
		},
	});
}

export {incidentsByErrorInfiniteQuery, useIncidentsByError, useIncidentsByErrorDefinitions, PAGE_SIZE};
