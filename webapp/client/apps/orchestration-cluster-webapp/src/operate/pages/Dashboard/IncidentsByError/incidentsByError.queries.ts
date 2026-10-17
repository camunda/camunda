/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {infiniteQueryOptions, queryOptions} from '@tanstack/react-query';
import type {
	GetIncidentProcessInstanceStatisticsByErrorResponseBody,
	GetIncidentProcessInstanceStatisticsByDefinitionResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {request} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {endpoints} from '#/shared/http/endpoints';

const PAGE_SIZE = 50;
const MAX_PAGES = 5;

type PageRange = {from: number; limit: number};

// The API only offers offset pagination (no cursors) for this endpoint, so the start
// offset of a page once it's evicted by maxPages can only be recovered if we recorded
// it ourselves. Keyed by each page's end offset (= next page's start offset), which is
// always unique and already computed when fetching forward. Without this, paging
// backward past an evicted short page (fewer than PAGE_SIZE items, e.g. because
// totalItems shrank between fetches) would subtract a fixed PAGE_SIZE and either skip
// or duplicate rows.
const pageStartOffsetByEndOffset = new Map<number, PageRange>();

const incidentsByErrorInfiniteQuery = () =>
	infiniteQueryOptions({
		queryKey: ['incidentsByError'] as const,
		queryFn: async ({pageParam}): Promise<GetIncidentProcessInstanceStatisticsByErrorResponseBody> => {
			const {response, error} = await request(
				endpoints.getIncidentProcessInstanceStatisticsByError({
					page: pageParam,
				}),
			);
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json();
		},
		initialPageParam: {from: 0, limit: PAGE_SIZE} as PageRange,
		getNextPageParam: (lastPage, _allPages, lastPageParam): PageRange | undefined => {
			const nextOffset = lastPageParam.from + lastPage.items.length;
			// An empty page means there's no forward progress to make, even if totalItems
			// claims more items exist (e.g. a stale count from a concurrent change) -
			// continuing would just re-request the same offset forever.
			if (nextOffset <= lastPageParam.from) {
				return undefined;
			}
			pageStartOffsetByEndOffset.set(nextOffset, {from: lastPageParam.from, limit: lastPage.items.length});
			// totalItems is a capped lower bound, so hasMoreTotalItems must also be checked -
			// otherwise reaching the cap would stop paging even though more items exist.
			return nextOffset < lastPage.page.totalItems || lastPage.page.hasMoreTotalItems
				? {from: nextOffset, limit: PAGE_SIZE}
				: undefined;
		},
		getPreviousPageParam: (_firstPage, _allPages, firstPageParam): PageRange | undefined => {
			if (firstPageParam.from <= 0) {
				return undefined;
			}
			const recoveredRange = pageStartOffsetByEndOffset.get(firstPageParam.from);
			if (recoveredRange) {
				return recoveredRange;
			}
			const from = Math.max(0, firstPageParam.from - PAGE_SIZE);
			return {from, limit: firstPageParam.from - from};
		},
		maxPages: MAX_PAGES,
	});

const incidentsByErrorDefinitionsQuery = (errorHashCode: number) =>
	queryOptions({
		queryKey: ['incidentsByErrorDefinitions', errorHashCode] as const,
		queryFn: async (): Promise<GetIncidentProcessInstanceStatisticsByDefinitionResponseBody> => {
			const {response, error} = await request(
				endpoints.getIncidentProcessInstanceStatisticsByDefinition({
					filter: {errorHashCode},
				}),
			);
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json();
		},
	});

export {incidentsByErrorInfiniteQuery, incidentsByErrorDefinitionsQuery, PAGE_SIZE};
