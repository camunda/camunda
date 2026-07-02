/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo} from 'react';
import {createFileRoute, notFound, redirect, retainSearchParams, stripSearchParams} from '@tanstack/react-router';
import {useSuspenseInfiniteQuery, useSuspenseQuery} from '@tanstack/react-query';
import {queries} from '#/shared/http/queries';
import {TasksLayoutPage} from '#/tasklist/pages/TasksLayoutPage';
import {
	tasklistIndexSearchDefaults,
	tasklistIndexSearchSchema,
	enforceSortInvariant,
	stripCustomFilterParams,
} from '#/tasklist/modules/available-tasks/searchSchema';
import {getTasksRequestBody} from '#/tasklist/modules/available-tasks/getTasksRequestBody';
import {getStateLocally} from '#/shared/browser-storage/local-storage';
import {tracking} from '#/shared/tracking';

export const Route = createFileRoute('/_auth/tasklist/_tasks')({
	validateSearch: tasklistIndexSearchSchema,
	search: {
		middlewares: [
			stripSearchParams(tasklistIndexSearchDefaults),
			retainSearchParams(['filter', 'sortBy']),
			enforceSortInvariant,
			stripCustomFilterParams,
		],
	},
	notFoundComponent: () => {
		throw notFound({routeId: '/_auth/tasklist'});
	},
	beforeLoad: async ({context: {queryClient}, search, matches}) => {
		const autoSelectNextTask = getStateLocally('tasklist.autoSelectNextTask') ?? false;
		const currentUser = await queryClient.ensureQueryData(queries.getCurrentUser());
		const data = await queryClient.ensureInfiniteQueryData(
			queries.queryUserTasks(getTasksRequestBody(search, {currentUsername: currentUser.username})),
		);
		const taskDetailsMatch = matches.find((match) => match.routeId === '/_auth/tasklist/_tasks/$userTaskKey');
		const tasks = data.pages.flatMap((page) => page.items);
		const nextOpenTask = tasks[0];

		if (autoSelectNextTask && taskDetailsMatch === undefined && nextOpenTask !== undefined) {
			tracking.track({
				eventName: 'tasklist:task-opened',
				by: 'auto-select',
				position: 0,
				filter: search.filter,
				sorting: search.sortBy,
			});
			throw redirect({
				to: '/tasklist/$userTaskKey',
				params: {userTaskKey: nextOpenTask.userTaskKey},
				search,
			});
		}

		return data;
	},
	component: function TasksLayoutRoute() {
		const search = Route.useSearch();
		const {data: currentUser} = useSuspenseQuery(queries.getCurrentUser());

		const requestBody = useMemo(
			() => getTasksRequestBody(search, {currentUsername: currentUser.username}),
			[search, currentUser.username],
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
