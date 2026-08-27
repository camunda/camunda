/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {CurrentUser, UserTask} from '@camunda/camunda-api-zod-schemas/8.10';
import {useNavigate} from '@tanstack/react-router';
import {useCallback} from 'react';
import {useTaskCompletion} from '#/tasklist/modules/task-details/useTaskCompletion';
import {CompleteTaskButton} from '#/tasklist/modules/task-details/shadcn.components/CompleteTaskButton';
import type {TasklistIndexSearch} from '#/tasklist/modules/available-tasks/searchSchema';

type Props = {
	task: UserTask;
	currentUser: CurrentUser;
	search: TasklistIndexSearch;
};

const TaskDetailsTaskPage: React.FC<Props> = ({task, currentUser, search}) => {
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

	return (
		<div className="flex h-full min-h-0 flex-col" data-testid="task-tab-content">
			<footer className="mt-auto flex w-full justify-end border-t border-border p-4">
				<CompleteTaskButton
					status={status}
					isDisabled={!isCompletionAllowed}
					isHidden={isHidden}
					onClick={() => complete({})}
				/>
			</footer>
		</div>
	);
};

export {TaskDetailsTaskPage};
