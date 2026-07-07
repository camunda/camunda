/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, useSearch} from '@tanstack/react-router';
import {useSuspenseQuery} from '@tanstack/react-query';
import {queries} from '#/shared/http/queries';
import {TaskDetailsTaskPage} from '#/tasklist/pages/TaskDetailsTaskPage';

export const Route = createFileRoute('/_auth/tasklist/_tasks/$userTaskKey/')({
	component: function TaskTabRoute() {
		const {userTaskKey} = Route.useParams();
		const search = useSearch({from: '/_auth/tasklist/_tasks'});
		const {data: task} = useSuspenseQuery(queries.getUserTask(userTaskKey));
		const {data: currentUser} = useSuspenseQuery(queries.getCurrentUser());

		return <TaskDetailsTaskPage task={task} currentUser={currentUser} search={search} />;
	},
	remountDeps: ({params: {userTaskKey}}) => [userTaskKey],
});
