/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {setup, assign, emit, fromPromise} from 'xstate';
import {t} from 'i18next';
import type {QueryClient} from '@tanstack/react-query';
import type {UserTask} from '@camunda/camunda-api-zod-schemas/8.10';
import {endpoints} from '#/shared/http/endpoints';
import {queries} from '#/shared/http/queries';
import {request, requestErrorSchema} from '#/shared/http/request';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {storeStateLocally} from '#/shared/browser-storage/local-storage';
import {tracking} from '#/shared/tracking';
import {isTaskTimeoutError} from './taskErrorHandling';
import {parseDenialReason} from './parseDenialReason';

type CompletionFailure = {reason: 'timeout'} | {reason: 'failed'; subtitle?: string};

type MachineInput = {
	queryClient: QueryClient;
	userTaskKey: string;
	currentUser: string;
	initialTaskState: UserTask['state'];
	initialAssignee: string | null;
};

type MachineContext = Omit<MachineInput, 'initialTaskState' | 'initialAssignee'> & {
	initialTaskState: UserTask['state'] | null;
	taskState: UserTask['state'];
	assignee: string | null;
	pollRetryCount: number;
};

type TaskCompletionEvent =
	| {type: 'task.complete'}
	| {type: 'task.updated'; taskState: UserTask['state']; assignee: string | null};

type TaskCompletionStatusTag = 'status:completing' | 'status:completion_successful' | 'status:completion_failed';

async function resolveFailureSubtitle(error: unknown): Promise<string | undefined> {
	const parsed = requestErrorSchema.safeParse(error);

	if (parsed.success && parsed.data.variant === 'failed-response') {
		if (parsed.data.response.status === 403) {
			return t('tasklist.taskActionForbidden');
		}

		return parseDenialReason(await parsed.data.response.json(), 'completion');
	}

	return error instanceof Error ? error.message : undefined;
}

const completeTaskLogic = fromPromise<'accepted', {userTaskKey: string}>(async ({input}) => {
	const {error} = await request(
		endpoints.completeTask({
			userTaskKey: input.userTaskKey,
			variables: {},
		}),
	);

	if (error === null) {
		return 'accepted' as const;
	}

	const parsed = requestErrorSchema.safeParse(error);

	if (
		parsed.success &&
		parsed.data.variant === 'failed-response' &&
		isTaskTimeoutError(await parsed.data.response.clone().json())
	) {
		throw {reason: 'timeout'} satisfies CompletionFailure;
	}

	throw {
		reason: 'failed',
		subtitle: await resolveFailureSubtitle(error),
	} satisfies CompletionFailure;
});

const fetchUserTaskLogic = fromPromise<UserTask, {queryClient: QueryClient; userTaskKey: string}>(async ({input}) =>
	input.queryClient.fetchQuery(queries.getUserTask(input.userTaskKey)),
);

