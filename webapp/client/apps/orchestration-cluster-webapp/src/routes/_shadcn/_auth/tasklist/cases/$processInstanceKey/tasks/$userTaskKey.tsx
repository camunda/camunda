/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback} from 'react';
import {useTranslation} from 'react-i18next';
import {createFileRoute, notFound, useNavigate} from '@tanstack/react-router';
import {useSuspenseQuery} from '@tanstack/react-query';
import {toast} from '@camunda/design-system';
import {TasklistCaseTaskPage} from '#/tasklist/pages/TasklistCaseTaskPage';
import {loadCaseTask} from '#/tasklist/modules/case-details/caseData';
import {useNextWork} from '#/tasklist/modules/case-details/useNextWork';
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
		const {t} = useTranslation();
		const navigate = useNavigate();
		const {processInstanceKey, userTaskKey} = Route.useParams();
		const {formSchema, variables} = Route.useLoaderData();
		const {data: currentUser} = useSuspenseQuery(queries.getCurrentUser());
		const {data: processInstance} = useSuspenseQuery(queries.getProcessInstance(processInstanceKey));
		const {data: task} = useSuspenseQuery(queries.getUserTask(userTaskKey));

		const openTask = useCallback(
			(nextTaskKey: string) =>
				navigate({
					to: '/tasklist/cases/$processInstanceKey/tasks/$userTaskKey',
					params: {processInstanceKey, userTaskKey: nextTaskKey},
				}),
			[navigate, processInstanceKey],
		);
		const showTasks = useCallback(
			() => navigate({to: '/tasklist/cases/$processInstanceKey', params: {processInstanceKey}}),
			[navigate, processInstanceKey],
		);
		const showNoWork = useCallback(() => {
			toast.info(t('tasklist.caseNoNextWork'));
			showTasks();
		}, [showTasks, t]);
		const {isSearchingNextWork, searchNextWork} = useNextWork({
			processInstance,
			username: currentUser.username,
			onNextTask: openTask,
			onNoWork: showNoWork,
		});

		return (
			<TasklistCaseTaskPage
				task={task}
				currentUser={currentUser}
				formSchema={formSchema}
				variables={variables}
				isSearchingNextWork={isSearchingNextWork}
				onCompleted={searchNextWork}
				onClose={showTasks}
			/>
		);
	},
});
