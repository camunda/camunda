/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo} from 'react';
import {useSuspenseInfiniteQuery, useSuspenseQuery} from '@tanstack/react-query';
import {createFileRoute, notFound} from '@tanstack/react-router';
import {queries} from '#/shared/http/queries';
import {getTasksRequestBody} from '#/tasklist/modules/available-tasks/getTasksRequestBody';
import {TasksLayoutPage} from '#/tasklist/pages/shadcn.components/TasksLayoutPage';
import {tasklistIndexSearchDefaults} from '#/tasklist/modules/available-tasks/searchSchema';

export const Route = createFileRoute('/shadcn/_auth/tasklist/_tasks')({
	notFoundComponent: () => {
		throw notFound({routeId: '/shadcn/_auth/tasklist'});
	},
	component: function TasksLayoutRoute() {
		const {data: currentUser} = useSuspenseQuery(queries.getCurrentUser());
		const requestBody = useMemo(
			() => getTasksRequestBody(tasklistIndexSearchDefaults, {currentUsername: currentUser.username}),
			[currentUser.username],
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
