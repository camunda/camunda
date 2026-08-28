/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo} from 'react';
import {useSuspenseInfiniteQuery, useSuspenseQuery} from '@tanstack/react-query';
import {createFileRoute, notFound, retainSearchParams, stripSearchParams, useRouterState} from '@tanstack/react-router';
import {queries} from '#/shared/http/queries';
import {getTasksRequestBody} from '#/tasklist/modules/available-tasks/getTasksRequestBody';
import {TasksLayoutPage} from '#/tasklist/pages/shadcn.components/TasksLayoutPage';
import {
	enforceSortInvariant,
	stripCustomFilterParams,
	tasklistIndexSearchDefaults,
	tasklistIndexSearchSchema,
} from '#/tasklist/modules/available-tasks/searchSchema';

export const Route = createFileRoute('/shadcn/_auth/tasklist/_tasks')({
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
		throw notFound({routeId: '/shadcn/_auth/tasklist'});
	},
	beforeLoad: async ({context: {queryClient}, search}) => {
		const currentUser = await queryClient.query(queries.getCurrentUser());
		const queryOptions = queries.queryUserTasks(getTasksRequestBody(search, {currentUsername: currentUser.username}));
		await queryClient.infiniteQuery(queryOptions);
	},
	component: function TasksLayoutRoute() {
		const search = Route.useSearch();
		const isPending = useRouterState({select: ({status}) => status === 'pending'});
		const {data: currentUser} = useSuspenseQuery(queries.getCurrentUser());
		const requestBody = useMemo(
			() => getTasksRequestBody(search, {currentUsername: currentUser.username}),
			[search, currentUser.username],
		);
		const {
			data,
			fetchNextPage,
			fetchPreviousPage,
			hasNextPage,
			hasPreviousPage,
			isFetchingNextPage,
			isFetchingPreviousPage,
		} = useSuspenseInfiniteQuery({
			...queries.queryUserTasks(requestBody),
			refetchInterval: 5000,
		});
		const onScrollDown = useCallback(async () => {
			await fetchNextPage();
		}, [fetchNextPage]);
		const onScrollUp = useCallback(async () => {
			await fetchPreviousPage();
		}, [fetchPreviousPage]);

		return (
			<TasksLayoutPage
				pages={data.pages}
				currentUser={currentUser}
				filter={search.filter}
				sortBy={search.sortBy}
				isPending={isPending}
				hasNextPage={hasNextPage}
				hasPreviousPage={hasPreviousPage}
				onScrollDown={onScrollDown}
				onScrollUp={onScrollUp}
				isFetchingNextPage={isFetchingNextPage}
				isFetchingPreviousPage={isFetchingPreviousPage}
			/>
		);
	},
});
