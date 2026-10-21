/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {infiniteQueryOptions, keepPreviousData, queryOptions} from '@tanstack/react-query';
import type {
	AgentInstanceStatus,
	QueryAgentInstanceHistoryRequestBody,
	QueryAgentInstanceHistoryResponseBody,
	QuerySortOrder,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {endpoints} from '#/shared/http/endpoints';
import {POLLING_INTERVAL_MS} from '../details.queries';
import {ACTIVE_AGENT_STATUSES} from '../instanceDiagram.queries';
import {instanceRequest} from '../processInstance.queries';

const HISTORY_PAGE_SIZE = 100;

function getRefetchInterval(agentInstanceStatus: AgentInstanceStatus) {
	return ACTIVE_AGENT_STATUSES.some((status) => status === agentInstanceStatus) ? POLLING_INTERVAL_MS : false;
}

function latestAgentMessageQuery(agentInstanceKey: string, agentInstanceStatus: AgentInstanceStatus) {
	return queryOptions({
		queryKey: ['agentInstanceLatestMessage', agentInstanceKey] as const,
		queryFn: ({signal}) =>
			instanceRequest<QueryAgentInstanceHistoryResponseBody>(
				endpoints.queryAgentInstanceHistory(agentInstanceKey, {
					sort: [
						{field: 'producedAt', order: 'DESC'},
						{field: 'historyItemKey', order: 'DESC'},
					],
					filter: {commitStatus: 'COMMITTED', role: 'ASSISTANT'},
					page: {limit: 1},
				}),
				signal,
			),
		select: (data) => data.items[0] ?? null,
		staleTime: POLLING_INTERVAL_MS,
		refetchInterval: getRefetchInterval(agentInstanceStatus),
	});
}

function agentInstanceHistoryQuery({
	agentInstanceKey,
	agentInstanceStatus,
	sortOrder,
	elementInstanceKey,
}: {
	agentInstanceKey: string;
	agentInstanceStatus: AgentInstanceStatus;
	sortOrder: QuerySortOrder;
	elementInstanceKey: string | null;
}) {
	const body: QueryAgentInstanceHistoryRequestBody = {
		sort: [
			{field: 'producedAt', order: sortOrder},
			{field: 'historyItemKey', order: sortOrder},
		],
		filter: {
			role: {$in: ['ASSISTANT', 'USER', 'TOOL_RESULT']},
			commitStatus: 'COMMITTED',
			elementInstanceKey: elementInstanceKey ?? undefined,
		},
	};

	return infiniteQueryOptions({
		queryKey: ['agentInstanceHistory', agentInstanceKey, body] as const,
		queryFn: ({pageParam, signal}) =>
			instanceRequest<QueryAgentInstanceHistoryResponseBody>(
				endpoints.queryAgentInstanceHistory(agentInstanceKey, {
					...body,
					page: {limit: HISTORY_PAGE_SIZE, after: pageParam},
				}),
				signal,
			),
		initialPageParam: undefined as string | undefined,
		getNextPageParam: (lastPage, allPages) => {
			const loadedItems = new Set(allPages.flatMap((page) => page.items.map((item) => item.historyItemKey))).size;
			const hasMoreItems = lastPage.page.hasMoreTotalItems || loadedItems < lastPage.page.totalItems;
			return lastPage.items.length === 0 || lastPage.page.endCursor === null || !hasMoreItems
				? undefined
				: lastPage.page.endCursor;
		},
		staleTime: POLLING_INTERVAL_MS,
		refetchInterval: getRefetchInterval(agentInstanceStatus),
		placeholderData: keepPreviousData,
	});
}

export {agentInstanceHistoryQuery, latestAgentMessageQuery};
