/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute} from '@tanstack/react-router';

import {NoTaskSelectedPage} from '#/tasklist/pages/NoTaskSelectedPage';
import {useSuspenseInfiniteQuery, useSuspenseQuery} from '@tanstack/react-query';
import {queries} from '#/shared/http/queries';
import {getTasksRequestBody} from '#/tasklist/modules/available-tasks/getTasksRequestBody';

export const Route = createFileRoute('/_auth/tasklist/_tasks/')({
	component: function NoTaskSelectedRoute() {
		const search = Route.useSearch();
		const {data: currentUser} = useSuspenseQuery(queries.getCurrentUser());
		const {data} = useSuspenseInfiniteQuery(
			queries.queryUserTasks(getTasksRequestBody(search, {currentUsername: currentUser.username})),
		);
		const hasNoTasks = data.pages[0]?.items.length === 0;

		return <NoTaskSelectedPage hasNoTasks={hasNoTasks} />;
	},
});
