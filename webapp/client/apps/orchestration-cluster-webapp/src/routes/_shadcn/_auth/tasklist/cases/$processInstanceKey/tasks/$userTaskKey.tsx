/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback} from 'react';
import {createFileRoute, notFound, useNavigate} from '@tanstack/react-router';
import {useSuspenseQuery} from '@tanstack/react-query';
import {TasklistCaseTaskPage} from '#/tasklist/pages/TasklistCaseTaskPage';
import {loadCaseTask} from '#/tasklist/modules/case-details/caseData';
import {queries} from '#/shared/http/queries';

export const Route = createFileRoute('/_shadcn/_auth/tasklist/cases/$processInstanceKey/tasks/$userTaskKey')({
	loader: async ({context: {queryClient}, params: {userTaskKey}}) => {
		const taskData = await loadCaseTask(queryClient, userTaskKey);

		if (taskData === null) {
			throw notFound();
		}

		return taskData;
	},
	remountDeps: ({params: {userTaskKey}}) => [userTaskKey],
	component: function TasklistCaseTaskRoute() {
		const navigate = useNavigate();
		const {processInstanceKey, userTaskKey} = Route.useParams();
		const {formSchema, variables} = Route.useLoaderData();
		const {data: currentUser} = useSuspenseQuery(queries.getCurrentUser());
		const {data: task} = useSuspenseQuery(queries.getUserTask(userTaskKey));
		const showTasks = useCallback(
			() => navigate({to: '/tasklist/cases/$processInstanceKey', params: {processInstanceKey}}),
			[navigate, processInstanceKey],
		);

		return (
			<TasklistCaseTaskPage
				task={task}
				currentUser={currentUser}
				formSchema={formSchema}
				variables={variables}
				onCompleted={showTasks}
				onClose={showTasks}
			/>
		);
	},
});
