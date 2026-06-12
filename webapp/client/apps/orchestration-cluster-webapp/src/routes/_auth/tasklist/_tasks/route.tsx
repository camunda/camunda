/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo} from 'react';
import {createFileRoute, retainSearchParams, stripSearchParams} from '@tanstack/react-router';
import {useSuspenseInfiniteQuery, useSuspenseQuery} from '@tanstack/react-query';
import {queries} from '#/shared/http/queries';
import {TasksLayoutPage} from '#/tasklist/pages/TasksLayoutPage';
import {tasklistIndexSearchDefaults, tasklistIndexSearchSchema} from '#/tasklist/modules/available-tasks/searchSchema';
import {tasksInfiniteQueryOptions} from '#/tasklist/modules/available-tasks/tasksQuery';
import {getTasksRequestBody} from '#/tasklist/modules/available-tasks/getTasksRequestBody';

export const Route = createFileRoute('/_auth/tasklist/_tasks')({
	validateSearch: tasklistIndexSearchSchema,
	search: {
		middlewares: [retainSearchParams(['sortBy']), stripSearchParams(tasklistIndexSearchDefaults)],
	},
	loader: ({context: {queryClient}}) =>
		queryClient.ensureInfiniteQueryData(tasksInfiniteQueryOptions(getTasksRequestBody())),
	component: function TasksLayoutRoute() {
		const {data, fetchNextPage, fetchPreviousPage, hasNextPage, hasPreviousPage} = useSuspenseInfiniteQuery(
			tasksInfiniteQueryOptions(getTasksRequestBody()),
		);
		const {data: currentUser} = useSuspenseQuery(queries.getCurrentUser());

		const tasks = useMemo(() => data.pages.flatMap((page) => page.items), [data]);

		const onScrollDown = useCallback(async () => {
			const result = await fetchNextPage();
			const pages = result.data?.pages ?? [];
			return pages[pages.length - 1]?.items ?? [];
		}, [fetchNextPage]);

		const onScrollUp = useCallback(async () => {
			const result = await fetchPreviousPage();
			return result.data?.pages[0]?.items ?? [];
		}, [fetchPreviousPage]);

		return (
			<TasksLayoutPage
				tasks={tasks}
				currentUser={currentUser}
				hasNextPage={hasNextPage}
				hasPreviousPage={hasPreviousPage}
				onScrollDown={onScrollDown}
				onScrollUp={onScrollUp}
			/>
		);
	},
});
