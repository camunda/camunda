/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback} from 'react';
import {useNavigate} from '@tanstack/react-router';
import type {CurrentUser, UserTask, Variable} from '@camunda/camunda-api-zod-schemas/8.10';
import {getStateLocally} from '#/shared/browser-storage/local-storage';
import {tracking} from '#/shared/tracking';
import type {TasklistIndexSearch} from '#/tasklist/modules/available-tasks/searchSchema';
import {CompleteTaskButton} from '#/tasklist/modules/task-details/components/CompleteTaskButton';
import {useTaskCompletion} from '#/tasklist/modules/task-details/useTaskCompletion';
import {TaskDetailsForm} from '#/tasklist/modules/task-details-form/TaskDetailsForm';
import styles from './TaskDetailsTaskPage.module.scss';

type Props = {
	task: UserTask;
	currentUser: CurrentUser;
	search: TasklistIndexSearch;
	formSchema: string | null;
	variables: Variable[];
};

const TaskDetailsTaskPage: React.FC<Props> = ({task, currentUser, search, formSchema, variables}) => {
	const navigate = useNavigate();
	const isCamundaForm = formSchema !== null;
	const customFilter = getStateLocally('tasklist.customFilters')?.[search.filter];

	const onComplete = useCallback(() => {
		tracking.track({
			eventName: 'tasklist:task-completed',
			isCamundaForm,
			hasRemainingTasks: false,
			filter: search.filter,
			customFilters: Object.keys(customFilter ?? {}),
			customFilterVariableCount: customFilter?.variables?.length ?? 0,
		});

		navigate({
			to: '/tasklist',
			search,
			state: (state) => ({
				...state,
				tasklistAutoSelectSource: 'task-completion',
			}),
		});
	}, [navigate, search, customFilter, isCamundaForm]);
	const {status, isCompletionAllowed, isHidden, complete} = useTaskCompletion({
		userTaskKey: task.userTaskKey,
		currentUser: currentUser.username,
		taskState: task.state,
		assignee: task.assignee,
		onComplete,
	});

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
		<div className={styles.container} data-testid="task-tab-content">
			<div className={styles.content} />
			<div className={styles.footer}>
				<CompleteTaskButton
					status={status}
					onClick={() => {
						complete();
					}}
					isHidden={isHidden}
					isDisabled={!isCompletionAllowed}
				/>
			</div>
		</div>
	);
};

export {TaskDetailsTaskPage};
