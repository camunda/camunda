/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'i18next';
import {assign, fromPromise, setup, type SnapshotFrom} from 'xstate';
import type {useNavigate} from '@tanstack/react-router';
import {toast} from '@camunda/design-system';
import type {
	CreateProcessInstanceResponseBody,
	ProcessDefinition,
	QueryUserTasksResponseBody,
	UserTask,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {endpoints} from '#/shared/http/endpoints';
import {request, requestErrorSchema} from '#/shared/http/request';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {getClientConfig} from '#/shared/config/getClientConfig';

const HTTP_STATUS_FORBIDDEN = 403;

type StartProcessStatus = 'inactive' | 'active' | 'active-tasks' | 'finished' | 'error';
type StartProcessFailureReason = 'forbidden' | 'generic';
type Navigate = ReturnType<typeof useNavigate>;

type StartProcessMachineInput = {
	navigate: Navigate;
};

type StartProcessMachineContext = StartProcessMachineInput & {
	selectedProcess: ProcessDefinition | null;
	variables: Record<string, unknown> | undefined;
	processInstanceKey: string | null;
	tasks: UserTask[];
	failureReason: StartProcessFailureReason | null;
};

type StartProcessEvent = {
	type: 'process.start';
	process: ProcessDefinition;
	variables?: Record<string, unknown>;
};

type StartProcessStatusTag =
	'status:starting' | 'status:waiting_for_tasks' | 'status:start_succeeded' | 'status:start_failed';

type NotifyOptions = {
	kind: 'success' | 'error';
	title: string;
	subtitle?: string;
	isDismissable?: boolean;
	isActionable?: boolean;
	actionButtonLabel?: string;
	onActionButtonClick?: () => void;
};

function isShadcnRoute() {
	return window.location.pathname.startsWith('/shadcn');
}

function notify(options: NotifyOptions) {
	const {kind, title, subtitle, isDismissable = true, isActionable, actionButtonLabel, onActionButtonClick} = options;

	if (isShadcnRoute()) {
		toast[kind](title, {
			description: subtitle,
			action:
				isActionable === true && actionButtonLabel !== undefined && onActionButtonClick !== undefined
					? {label: actionButtonLabel, onClick: onActionButtonClick}
					: undefined,
			duration: isDismissable ? undefined : Infinity,
		});
		return;
	}

	notificationsStore.displayNotification({
		kind,
		title,
		subtitle,
		isDismissable,
		isActionable,
		actionButtonLabel,
		onActionButtonClick,
	});
}

function getStartProcessFailureReason(error: unknown): StartProcessFailureReason {
	const result = requestErrorSchema.safeParse(error);

	if (
		result.success &&
		result.data.variant === 'failed-response' &&
		result.data.response.status === HTTP_STATUS_FORBIDDEN
	) {
		return 'forbidden';
	}

	return 'generic';
}

const createProcessInstanceLogic = fromPromise<
	CreateProcessInstanceResponseBody,
	{processDefinitionKey: string; tenantId: string | undefined; variables: Record<string, unknown> | undefined}
>(async ({input}) => {
	const {response, error} = await request(endpoints.createProcessInstance(input));

	if (error !== null) {
		throw error;
	}

	return response.json();
});

const queryNewProcessInstanceTasksLogic = fromPromise<QueryUserTasksResponseBody, {processInstanceKey: string}>(
	async ({input, signal}) => {
		const queryRequest = endpoints.queryUserTasks({
			filter: {
				processInstanceKey: input.processInstanceKey,
				state: 'CREATED',
			},
			page: {limit: 10},
		});
		const {response, error} = await request(new Request(queryRequest, {signal}));

		if (error !== null) {
			throw error;
		}

		return response.json();
	},
);

const navigateToTaskLogic = fromPromise<void, {navigate: Navigate; userTaskKey: string}>(async ({input}) => {
	await input.navigate({
		to: isShadcnRoute() ? '/shadcn/tasklist/$userTaskKey' : '/tasklist/$userTaskKey',
		params: {userTaskKey: input.userTaskKey},
		search: {filter: 'all-open', sortBy: 'creation'},
	});
});

const startProcessMachine = setup({
	types: {
		context: {} as StartProcessMachineContext,
		input: {} as StartProcessMachineInput | undefined,
		events: {} as StartProcessEvent,
		tags: {} as StartProcessStatusTag,
	},
	actors: {
		createProcessInstance: createProcessInstanceLogic,
		queryNewProcessInstanceTasks: queryNewProcessInstanceTasksLogic,
		navigateToTask: navigateToTaskLogic,
	},
	guards: {
		hasTasks: (_, params: {tasks: UserTask[]}) => params.tasks.length > 0,
		hasSingleTask: ({context}) => context.tasks.length === 1,
		hasMultipleTasks: ({context}) => context.tasks.length > 1,
	},
	actions: {
		storeStartRequest: assign(({event}) => ({
			selectedProcess: event.process,
			variables: event.variables,
		})),
		closeStartForm: ({context, event}) => {
			if (event.process.hasStartForm) {
				void context.navigate({
					to: isShadcnRoute() ? '/shadcn/tasklist/processes' : '/tasklist/processes',
					search: true,
				});
			}
		},
		storeProcessInstanceKey: assign((_, params: {processInstanceKey: string}) => ({
			processInstanceKey: params.processInstanceKey,
		})),
		storeTasks: assign((_, params: {tasks: UserTask[]}) => ({tasks: params.tasks})),
		clearOperation: assign({
			selectedProcess: null,
			variables: undefined,
			processInstanceKey: null,
			tasks: [],
			failureReason: null,
		}),
		setFailureReason: assign((_, params: {error: unknown}) => ({
			failureReason: getStartProcessFailureReason(params.error),
		})),
		notifySuccess: () => {
			notify({
				kind: 'success',
				title: t('tasklist.processesStartProcessNotificationSuccess'),
				isDismissable: true,
			});
		},
		notifyFailure: ({context}) => {
			const process = context.selectedProcess;

			if (process === null) {
				return;
			}

			if (context.failureReason === 'forbidden') {
				notify({
					kind: 'error',
					title: t('tasklist.processesStartProcessFailed'),
					subtitle: t('tasklist.taskActionForbidden'),
					isDismissable: true,
				});
				return;
			}

			notify({
				kind: 'error',
				title:
					getClientConfig().deployment.isMultiTenancyEnabled && process.tenantId === undefined
						? t('tasklist.processesStartProcessFailedMissingTenant')
						: t('tasklist.processesStartProcessFailed'),
				subtitle: process.name ?? process.processDefinitionId,
				isDismissable: false,
			});
		},
		notifyNewTasks: ({context}) => {
			context.tasks.forEach(({elementId, name, processDefinitionId, processName, userTaskKey}) => {
				notify({
					kind: 'success',
					title: t('tasklist.processesNewTaskNotification', {
						processName: processName ?? processDefinitionId,
						taskName: name ?? elementId,
					}),
					isActionable: true,
					actionButtonLabel: t('tasklist.processesNewTaskNotificationAction'),
					onActionButtonClick: () => {
						void context.navigate({
							to: isShadcnRoute() ? '/shadcn/tasklist/$userTaskKey' : '/tasklist/$userTaskKey',
							params: {userTaskKey},
							search: {filter: 'all-open', sortBy: 'creation'},
						});
					},
				});
			});
		},
	},
	delays: {
		STATUS_RESET_DELAY: 500,
		POLL_DELAY: 1000,
		TASKS_TIMEOUT: 15000,
	},
}).createMachine({
	id: 'startProcess',
	context: ({input}) => ({
		navigate: input?.navigate ?? (async () => undefined),
		selectedProcess: null,
		variables: undefined,
		processInstanceKey: null,
		tasks: [],
		failureReason: null,
	}),
	initial: 'Idle',
	states: {
		Idle: {
			on: {
				'process.start': {
					target: 'Starting',
					actions: ['storeStartRequest', 'closeStartForm'],
				},
			},
		},
		Starting: {
			tags: 'status:starting',
			invoke: {
				src: 'createProcessInstance',
				input: ({context}) => ({
					processDefinitionKey: context.selectedProcess?.processDefinitionKey ?? '',
					tenantId: context.selectedProcess?.tenantId,
					variables: context.variables,
				}),
				onDone: {
					target: 'WaitingForTasks',
					actions: [
						{
							type: 'storeProcessInstanceKey',
							params: ({event}) => ({processInstanceKey: event.output.processInstanceKey}),
						},
						'notifySuccess',
					],
				},
				onError: {
					target: 'Failed',
					actions: {type: 'setFailureReason', params: ({event}) => ({error: event.error})},
				},
			},
		},
		WaitingForTasks: {
			tags: 'status:waiting_for_tasks',
			after: {
				TASKS_TIMEOUT: {target: 'Succeeded'},
			},
			initial: 'Fetching',
			states: {
				Fetching: {
					invoke: {
						src: 'queryNewProcessInstanceTasks',
						input: ({context}) => ({processInstanceKey: context.processInstanceKey ?? ''}),
						onDone: [
							{
								guard: {
									type: 'hasTasks',
									params: ({event}) => ({tasks: event.output.items}),
								},
								target: '#startProcess.HandlingTasks',
								actions: {
									type: 'storeTasks',
									params: ({event}) => ({tasks: event.output.items}),
								},
							},
							{target: 'Waiting'},
						],
						onError: {target: 'Waiting'},
					},
				},
				Waiting: {
					after: {
						POLL_DELAY: {target: 'Fetching'},
					},
				},
			},
		},
		HandlingTasks: {
			tags: 'status:waiting_for_tasks',
			always: [
				{guard: 'hasSingleTask', target: 'RedirectingToTask'},
				{guard: 'hasMultipleTasks', target: 'NotifyingTasks'},
				{target: 'Succeeded'},
			],
		},
		RedirectingToTask: {
			tags: 'status:waiting_for_tasks',
			invoke: {
				src: 'navigateToTask',
				input: ({context}) => ({
					navigate: context.navigate,
					userTaskKey: context.tasks[0]?.userTaskKey ?? '',
				}),
				onDone: {target: 'Succeeded'},
				onError: {target: 'Succeeded'},
			},
		},
		NotifyingTasks: {
			tags: 'status:waiting_for_tasks',
			entry: 'notifyNewTasks',
			always: {target: 'Succeeded'},
		},
		Succeeded: {
			tags: 'status:start_succeeded',
			after: {
				STATUS_RESET_DELAY: {target: 'Idle', actions: 'clearOperation'},
			},
		},
		Failed: {
			tags: 'status:start_failed',
			after: {
				STATUS_RESET_DELAY: {
					target: 'Idle',
					actions: ['notifyFailure', 'clearOperation'],
				},
			},
		},
	},
});

function deriveStartProcessStatus(snapshot: SnapshotFrom<typeof startProcessMachine>): StartProcessStatus {
	if (snapshot.hasTag('status:starting')) {
		return 'active';
	}

	if (snapshot.hasTag('status:waiting_for_tasks')) {
		return 'active-tasks';
	}

	if (snapshot.hasTag('status:start_succeeded')) {
		return 'finished';
	}

	if (snapshot.hasTag('status:start_failed')) {
		return 'error';
	}

	return 'inactive';
}

export {deriveStartProcessStatus, startProcessMachine};
export type {StartProcessStatus};
