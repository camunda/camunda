/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo} from 'react';
import {createFileRoute, stripSearchParams} from '@tanstack/react-router';
import {useSuspenseInfiniteQuery} from '@tanstack/react-query';
import {queries} from '#/shared/http/queries';
import {TaskDetailsHistoryErrorPage} from '#/tasklist/pages/TaskDetailsHistoryErrorPage';
import {TaskDetailsHistoryPage} from '#/tasklist/pages/TaskDetailsHistoryPage';
import {getAuditLogsRequestBody} from '#/tasklist/modules/task-details-history/getAuditLogsRequestBody';
import {
	getAuditLogSort,
	taskDetailsHistorySearchDefaults,
	taskDetailsHistorySearchSchema,
} from '#/tasklist/modules/task-details-history/sortUtils';

export const Route = createFileRoute('/_auth/tasklist/_tasks/$userTaskKey/history')({
	validateSearch: taskDetailsHistorySearchSchema,
	search: {
		middlewares: [stripSearchParams(taskDetailsHistorySearchDefaults)],
	},
	loaderDeps: ({search}) => ({...search}),
	loader: ({context: {queryClient}, params: {userTaskKey}, deps}) =>
		queryClient.ensureInfiniteQueryData(
			queries.queryUserTaskAuditLogs(userTaskKey, getAuditLogsRequestBody(getAuditLogSort(deps))),
		),
	errorComponent: TaskDetailsHistoryErrorPage,
	component: function HistoryRoute() {
		const {userTaskKey} = Route.useParams();
		const search = Route.useSearch();
		const {data, fetchNextPage, hasNextPage, isFetchingNextPage} = useSuspenseInfiniteQuery({
			...queries.queryUserTaskAuditLogs(userTaskKey, getAuditLogsRequestBody(getAuditLogSort(search))),
			refetchInterval: 5000,
		});
		const auditLogs = useMemo(() => data.pages.flatMap((page) => page.items), [data]);

		const onScrollDown = useCallback(() => {
			if (hasNextPage && !isFetchingNextPage) {
				void fetchNextPage();
			}
		}, [fetchNextPage, hasNextPage, isFetchingNextPage]);

		return <TaskDetailsHistoryPage auditLogs={auditLogs} search={search} onScrollDown={onScrollDown} />;
	},
});
