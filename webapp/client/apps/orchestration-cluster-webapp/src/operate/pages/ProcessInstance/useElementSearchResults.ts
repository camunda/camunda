/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo} from 'react';
import {useInfiniteQuery, useQueryClient} from '@tanstack/react-query';
import type {
	ElementInstance,
	QueryElementInstancesRequestBody,
	QueryElementInstancesResponseBody,
	QuerySortOrder,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {endpoints} from '#/shared/http/endpoints';
import {historySort, instanceRequest, POLLING_INTERVAL_MS} from './processInstance.queries';
import {escapeLikePattern} from './escapeLikePattern';
import type {ElementStatusFilter} from './processInstanceSearch';

const PAGE_LIMIT = 50;
const MAX_PAGES = 5;

function buildElementSearchRequest({
	processInstanceKey,
	searchText,
	statusFilter,
	sortOrder,
}: {
	processInstanceKey: string;
	searchText: string;
	statusFilter?: ElementStatusFilter;
	sortOrder: QuerySortOrder;
}): QueryElementInstancesRequestBody {
	const pattern = escapeLikePattern(searchText);
	return {
		filter: {
			processInstanceKey,
			...(searchText !== '' && {$or: [{elementName: {$like: pattern}}, {elementId: {$like: pattern}}]}),
			...(statusFilter === 'active' && {state: 'ACTIVE', hasIncident: false}),
			...(statusFilter === 'incidents' && {hasIncident: true}),
		},
		sort: historySort(sortOrder),
	};
}

function getNextSearchOffset({items, page}: QueryElementInstancesResponseBody, offset: number) {
	const next = offset + PAGE_LIMIT;
	return items.length === PAGE_LIMIT && next < page.totalItems ? next : null;
}

function isWindowPastShrunkResults({
	items,
	totalCount,
	firstOffset,
}: {
	items: ElementInstance[];
	totalCount: number;
	firstOffset: number;
}) {
	return items.length < PAGE_LIMIT && totalCount > 0 && firstOffset > 0;
}

function uniqueByKey(items: ElementInstance[]) {
	const seen = new Set<string>();
	return items.filter(({elementInstanceKey}) => {
		if (seen.has(elementInstanceKey)) {
			return false;
		}
		seen.add(elementInstanceKey);
		return true;
	});
}

function useElementSearchResults(request: QueryElementInstancesRequestBody) {
	const client = useQueryClient();
	const queryKey = useMemo(() => ['elementSearchResults', request], [request]);
	const query = useInfiniteQuery({
		queryKey,
		queryFn: ({pageParam, signal}) =>
			instanceRequest<QueryElementInstancesResponseBody>(
				endpoints.queryElementInstances({...request, page: {from: pageParam, limit: PAGE_LIMIT}}),
				signal,
			),
		initialPageParam: 0,
		getNextPageParam: (lastPage, _, lastPageParam) => getNextSearchOffset(lastPage, lastPageParam),
		getPreviousPageParam: (_, __, firstPageParam) => (firstPageParam > 0 ? firstPageParam - PAGE_LIMIT : null),
		maxPages: MAX_PAGES,
		refetchInterval: POLLING_INTERVAL_MS,
		staleTime: POLLING_INTERVAL_MS,
		placeholderData: (previous) => previous,
		select: (data) => ({
			items: uniqueByKey(data.pages.flatMap((page) => page.items)),
			totalCount: data.pages[0]?.page.totalItems ?? 0,
			firstOffset: data.pageParams[0] ?? 0,
		}),
	});
	const {data, isPlaceholderData} = query;
	useEffect(() => {
		if (data && !isPlaceholderData && isWindowPastShrunkResults(data)) {
			void client.resetQueries({queryKey});
		}
	}, [client, data, isPlaceholderData, queryKey]);
	return query;
}

export {buildElementSearchRequest, getNextSearchOffset, isWindowPastShrunkResults, useElementSearchResults, PAGE_LIMIT};
