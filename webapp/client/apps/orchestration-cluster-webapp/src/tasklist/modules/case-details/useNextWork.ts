/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, useQueryClient} from '@tanstack/react-query';
import type {ProcessInstance, UserTask} from '@camunda/camunda-api-zod-schemas/8.11';
import {queries} from '#/shared/http/queries';
import {getOpenCaseUserTasksRequestBody, isTaskOfCase} from '#/tasklist/modules/case-details/getCaseRequestBodies';

const POLL_INTERVAL = 1000;
const MAX_WAIT = 10_000;

function pickNextTask(tasks: UserTask[], completedTaskKey: string, username: string): UserTask | undefined {
	const candidates = tasks.filter(({userTaskKey}) => userTaskKey !== completedTaskKey);

	return (
		candidates.find(({assignee}) => assignee === username) ??
		candidates.find(({assignee}) => assignee === null || assignee === undefined) ??
		candidates[0]
	);
}

/**
 * After a completion, searches the user tasks of the case until the next task appears (NEXT_WORK.md).
 * The user task search is eventually consistent, so it searches again for a short time.
 */
function useNextWork({
	processInstance,
	username,
	onNextTask,
	onNoWork,
}: {
	processInstance: ProcessInstance;
	username: string;
	onNextTask: (userTaskKey: string) => void;
	onNoWork: () => void;
}) {
	const queryClient = useQueryClient();
	const {mutate, isPending} = useMutation({
		mutationFn: async (completedTaskKey: string): Promise<string | null> => {
			const deadline = Date.now() + MAX_WAIT;

			while (Date.now() < deadline) {
				const {items} = await queryClient.fetchQuery({
					...queries.queryUserTasksPage(getOpenCaseUserTasksRequestBody(processInstance)),
					staleTime: 0,
				});
				const caseTasks = items.filter((task) => isTaskOfCase(task, processInstance.processInstanceKey));
				const nextTask = pickNextTask(caseTasks, completedTaskKey, username);

				if (nextTask !== undefined) {
					return nextTask.userTaskKey;
				}

				await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL));
			}

			return null;
		},
		onSuccess: (userTaskKey) => (userTaskKey === null ? onNoWork() : onNextTask(userTaskKey)),
	});

	return {isSearchingNextWork: isPending, searchNextWork: mutate};
}

export {useNextWork};
