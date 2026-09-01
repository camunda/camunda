/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {CurrentUser, UserTask, Variable} from '@camunda/camunda-api-zod-schemas/8.10';
import {useNavigate} from '@tanstack/react-router';
import {useCallback} from 'react';
import type {TasklistIndexSearch} from '#/tasklist/modules/available-tasks/searchSchema';
import {TaskDetailsForm} from '#/tasklist/modules/task-details-form/shadcn.components/TaskDetailsForm';
import {useTaskCompletion} from '#/tasklist/modules/task-details/useTaskCompletion';
import {TaskDetailsVariables} from '#/tasklist/modules/task-details-variables/shadcn.components/TaskDetailsVariables';

type Props = {
	task: UserTask;
	currentUser: CurrentUser;
	search: TasklistIndexSearch;
	formSchema: string | null;
	variables: Variable[];
	totalVariables?: number;
	hasNextVariablesPage?: boolean;
	isFetchingNextVariablesPage?: boolean;
	isNextVariablesPageError?: boolean;
	onLoadNextVariablesPage?: () => void;
};

const TaskDetailsTaskPage: React.FC<Props> = ({
	task,
	currentUser,
	search,
	formSchema,
	variables,
	totalVariables = variables.length,
	hasNextVariablesPage = false,
	isFetchingNextVariablesPage = false,
	isNextVariablesPageError = false,
	onLoadNextVariablesPage = () => {},
}) => {
	const navigate = useNavigate();
	const onComplete = useCallback(() => {
		navigate({
			to: '/shadcn/tasklist',
			search,
			state: (state) => ({
				...state,
				tasklistAutoSelectSource: 'task-completion',
			}),
		});
	}, [navigate, search]);
	const {status, isCompletionAllowed, isHidden, complete} = useTaskCompletion({
		userTaskKey: task.userTaskKey,
		currentUser: currentUser.username,
		taskState: task.state,
		assignee: task.assignee ?? null,
		onComplete,
		isShadcn: true,
	});
	const isEditingAllowed = currentUser.username === task.assignee && task.state === 'CREATED';

	if (formSchema !== null) {
		return (
			<TaskDetailsForm
				formSchema={formSchema}
				variables={variables}
				completionStatus={status}
				isCompletionAllowed={isCompletionAllowed}
				isHidden={isHidden}
				onSubmit={complete}
			/>
		);
	}

	return (
		<TaskDetailsVariables
			userTaskKey={task.userTaskKey}
			variables={variables}
			totalVariables={totalVariables}
			isEditingAllowed={isEditingAllowed}
			isCompletionAllowed={isCompletionAllowed}
			isCompleted={task.state === 'COMPLETED'}
			completionStatus={status}
			isCompletionHidden={isHidden}
			hasNextPage={hasNextVariablesPage}
			isFetchingNextPage={isFetchingNextVariablesPage}
			isNextPageError={isNextVariablesPageError}
			onLoadNextPage={onLoadNextVariablesPage}
			onSubmit={complete}
		/>
	);
};

export {TaskDetailsTaskPage};
