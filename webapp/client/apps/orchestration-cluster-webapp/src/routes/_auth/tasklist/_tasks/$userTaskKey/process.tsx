/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute} from '@tanstack/react-router';
import {useSuspenseQuery} from '@tanstack/react-query';
import {queries} from '#/shared/http/queries';
import {TaskDetailsProcessPage} from '#/tasklist/pages/TaskDetailsProcessPage';
import {TaskDetailsProcessRouteError} from '#/tasklist/pages/TaskDetailsProcessError';
import {TaskDetailsProcessSkeleton} from '#/tasklist/pages/TaskDetailsProcessSkeleton';
import {EmptyProcessXmlError} from '#/shared/errors';

export const Route = createFileRoute('/_auth/tasklist/_tasks/$userTaskKey/process')({
	loader: async ({context: {queryClient}, params: {userTaskKey}}) => {
		const task = await queryClient.ensureQueryData(queries.getUserTask(userTaskKey));
		return queryClient.ensureQueryData(queries.getProcessDefinitionXml(task.processDefinitionKey));
	},
	pendingComponent: TaskDetailsProcessSkeleton,
	errorComponent: TaskDetailsProcessRouteError,
	component: function ProcessTabRoute() {
		const {userTaskKey} = Route.useParams();
		const {data: task} = useSuspenseQuery(queries.getUserTask(userTaskKey));
		const {data: processXml} = useSuspenseQuery(queries.getProcessDefinitionXml(task.processDefinitionKey));

		if (processXml.trim() === '') {
			throw new EmptyProcessXmlError();
		}

		return <TaskDetailsProcessPage task={task} processXml={processXml} />;
	},
});
