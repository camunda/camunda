/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {setup, assign, fromPromise} from 'xstate';
import {t} from 'i18next';
import type {QueryClient} from '@tanstack/react-query';
import type {UserTask} from '@camunda/camunda-api-zod-schemas/8.10';
import {queries} from '#/shared/http/queries';
import {request, requestErrorSchema} from '#/shared/http/request';
import {endpoints} from '#/shared/http/endpoints';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {tracking} from '#/shared/tracking';
import {isTaskTimeoutError} from './taskErrorHandling';
import {parseDenialReason} from './parseDenialReason';

type AssignmentFailure = {reason: 'timeout'} | {reason: 'failed'; subtitle?: string};

type MachineInput = {
	queryClient: QueryClient;
	userTaskKey: string;
	currentUser: string;
	initialTaskState: UserTask['state'];
	initialAssignee: string | null;
};

type MachineContext = {
	queryClient: QueryClient;
	userTaskKey: string;
	currentUser: string;
	initialTaskState: UserTask['state'] | null;
	initialAssignee: string | null;
	pollRetryCount: number;
};

type TaskAssignmentStatusTag =
	| 'status:assigning'
	| 'status:unassigning'
	| 'status:assignment_successful'
	| 'status:unassignment_successful';

async function resolveFailureSubtitle(
	error: unknown,
	type: 'assignment' | 'unassignment',
): Promise<string | undefined> {
	const parsed = requestErrorSchema.safeParse(error);

	if (parsed.success && parsed.data.variant === 'failed-response') {
		if (parsed.data.response.status === 403) {
			return t('tasklist.taskActionForbidden');
		}

		return parseDenialReason(await parsed.data.response.json(), type);
	}

	return undefined;
}

