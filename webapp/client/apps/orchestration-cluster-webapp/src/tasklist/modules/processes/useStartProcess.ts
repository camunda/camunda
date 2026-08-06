/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useActorRef, useSelector} from '@xstate/react';
import {t} from 'i18next';
import {useCallback} from 'react';
import {assign, fromPromise, setup, type SnapshotFrom} from 'xstate';
import type {CreateProcessInstanceResponseBody, ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.10';
import {endpoints} from '#/shared/http/endpoints';
import {request, requestErrorSchema} from '#/shared/http/request';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {getClientConfig} from '#/shared/config/getClientConfig';

const HTTP_STATUS_FORBIDDEN = 403;

type StartProcessStatus = 'inactive' | 'active' | 'finished' | 'error';
type StartProcessFailureReason = 'forbidden' | 'generic';

type StartProcessContext = {
	selectedProcess: ProcessDefinition | null;
	selectedTenantId: string | undefined;
	failureReason: StartProcessFailureReason | null;
};

type StartProcessEvent = {
	type: 'process.start';
	process: ProcessDefinition;
	tenantId: string | undefined;
};

type StartProcessStatusTag = 'status:starting' | 'status:start_succeeded' | 'status:start_failed';

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
	{processDefinitionKey: string; tenantId: string | undefined}
>(async ({input}) => {
	const {response, error} = await request(endpoints.createProcessInstance(input));

	if (error !== null) {
		throw error;
	}

	return response.json();
});

const startProcessMachine = setup({
	types: {
		context: {} as StartProcessContext,
		events: {} as StartProcessEvent,
		tags: {} as StartProcessStatusTag,
	},
	actors: {
		createProcessInstance: createProcessInstanceLogic,
	},
	guards: {
		doesNotRequireStartForm: ({event}) => !event.process.hasStartForm,
	},
	actions: {
		selectProcess: assign(({event}) => ({
			selectedProcess: event.process,
			selectedTenantId: event.tenantId,
		})),
		clearSelectedProcess: assign({
			selectedProcess: null,
			selectedTenantId: undefined,
			failureReason: null,
		}),
		setFailureReason: assign((_, params: {error: unknown}) => ({
			failureReason: getStartProcessFailureReason(params.error),
		})),
		notifySuccess: () => {
			notificationsStore.displayNotification({
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
				notificationsStore.displayNotification({
					kind: 'error',
					title: t('tasklist.processesStartProcessFailed'),
					subtitle: t('tasklist.taskActionForbidden'),
					isDismissable: true,
				});
				return;
			}

			notificationsStore.displayNotification({
				kind: 'error',
				title:
					getClientConfig().deployment.isMultiTenancyEnabled && context.selectedTenantId === undefined
						? t('tasklist.processesStartProcessFailedMissingTenant')
						: t('tasklist.processesStartProcessFailed'),
				subtitle: process.name ?? process.processDefinitionId,
				isDismissable: false,
			});
		},
	},
	delays: {
		STATUS_RESET_DELAY: 500,
	},
}).createMachine({
	id: 'startProcess',
	context: {
		selectedProcess: null,
		selectedTenantId: undefined,
		failureReason: null,
	},
	initial: 'Idle',
	states: {
		Idle: {
			on: {
				'process.start': {
					guard: 'doesNotRequireStartForm',
					target: 'Starting',
					actions: 'selectProcess',
				},
			},
		},
		Starting: {
			tags: 'status:starting',
			invoke: {
				src: 'createProcessInstance',
				input: ({context}) => ({
					processDefinitionKey: context.selectedProcess?.processDefinitionKey ?? '',
					tenantId: context.selectedTenantId,
				}),
				onDone: {target: 'Succeeded'},
				onError: {
					target: 'Failed',
					actions: {
						type: 'setFailureReason',
						params: ({event}) => ({error: event.error}),
					},
				},
			},
		},
		Succeeded: {
			tags: 'status:start_succeeded',
			entry: 'notifySuccess',
			after: {
				STATUS_RESET_DELAY: {target: 'Idle', actions: 'clearSelectedProcess'},
			},
		},
		Failed: {
			tags: 'status:start_failed',
			after: {
				STATUS_RESET_DELAY: {
					target: 'Idle',
					actions: ['notifyFailure', 'clearSelectedProcess'],
				},
			},
		},
	},
});

function deriveStartProcessStatus(snapshot: SnapshotFrom<typeof startProcessMachine>): StartProcessStatus {
	if (snapshot.hasTag('status:starting')) {
		return 'active';
	}

	if (snapshot.hasTag('status:start_succeeded')) {
		return 'finished';
	}

	if (snapshot.hasTag('status:start_failed')) {
		return 'error';
	}

	return 'inactive';
}

function useStartProcess() {
	const actorRef = useActorRef(startProcessMachine);
	const status = useSelector(actorRef, deriveStartProcessStatus);
	const selectedProcessDefinitionKey = useSelector(
		actorRef,
		(snapshot) => snapshot.context.selectedProcess?.processDefinitionKey ?? null,
	);
	const startProcess = useCallback(
		(process: ProcessDefinition, tenantId: string | undefined) => {
			actorRef.send({type: 'process.start', process, tenantId});
		},
		[actorRef],
	);

	return {
		status,
		selectedProcessDefinitionKey,
		isBusy: status !== 'inactive',
		startProcess,
	};
}

export {startProcessMachine, useStartProcess};
export type {StartProcessStatus};
