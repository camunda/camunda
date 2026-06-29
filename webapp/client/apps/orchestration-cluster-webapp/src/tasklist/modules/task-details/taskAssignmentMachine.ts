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

const SUCCESS_RESET_DELAY = 500;
const POLL_BASE_DELAY = 500;
const POLL_MAX_DELAY = 5000;

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

const assignTaskActor = fromPromise<'accepted', {userTaskKey: string; assignee: string}>(async ({input}) => {
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

const unassignTaskActor = fromPromise<void, {userTaskKey: string}>(async ({input}) => {
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

const fetchUserTaskActor = fromPromise<UserTask, {queryClient: QueryClient; userTaskKey: string}>(async ({input}) =>
	input.queryClient.fetchQuery(queries.getUserTask(input.userTaskKey)),
);

const taskAssignmentMachine = setup({
	types: {
		context: {} as MachineContext,
		input: {} as MachineInput,
		events: {} as {type: 'ASSIGN'} | {type: 'UNASSIGN'},
	},
	actors: {
		assignTask: assignTaskActor,
		unassignTask: unassignTaskActor,
		fetchUserTask: fetchUserTaskActor,
	},
	guards: {
		isTimeout: ({event}) => {
			const error = (event as {error?: unknown}).error as AssignmentFailure | undefined;

			return error?.reason === 'timeout';
		},
		assignmentSettled: ({event}) => {
			const task = (event as {output?: UserTask}).output;

			return task?.assignee !== null;
		},
		unassignmentSettled: ({event}) => {
			const task = (event as {output?: UserTask}).output;

			return task?.assignee === null;
		},
		taskNoLongerAssigning: ({event}) => {
			const task = (event as {output?: UserTask}).output;

			return task?.state !== 'ASSIGNING';
		},
		isInitiallyAssigning: ({context}) => context.initialTaskState === 'ASSIGNING' && context.initialAssignee === null,
		isInitiallyUnassigning: ({context}) => context.initialTaskState === 'ASSIGNING' && context.initialAssignee !== null,
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
		commitTask: ({context, event}) => {
			const {queryClient, userTaskKey} = context;
			const task = (event as {output?: UserTask}).output;

			if (task !== undefined) {
				queryClient.setQueryData(queries.getUserTask(userTaskKey).queryKey, task);
			}

			queryClient.invalidateQueries({queryKey: ['userTasks']});
		},
		notifyAssignFailure: ({event}) => {
			const error = (event as {error?: AssignmentFailure}).error;

			notificationsStore.displayNotification({
				kind: 'error',
				title: t('tasklist.taskDetailsTaskAssignmentError'),
				subtitle: error?.reason === 'failed' ? error.subtitle : undefined,
				isDismissable: true,
			});
		},
		notifyUnassignFailure: ({event}) => {
			const error = (event as {error?: AssignmentFailure}).error;

			notificationsStore.displayNotification({
				kind: 'error',
				title: t('tasklist.taskDetailsTaskUnassignmentError'),
				subtitle: error?.reason === 'failed' ? error.subtitle : undefined,
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
		SUCCESS_RESET_DELAY,
		POLL_DELAY: ({context}) => Math.min(POLL_BASE_DELAY * Math.pow(2, context.pollRetryCount), POLL_MAX_DELAY),
	},
}).createMachine({
	id: 'taskAssignment',
	context: ({input}) => ({...input, pollRetryCount: 0}),
	initial: 'idle',
	states: {
		idle: {
			always: [
				{guard: 'isInitiallyAssigning', target: 'awaitingAssignment', actions: 'clearInitialTaskState'},
				{guard: 'isInitiallyUnassigning', target: 'awaitingUnassignment', actions: 'clearInitialTaskState'},
			],
			on: {
				ASSIGN: {target: 'assigning'},
				UNASSIGN: {target: 'unassigning'},
			},
		},

		awaitingAssignment: {
			entry: 'resetRetryCount',
			initial: 'fetching',
			states: {
				fetching: {
					invoke: {
						src: 'fetchUserTask',
						input: ({context}) => ({
							queryClient: context.queryClient,
							userTaskKey: context.userTaskKey,
						}),
						onDone: [
							{
								guard: 'taskNoLongerAssigning',
								target: '#taskAssignment.idle',
								actions: 'commitTask',
							},
							{target: 'waiting', actions: 'incrementRetryCount'},
						],
						onError: {target: 'waiting', actions: 'incrementRetryCount'},
					},
				},
				waiting: {
					after: {
						POLL_DELAY: {target: 'fetching'},
					},
				},
			},
		},

		awaitingUnassignment: {
			entry: 'resetRetryCount',
			initial: 'fetching',
			states: {
				fetching: {
					invoke: {
						src: 'fetchUserTask',
						input: ({context}) => ({
							queryClient: context.queryClient,
							userTaskKey: context.userTaskKey,
						}),
						onDone: [
							{
								guard: 'taskNoLongerAssigning',
								target: '#taskAssignment.idle',
								actions: 'commitTask',
							},
							{target: 'waiting', actions: 'incrementRetryCount'},
						],
						onError: {target: 'waiting', actions: 'incrementRetryCount'},
					},
				},
				waiting: {
					after: {
						POLL_DELAY: {target: 'fetching'},
					},
				},
			},
		},

		assigning: {
			invoke: {
				src: 'assignTask',
				input: ({context}) => ({
					userTaskKey: context.userTaskKey,
					assignee: context.currentUser,
				}),
				onDone: {target: 'pollingAssignment'},
				onError: [
					{
						guard: 'isTimeout',
						target: 'assignmentDelayed',
					},
					{
						target: 'idle',
						actions: 'notifyAssignFailure',
					},
				],
			},
		},

		assignmentDelayed: {
			entry: ['setOptimisticAssigning', 'notifyAssignmentDelayed', 'resetRetryCount'],
			initial: 'fetching',
			states: {
				fetching: {
					invoke: {
						src: 'fetchUserTask',
						input: ({context}) => ({
							queryClient: context.queryClient,
							userTaskKey: context.userTaskKey,
						}),
						onDone: [
							{
								guard: 'taskNoLongerAssigning',
								target: '#taskAssignment.assignmentSucceeded',
								actions: 'commitTask',
							},
							{target: 'waiting', actions: 'incrementRetryCount'},
						],
						onError: {target: 'waiting', actions: 'incrementRetryCount'},
					},
				},
				waiting: {
					after: {
						POLL_DELAY: {target: 'fetching'},
					},
				},
			},
		},

		pollingAssignment: {
			entry: 'resetRetryCount',
			initial: 'fetching',
			states: {
				fetching: {
					invoke: {
						src: 'fetchUserTask',
						input: ({context}) => ({
							queryClient: context.queryClient,
							userTaskKey: context.userTaskKey,
						}),
						onDone: [
							{
								guard: 'assignmentSettled',
								target: '#taskAssignment.assignmentSucceeded',
								actions: 'commitTask',
							},
							{target: 'waiting', actions: 'incrementRetryCount'},
						],
						onError: {target: 'waiting', actions: 'incrementRetryCount'},
					},
				},
				waiting: {
					after: {
						POLL_DELAY: {target: 'fetching'},
					},
				},
			},
		},

		assignmentSucceeded: {
			entry: 'trackAssigned',
			after: {
				SUCCESS_RESET_DELAY: {target: 'idle'},
			},
		},

		unassigning: {
			invoke: {
				src: 'unassignTask',
				input: ({context}) => ({
					userTaskKey: context.userTaskKey,
				}),
				onDone: {target: 'pollingUnassignment'},
				onError: [
					{
						guard: 'isTimeout',
						target: 'unassignmentDelayed',
					},
					{
						target: 'idle',
						actions: 'notifyUnassignFailure',
					},
				],
			},
		},

		unassignmentDelayed: {
			entry: ['setOptimisticUnassigning', 'notifyUnassignmentDelayed', 'trackUnassignmentDelayed', 'resetRetryCount'],
			initial: 'fetching',
			states: {
				fetching: {
					invoke: {
						src: 'fetchUserTask',
						input: ({context}) => ({
							queryClient: context.queryClient,
							userTaskKey: context.userTaskKey,
						}),
						onDone: [
							{
								guard: 'taskNoLongerAssigning',
								target: '#taskAssignment.unassignmentSucceeded',
								actions: 'commitTask',
							},
							{target: 'waiting', actions: 'incrementRetryCount'},
						],
						onError: {target: 'waiting', actions: 'incrementRetryCount'},
					},
				},
				waiting: {
					after: {
						POLL_DELAY: {target: 'fetching'},
					},
				},
			},
		},

		pollingUnassignment: {
			entry: 'resetRetryCount',
			initial: 'fetching',
			states: {
				fetching: {
					invoke: {
						src: 'fetchUserTask',
						input: ({context}) => ({
							queryClient: context.queryClient,
							userTaskKey: context.userTaskKey,
						}),
						onDone: [
							{
								guard: 'unassignmentSettled',
								target: '#taskAssignment.unassignmentSucceeded',
								actions: 'commitTask',
							},
							{target: 'waiting', actions: 'incrementRetryCount'},
						],
						onError: {target: 'waiting', actions: 'incrementRetryCount'},
					},
				},
				waiting: {
					after: {
						POLL_DELAY: {target: 'fetching'},
					},
				},
			},
		},

		unassignmentSucceeded: {
			entry: 'trackUnassigned',
			after: {
				SUCCESS_RESET_DELAY: {target: 'idle'},
			},
		},
	},
});

export {taskAssignmentMachine};
