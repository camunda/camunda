/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {fromPromise, setup} from 'xstate';
import type {QueryClient} from '@tanstack/react-query';
import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {t} from 'i18next';
import {endpoints} from '#/shared/http/endpoints';
import {request, requestErrorSchema} from '#/shared/http/request';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {handleOperationError} from '#/operate/shared/utils/handleOperationError';
import {processInstanceQuery} from '#/operate/pages/ProcessInstance/processInstance.queries';

type HeaderAction = 'retry' | 'cancel' | 'delete' | 'suspend' | 'resume';
type Input = {
	action: HeaderAction;
	processInstanceKey: string;
	queryClient: QueryClient;
	onDeleted: () => void;
};

const REQUESTS = {
	retry: endpoints.resolveProcessInstanceIncidents,
	cancel: endpoints.cancelProcessInstance,
	delete: endpoints.deleteProcessInstance,
	suspend: endpoints.suspendProcessInstance,
	resume: endpoints.resumeProcessInstance,
};

function notifyFailure(action: HeaderAction, error: unknown) {
	const parsed = requestErrorSchema.safeParse(error);
	const response = parsed.success ? parsed.data.response : null;
	if (action === 'retry') {
		handleOperationError(response?.status);
		return;
	}
	const isForbidden = (action === 'suspend' || action === 'resume') && response?.status === 403;
	const titles = {
		cancel: t('operate.processes.instancesTable.operations.cancelFailed'),
		delete: t('operate.processes.instancesTable.operations.deleteFailed'),
		suspend: t('operate.processes.instancesTable.operations.suspendFailed'),
		resume: t('operate.processes.instancesTable.operations.resumeFailed'),
	};
	notificationsStore.displayNotification({
		kind: isForbidden ? 'warning' : 'error',
		title: isForbidden ? t('operate.shared.operations.forbiddenTitle') : titles[action],
		subtitle: isForbidden
			? t('operate.shared.operations.forbiddenSubtitle')
			: error instanceof Error
				? error.message
				: (action === 'cancel' || action === 'delete') && parsed.success && parsed.data.variant === 'network-error'
					? parsed.data.networkError.message
					: response?.statusText,
		isDismissable: true,
	});
}

const processInstanceHeaderOperationMachine = setup({
	types: {
		input: {} as Input,
		context: {} as Input,
		events: {} as {type: 'execute'},
	},
	actors: {
		execute: fromPromise<ProcessInstance | null, Input>(async ({input, signal}) => {
			const {action, processInstanceKey, queryClient} = input;
			const {response, error} = await request(new Request(REQUESTS[action](processInstanceKey), {signal}));
			if (error !== null) {
				throw error;
			}
			if (action === 'retry') {
				await response.json();
			}
			if (action !== 'suspend' && action !== 'resume') {
				return null;
			}
			const expectedState = action === 'suspend' ? 'SUSPENDED' : 'ACTIVE';
			const queryKey = ['processInstanceStateChange', processInstanceKey, action, expectedState] as const;
			const cancelQuery = () => void queryClient.cancelQueries({queryKey, exact: true});
			signal.addEventListener('abort', cancelQuery, {once: true});
			try {
				return await queryClient.fetchQuery({
					queryKey,
					staleTime: 0,
					retry: (failureCount) => !signal.aborted && failureCount < 30,
					retryDelay: 1000,
					queryFn: async ({signal}): Promise<ProcessInstance> => {
						const {response, error} = await request(
							new Request(endpoints.getProcessInstance(processInstanceKey), {signal}),
						);
						if (error !== null) {
							throw error;
						}
						const instance: ProcessInstance = await response.json();
						const hasReachedExpectedState =
							action === 'resume' ? instance.state !== 'SUSPENDED' : instance.state === expectedState;
						if (!hasReachedExpectedState) {
							throw new Error(t('operate.processInstance.operations.stateNotReached', {state: expectedState}));
						}
						return instance;
					},
				});
			} finally {
				signal.removeEventListener('abort', cancelQuery);
			}
		}),
	},
}).createMachine({
	context: ({input}) => input,
	initial: 'idle',
	states: {
		idle: {on: {execute: 'pending'}},
		pending: {
			invoke: {
				src: 'execute',
				input: ({context}) => context,
				onDone: {
					target: 'success',
					actions: ({context, event}) => {
						const {action, processInstanceKey, queryClient, onDeleted} = context;
						if (event.output !== null) {
							queryClient.setQueryData(processInstanceQuery(processInstanceKey).queryKey, event.output);
							queryClient.removeQueries({
								queryKey: [
									'processInstanceStateChange',
									processInstanceKey,
									action,
									action === 'suspend' ? 'SUSPENDED' : 'ACTIVE',
								],
								exact: true,
							});
						}
						void queryClient.invalidateQueries({queryKey: ['processInstances']});
						const titles = {
							retry: t('operate.processInstance.operations.retryScheduled'),
							cancel: t('operate.processInstance.operations.cancelScheduled'),
							delete: t('operate.processes.instancesTable.operations.deleteScheduled'),
							suspend: t('operate.processInstance.operations.suspended'),
							resume: t('operate.processInstance.operations.resumed'),
						};
						notificationsStore.displayNotification({kind: 'info', title: titles[action], isDismissable: true});
						if (action === 'delete') {
							onDeleted();
						}
					},
				},
				onError: {target: 'error', actions: ({context, event}) => notifyFailure(context.action, event.error)},
			},
		},
		success: {after: {2000: 'idle'}, on: {execute: 'pending'}},
		error: {after: {2000: 'idle'}, on: {execute: 'pending'}},
	},
});

export {processInstanceHeaderOperationMachine};
export type {HeaderAction};