const assignTaskLogic = fromPromise<'accepted', {userTaskKey: string; assignee: string}>(async ({input}) => {
	const {error} = await request(
		endpoints.assignTask({
			userTaskKey: input.userTaskKey,
			assignee: input.assignee,
			allowOverride: false,
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
		throw {reason: 'timeout'} satisfies AssignmentFailure;
	}

	throw {
		reason: 'failed',
		subtitle: await resolveFailureSubtitle(error, 'assignment'),
	} satisfies AssignmentFailure;
});

const unassignTaskLogic = fromPromise<void, {userTaskKey: string}>(async ({input}) => {
	const {error} = await request(endpoints.unassignTask({userTaskKey: input.userTaskKey}));

	if (error === null) {
		return;
	}

	const parsed = requestErrorSchema.safeParse(error);

	if (
		parsed.success &&
		parsed.data.variant === 'failed-response' &&
		isTaskTimeoutError(await parsed.data.response.clone().json())
	) {
		throw {reason: 'timeout'} satisfies AssignmentFailure;
	}

	throw {
		reason: 'failed',
		subtitle: await resolveFailureSubtitle(error, 'unassignment'),
	} satisfies AssignmentFailure;
});

const fetchUserTaskLogic = fromPromise<UserTask, {queryClient: QueryClient; userTaskKey: string}>(async ({input}) =>
	input.queryClient.fetchQuery(queries.getUserTask(input.userTaskKey)),
);

const taskAssignmentMachine = setup({
	types: {
		context: {} as MachineContext,
		input: {} as MachineInput,
		events: {} as {type: 'task.toggle'; taskState: UserTask['state']; assignee: string | null},
		tags: {} as TaskAssignmentStatusTag,
	},
	actors: {
		assignTask: assignTaskLogic,
		unassignTask: unassignTaskLogic,
		fetchUserTask: fetchUserTaskLogic,
	},
	guards: {
		isTimeout: (_, params: {error: AssignmentFailure | undefined}) => {
			return params.error?.reason === 'timeout';
		},
		assignmentSettled: (_, params: {task: UserTask | undefined}) => {
			return params.task?.assignee !== null;
		},
		unassignmentSettled: (_, params: {task: UserTask | undefined}) => {
			return params.task?.assignee === null;
		},
		taskNoLongerAssigning: (_, params: {task: UserTask | undefined}) => {
			return params.task?.state !== 'ASSIGNING';
		},
		isInitiallyAssigning: ({context}) => context.initialTaskState === 'ASSIGNING' && context.initialAssignee === null,
		isInitiallyUnassigning: ({context}) => context.initialTaskState === 'ASSIGNING' && context.initialAssignee !== null,
		isTaskAssigned: (_, params: {taskState: UserTask['state']; assignee: string | null}) =>
			typeof params.assignee === 'string' && params.taskState !== 'ASSIGNING',
	},
	actions: {
		setOptimisticAssigning: ({context}) => {
			const {queryClient, userTaskKey} = context;
			const currentTask = queryClient.getQueryData<UserTask>(queries.getUserTask(userTaskKey).queryKey);

			if (currentTask !== undefined) {
				queryClient.setQueryData(queries.getUserTask(userTaskKey).queryKey, {
					...currentTask,
					state: 'ASSIGNING' as const,
					assignee: null,
				});
			}
		},
		notifyAssignmentDelayed: () => {
			notificationsStore.displayNotification({
				kind: 'info',
				title: t('tasklist.taskDetailsAssignmentDelayInfoTitle'),
				subtitle: t('tasklist.taskDetailsAssignmentDelayInfoSubtitle'),
				isDismissable: true,
			});
		},
		setOptimisticUnassigning: ({context}) => {
			const {queryClient, userTaskKey} = context;
			const currentTask = queryClient.getQueryData<UserTask>(queries.getUserTask(userTaskKey).queryKey);

			if (currentTask !== undefined) {
				queryClient.setQueryData(queries.getUserTask(userTaskKey).queryKey, {
					...currentTask,
					state: 'ASSIGNING' as const,
				});
			}
		},
		notifyUnassignmentDelayed: () => {
			notificationsStore.displayNotification({
				kind: 'info',
				title: t('tasklist.taskDetailsUnassignmentDelayInfoTitle'),
				subtitle: t('tasklist.taskDetailsUnassignmentDelayInfoSubtitle'),
				isDismissable: true,
			});
		},
		trackUnassignmentDelayed: () => {
			tracking.track({eventName: 'tasklist:task-unassignment-delayed-notification'});
		},
		commitTask: ({context}, params: {task: UserTask | undefined}) => {
			const {queryClient, userTaskKey} = context;

			if (params.task !== undefined) {
				queryClient.setQueryData(queries.getUserTask(userTaskKey).queryKey, params.task);
			}

			queryClient.invalidateQueries({queryKey: ['userTasks']});
		},
		notifyAssignFailure: (_, params: {error: AssignmentFailure | undefined}) => {
			notificationsStore.displayNotification({
				kind: 'error',
				title: t('tasklist.taskDetailsTaskAssignmentError'),
				subtitle: params.error?.reason === 'failed' ? params.error.subtitle : undefined,
				isDismissable: true,
			});
		},
		notifyUnassignFailure: (_, params: {error: AssignmentFailure | undefined}) => {
			notificationsStore.displayNotification({
				kind: 'error',
				title: t('tasklist.taskDetailsTaskUnassignmentError'),
				subtitle: params.error?.reason === 'failed' ? params.error.subtitle : undefined,
				isDismissable: true,
			});
		},
		trackAssigned: () => {
			tracking.track({eventName: 'tasklist:task-assigned'});
		},
		trackUnassigned: () => {
			tracking.track({eventName: 'tasklist:task-unassigned'});
		},
		resetRetryCount: assign({pollRetryCount: 0}),
		incrementRetryCount: assign({pollRetryCount: ({context}) => context.pollRetryCount + 1}),
		clearInitialTaskState: assign({initialTaskState: null}),
	},

	delays: {
		SUCCESS_RESET_DELAY: 500,
		POLL_DELAY: ({context}) => {
			const POLL_BASE_DELAY = 500;
			const POLL_MAX_DELAY = 5000;

			return Math.min(POLL_BASE_DELAY * Math.pow(2, context.pollRetryCount), POLL_MAX_DELAY);
		},
	},
}).createMachine({
	id: 'taskAssignment',
	context: ({input}) => ({...input, pollRetryCount: 0}),
	initial: 'Idle',
	states: {
		Idle: {
			always: [
				{guard: 'isInitiallyAssigning', target: 'AwaitingAssignment', actions: 'clearInitialTaskState'},
				{guard: 'isInitiallyUnassigning', target: 'AwaitingUnassignment', actions: 'clearInitialTaskState'},
			],
			on: {
				'task.toggle': [
					{
						guard: {
							type: 'isTaskAssigned',
							params: ({event}) => ({taskState: event.taskState, assignee: event.assignee}),
						},
						target: 'Unassigning',
					},
					{target: 'Assigning'},
				],
			},
		},

		AwaitingAssignment: {
			entry: 'resetRetryCount',
			tags: 'status:assigning',
			initial: 'Fetching',
			states: {
				Fetching: {
					invoke: {
						src: 'fetchUserTask',
						input: ({context}) => ({
							queryClient: context.queryClient,
							userTaskKey: context.userTaskKey,
						}),
						onDone: [
							{
								guard: {
									type: 'taskNoLongerAssigning',
									params: ({event}) => ({task: event.output}),
								},
								target: '#taskAssignment.Idle',
								actions: {
									type: 'commitTask',
									params: ({event}) => ({task: event.output}),
								},
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

		AwaitingUnassignment: {
			entry: 'resetRetryCount',
			tags: 'status:unassigning',
			initial: 'Fetching',
			states: {
				Fetching: {
					invoke: {
						src: 'fetchUserTask',
						input: ({context}) => ({
							queryClient: context.queryClient,
							userTaskKey: context.userTaskKey,
						}),
						onDone: [
							{
								guard: {
									type: 'taskNoLongerAssigning',
									params: ({event}) => ({task: event.output}),
								},
								target: '#taskAssignment.Idle',
								actions: {
									type: 'commitTask',
									params: ({event}) => ({task: event.output}),
								},
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

		Assigning: {
			tags: 'status:assigning',
			invoke: {
				src: 'assignTask',
				input: ({context}) => ({
					userTaskKey: context.userTaskKey,
					assignee: context.currentUser,
				}),
				onDone: {target: 'PollingAssignment'},
				onError: [
					{
						guard: {
							type: 'isTimeout',
							params: ({event}) => ({error: event.error as AssignmentFailure | undefined}),
						},
						target: 'AssignmentDelayed',
					},
					{
						target: 'Idle',
						actions: {
							type: 'notifyAssignFailure',
							params: ({event}) => ({error: event.error as AssignmentFailure | undefined}),
						},
					},
				],
			},
		},

		AssignmentDelayed: {
			entry: ['setOptimisticAssigning', 'notifyAssignmentDelayed', 'resetRetryCount'],
			tags: 'status:assigning',
			initial: 'Fetching',
			states: {
				Fetching: {
					invoke: {
						src: 'fetchUserTask',
						input: ({context}) => ({
							queryClient: context.queryClient,
							userTaskKey: context.userTaskKey,
						}),
						onDone: [
							{
								guard: {
									type: 'taskNoLongerAssigning',
									params: ({event}) => ({task: event.output}),
								},
								target: '#taskAssignment.AssignmentSucceeded',
								actions: {
									type: 'commitTask',
									params: ({event}) => ({task: event.output}),
								},
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

		PollingAssignment: {
			entry: 'resetRetryCount',
			tags: 'status:assigning',
			initial: 'Fetching',
			states: {
				Fetching: {
					invoke: {
						src: 'fetchUserTask',
						input: ({context}) => ({
							queryClient: context.queryClient,
							userTaskKey: context.userTaskKey,
						}),
						onDone: [
							{
								guard: {
									type: 'assignmentSettled',
									params: ({event}) => ({task: event.output}),
								},
								target: '#taskAssignment.AssignmentSucceeded',
								actions: {
									type: 'commitTask',
									params: ({event}) => ({task: event.output}),
								},
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

		AssignmentSucceeded: {
			entry: 'trackAssigned',
			tags: 'status:assignment_successful',
			after: {
				SUCCESS_RESET_DELAY: {target: 'Idle'},
			},
		},

		Unassigning: {
			tags: 'status:unassigning',
			invoke: {
				src: 'unassignTask',
				input: ({context}) => ({
					userTaskKey: context.userTaskKey,
				}),
				onDone: {target: 'PollingUnassignment'},
				onError: [
					{
						guard: {
							type: 'isTimeout',
							params: ({event}) => ({error: event.error as AssignmentFailure | undefined}),
						},
						target: 'UnassignmentDelayed',
					},
					{
						target: 'Idle',
						actions: {
							type: 'notifyUnassignFailure',
							params: ({event}) => ({error: event.error as AssignmentFailure | undefined}),
						},
					},
				],
			},
		},

		UnassignmentDelayed: {
			entry: ['setOptimisticUnassigning', 'notifyUnassignmentDelayed', 'trackUnassignmentDelayed', 'resetRetryCount'],
			tags: 'status:unassigning',
			initial: 'Fetching',
			states: {
				Fetching: {
					invoke: {
						src: 'fetchUserTask',
						input: ({context}) => ({
							queryClient: context.queryClient,
							userTaskKey: context.userTaskKey,
						}),
						onDone: [
							{
								guard: {
									type: 'taskNoLongerAssigning',
									params: ({event}) => ({task: event.output}),
								},
								target: '#taskAssignment.UnassignmentSucceeded',
								actions: {
									type: 'commitTask',
									params: ({event}) => ({task: event.output}),
								},
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

		PollingUnassignment: {
			entry: 'resetRetryCount',
			tags: 'status:unassigning',
			initial: 'Fetching',
			states: {
				Fetching: {
					invoke: {
						src: 'fetchUserTask',
						input: ({context}) => ({
							queryClient: context.queryClient,
							userTaskKey: context.userTaskKey,
						}),
						onDone: [
							{
								guard: {
									type: 'unassignmentSettled',
									params: ({event}) => ({task: event.output}),
								},
								target: '#taskAssignment.UnassignmentSucceeded',
								actions: {
									type: 'commitTask',
									params: ({event}) => ({task: event.output}),
								},
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

		UnassignmentSucceeded: {
			entry: 'trackUnassigned',
			tags: 'status:unassignment_successful',
			after: {
				SUCCESS_RESET_DELAY: {target: 'Idle'},
			},
		},
	},
});

export {taskAssignmentMachine};
