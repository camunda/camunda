/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute} from '@tanstack/react-router';
import {useSuspenseInfiniteQuery, useSuspenseQuery} from '@tanstack/react-query';
import {NoTaskSelectedPage} from '#/tasklist/pages/shadcn.components/NoTaskSelectedPage';
import {queries} from '#/shared/http/queries';
import {getTasksRequestBody} from '#/tasklist/modules/available-tasks/getTasksRequestBody';
import {tasklistIndexSearchDefaults} from '#/tasklist/modules/available-tasks/searchSchema';

export const Route = createFileRoute('/shadcn/_auth/tasklist/_tasks/')({
	component: function NoTaskSelectedRoute() {
		const {data: currentUser} = useSuspenseQuery(queries.getCurrentUser());
		const {data} = useSuspenseInfiniteQuery(
			queries.queryUserTasks(getTasksRequestBody(tasklistIndexSearchDefaults, {currentUsername: currentUser.username})),
		);
		const hasNoTasks = data.pages[0]?.items.length === 0;

		return <NoTaskSelectedPage hasNoTasks={hasNoTasks} />;
	},
});
