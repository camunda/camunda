/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback} from 'react';
import {useNavigate} from '@tanstack/react-router';
import {useQueryClient} from '@tanstack/react-query';
import type {CurrentUser, UserTask} from '@camunda/camunda-api-zod-schemas/8.10';
import {getStateLocally} from '#/shared/browser-storage/local-storage';
import {tracking} from '#/shared/tracking';
import {queries} from '#/shared/http/queries';
import type {TasklistIndexSearch} from '#/tasklist/modules/available-tasks/searchSchema';
import {getTasksRequestBody} from '#/tasklist/modules/available-tasks/getTasksRequestBody';
import {CompleteTaskButton} from '#/tasklist/modules/task-details/components/CompleteTaskButton';
import {useTaskCompletion} from '#/tasklist/modules/task-details/useTaskCompletion';
import styles from './TaskDetailsTaskPage.module.scss';

type Props = {
	task: UserTask;
	currentUser: CurrentUser;
	search: TasklistIndexSearch;
};

const TaskDetailsTaskPage: React.FC<Props> = ({task, currentUser, search}) => {
	const navigate = useNavigate();
	const queryClient = useQueryClient();
	const customFilter = getStateLocally('tasklist.customFilters')?.[search.filter];
	const onComplete = useCallback(async () => {
		tracking.track({
			eventName: 'tasklist:task-completed',
			isCamundaForm: false,
			hasRemainingTasks: false,
			filter: search.filter,
			customFilters: Object.keys(customFilter ?? {}),
			customFilterVariableCount: customFilter?.variables?.length ?? 0,
		});

		if (getStateLocally('tasklist.autoSelectNextTask') !== true) {
			navigate({to: '/tasklist', search});
			return;
		}

		const data = await queryClient.fetchInfiniteQuery(
			queries.queryUserTasks(getTasksRequestBody(search, {currentUsername: currentUser.username})),
		);
		const tasks = data.pages.flatMap((page) => page.items);
		const nextOpenTask = tasks.find(({state, userTaskKey}) => state === 'CREATED' && userTaskKey !== task.userTaskKey);

		if (nextOpenTask === undefined) {
			navigate({to: '/tasklist', search});
			return;
		}

		tracking.track({
			eventName: 'tasklist:task-opened',
			by: 'auto-select',
			position: tasks.indexOf(nextOpenTask),
			filter: search.filter,
			sorting: search.sortBy,
		});

		navigate({to: '/tasklist/$userTaskKey', params: {userTaskKey: nextOpenTask.userTaskKey}, search});
	}, [currentUser.username, navigate, queryClient, search, customFilter, task.userTaskKey]);
	const {status, isCompletionAllowed, isHidden, complete} = useTaskCompletion({
		userTaskKey: task.userTaskKey,
		currentUser: currentUser.username,
		taskState: task.state,
		assignee: task.assignee,
		onComplete,
	});

	return (
		<div className={styles.container} data-testid="task-tab-content">
			<div className={styles.footer}>
				<CompleteTaskButton status={status} onClick={complete} isHidden={isHidden} isDisabled={!isCompletionAllowed} />
			</div>
		</div>
	);
};

export {TaskDetailsTaskPage};
