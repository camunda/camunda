/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo} from 'react';
import {createFileRoute, notFound, retainSearchParams, stripSearchParams} from '@tanstack/react-router';
import {useSuspenseInfiniteQuery, useSuspenseQuery} from '@tanstack/react-query';
import {queries} from '#/shared/http/queries';
import {TasksLayoutPage} from '#/tasklist/pages/TasksLayoutPage';
import {
	tasklistIndexSearchDefaults,
	tasklistIndexSearchSchema,
	enforceSortInvariant,
} from '#/tasklist/modules/available-tasks/searchSchema';
import {getTasksRequestBody} from '#/tasklist/modules/available-tasks/getTasksRequestBody';

export const Route = createFileRoute('/_auth/tasklist/_tasks')({
	validateSearch: tasklistIndexSearchSchema,
	search: {
		middlewares: [
			stripSearchParams(tasklistIndexSearchDefaults),
			enforceSortInvariant,
			retainSearchParams(['filter', 'sortBy']),
		],
	},
	loaderDeps: ({search: {filter, sortBy}}) => ({filter, sortBy}),
	notFoundComponent: () => {
		throw notFound({routeId: '/_auth/tasklist'});
	},
	loader: async ({context: {queryClient}, deps: {filter, sortBy}}) => {
		const currentUser = await queryClient.ensureQueryData(queries.getCurrentUser());
		return queryClient.ensureInfiniteQueryData(
			queries.queryUserTasks(getTasksRequestBody({filter, sortBy}, {currentUsername: currentUser.username})),
		);
	},
	component: function TasksLayoutRoute() {
		const {filter, sortBy} = Route.useSearch();
		const {data: currentUser} = useSuspenseQuery(queries.getCurrentUser());

		const requestBody = useMemo(
			() => getTasksRequestBody({filter, sortBy}, {currentUsername: currentUser.username}),
			[filter, sortBy, currentUser.username],
		);

		const {data, fetchNextPage, fetchPreviousPage, hasNextPage, hasPreviousPage} = useSuspenseInfiniteQuery({
			...queries.queryUserTasks(requestBody),
			refetchInterval: 5000,
		});

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