const taskCompletionMachine = setup({
	types: {
		context: {} as MachineContext,
		input: {} as MachineInput,
		events: {} as TaskCompletionEvent,
		emitted: {} as {type: 'task.completed'},
		tags: {} as TaskCompletionStatusTag,
	},
	actors: {
		completeTask: completeTaskLogic,
		fetchUserTask: fetchUserTaskLogic,
	},
	guards: {
		isTimeout: (_, params: {error: CompletionFailure | undefined}) => params.error?.reason === 'timeout',
		isTaskCompleted: (_, params: {task: UserTask | undefined}) => params.task?.state === 'COMPLETED',
		isInitiallyCompleting: ({context}) => context.initialTaskState === 'COMPLETING',
		canCompleteTask: ({context}) => context.currentUser === context.assignee && context.taskState === 'CREATED',
	},
	actions: {
		updateTask: assign(({event}) =>
			event.type === 'task.updated'
				? {
						taskState: event.taskState,
						assignee: event.assignee,
					}
				: {},
		),
		setOptimisticCompleting: ({context}) => {
			const {queryClient, userTaskKey} = context;
			const currentTask = queryClient.getQueryData<UserTask>(queries.getUserTask(userTaskKey).queryKey);

			if (currentTask !== undefined) {
				queryClient.setQueryData(queries.getUserTask(userTaskKey).queryKey, {
					...currentTask,
					state: 'COMPLETING' as const,
				});
			}
		},
		notifyCompletionDelayed: () => {
			notificationsStore.displayNotification({
				kind: 'info',
				title: t('tasklist.taskDetailsCompletionDelayInfoTitle'),
				subtitle: t('tasklist.taskDetailsCompletionDelayInfoSubtitle'),
				isDismissable: true,
			});
		},
		trackCompletionDelayed: () => {
			tracking.track({eventName: 'tasklist:task-completion-delayed-notification'});
		},
		commitTask: ({context}, params: {task: UserTask | undefined}) => {
			const {queryClient, userTaskKey} = context;

			if (params.task !== undefined) {
				queryClient.setQueryData(queries.getUserTask(userTaskKey).queryKey, params.task);
			}

			queryClient.invalidateQueries({queryKey: ['userTasks']});
		},
		notifyCompletionSuccess: () => {
			notificationsStore.displayNotification({
				kind: 'success',
				title: t('tasklist.taskCompletedNotification'),
				isDismissable: true,
			});
		},
		storeCompletionLocally: () => {
			storeStateLocally('tasklist.hasCompletedTask', true);
		},
		notifyCompletionFailure: (_, params: {error: CompletionFailure | undefined}) => {
			notificationsStore.displayNotification({
				kind: 'error',
				title: t('tasklist.taskCouldNotBeCompletedNotification'),
				subtitle: params.error?.reason === 'failed' ? params.error.subtitle : undefined,
				isDismissable: true,
			});
		},
		trackCompletionFailure: () => {
			tracking.track({eventName: 'tasklist:task-completion-rejected-notification'});
		},
		complete: emit({type: 'task.completed'}),
		resetRetryCount: assign({pollRetryCount: 0}),
		incrementRetryCount: assign({pollRetryCount: ({context}) => context.pollRetryCount + 1}),
		clearInitialTaskState: assign({initialTaskState: null}),
	},
	delays: {
		SUCCESS_RESET_DELAY: 500,
		FAILURE_RESET_DELAY: 500,
		POLL_DELAY: ({context}) => {
			const POLL_BASE_DELAY = 500;
			const POLL_MAX_DELAY = 5000;

			return Math.min(POLL_BASE_DELAY * Math.pow(2, context.pollRetryCount), POLL_MAX_DELAY);
		},
	},
}).createMachine({
	id: 'taskCompletion',
	context: ({input}) => ({
		queryClient: input.queryClient,
		userTaskKey: input.userTaskKey,
		currentUser: input.currentUser,
		initialTaskState: input.initialTaskState,
		taskState: input.initialTaskState,
		assignee: input.initialAssignee,
		pollRetryCount: 0,
	}),
	initial: 'Idle',
	states: {
		Idle: {
			always: [{guard: 'isInitiallyCompleting', target: 'AwaitingCompletion', actions: 'clearInitialTaskState'}],
			on: {
				'task.complete': {guard: 'canCompleteTask', target: 'Completing'},
				'task.updated': {actions: 'updateTask'},
			},
		},

		Completing: {
			tags: 'status:completing',
			invoke: {
				src: 'completeTask',
				input: ({context}) => ({userTaskKey: context.userTaskKey}),
				onDone: {target: 'PollingCompletion'},
				onError: [
					{
						guard: {
							type: 'isTimeout',
							params: ({event}) => ({error: event.error as CompletionFailure | undefined}),
						},
						target: 'CompletionDelayed',
					},
					{
						target: 'CompletionFailed',
						actions: [
							{
								type: 'notifyCompletionFailure',
								params: ({event}) => ({error: event.error as CompletionFailure | undefined}),
							},
							'trackCompletionFailure',
						],
					},
				],
			},
		},

		CompletionDelayed: {
			entry: ['setOptimisticCompleting', 'notifyCompletionDelayed', 'trackCompletionDelayed'],
			tags: 'status:completing',
			always: {target: 'PollingCompletion'},
		},

		AwaitingCompletion: {
			tags: 'status:completing',
			always: {target: 'PollingCompletion'},
		},

		PollingCompletion: {
			entry: 'resetRetryCount',
			tags: 'status:completing',
			initial: 'Fetching',
			states: {
				Fetching: {
					invoke: {
						src: 'fetchUserTask',
						input: ({context}) => ({queryClient: context.queryClient, userTaskKey: context.userTaskKey}),
						onDone: [
							{
								guard: {type: 'isTaskCompleted', params: ({event}) => ({task: event.output})},
								target: '#taskCompletion.CompletionSucceeded',
								actions: {type: 'commitTask', params: ({event}) => ({task: event.output})},
							},
							{target: 'Waiting', actions: 'incrementRetryCount'},
						],
						onError: {target: 'Waiting', actions: 'incrementRetryCount'},
					},
				},
				Waiting: {
					after: {
						POLL_DELAY: {target: 'Fetching'},
					},
				},
			},
		},

		CompletionSucceeded: {
			entry: ['notifyCompletionSuccess', 'storeCompletionLocally'],
			tags: 'status:completion_successful',
			after: {
				SUCCESS_RESET_DELAY: {target: 'Idle', actions: 'complete'},
			},
		},

		CompletionFailed: {
			tags: 'status:completion_failed',
			after: {
				FAILURE_RESET_DELAY: {target: 'Idle'},
			},
		},
	},
});

export {taskCompletionMachine};
