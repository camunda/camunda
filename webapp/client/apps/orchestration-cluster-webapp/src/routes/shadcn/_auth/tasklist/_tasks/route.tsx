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
