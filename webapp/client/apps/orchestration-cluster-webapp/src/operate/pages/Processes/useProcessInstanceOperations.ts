/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, useQueryClient, type QueryClient} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import type {BatchOperation, BatchOperationType, ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.10';
import {ForbiddenError} from '#/shared/errors';
import {request, requestErrorSchema} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {endpoints} from '#/shared/http/endpoints';
import {notificationsStore} from '#/shared/notifications/notifications.store';

type OperationType = Extract<
	BatchOperationType,
	'RESOLVE_INCIDENT' | 'CANCEL_PROCESS_INSTANCE' | 'DELETE_PROCESS_INSTANCE'
>;

// Listed as "still going" rather than "done" so a state the API adds later ends the wait instead
// of spinning on it. FAILED and SUSPENDED both end it: the row then reports what happened through
// the operation-state column rather than leaving a spinner up forever.
const IN_PROGRESS_BATCH_OPERATION_STATES: BatchOperation['state'][] = ['CREATED', 'ACTIVE'];
const TERMINAL_PROCESS_INSTANCE_STATES: ProcessInstance['state'][] = ['COMPLETED', 'TERMINATED'];

function getOperationErrorSubtitle(error: unknown): string | undefined {
	if (error instanceof Error) {
		return error.message;
	}

	const requestError = requestErrorSchema.safeParse(error);
	if (!requestError.success) {
		return undefined;
	}

	return requestError.data.variant === 'network-error'
		? requestError.data.networkError.message
		: requestError.data.response.statusText || undefined;
}

/**
 * Waits for the batch operation a command created to leave its transitional state. The instances
 * list is only worth refetching once the operation has been applied — invalidating on the 202
 * would just re-read the unchanged instance.
 */
async function waitForBatchOperation(queryClient: QueryClient, batchOperationKey: string) {
	await queryClient.fetchQuery({
		queryKey: ['batchOperation', batchOperationKey] as const,
		queryFn: async (): Promise<BatchOperation> => {
			const {response, error} = await request(endpoints.getBatchOperation({batchOperationKey}));
			if (error !== null) {
				throw mapQueryError(error);
			}
			const batchOperation: BatchOperation = await response.json();
			if (IN_PROGRESS_BATCH_OPERATION_STATES.includes(batchOperation.state)) {
				throw new Error('batch operation is still running');
			}
			return batchOperation;
		},
		retry: true,
	});
}

/**
 * Cancellation returns 204 with no body — there is no batch operation to follow — so completion is
 * observed on the instance itself, as legacy does.
 */
async function waitForInstanceToFinish(queryClient: QueryClient, processInstanceKey: string) {
	await queryClient.fetchQuery({
		queryKey: ['processInstanceState', processInstanceKey] as const,
		queryFn: async (): Promise<ProcessInstance> => {
			const {response, error} = await request(endpoints.getProcessInstance(processInstanceKey));
			if (error !== null) {
				throw mapQueryError(error);
			}
			const processInstance: ProcessInstance = await response.json();
			if (!TERMINAL_PROCESS_INSTANCE_STATES.includes(processInstance.state)) {
				throw new Error('process instance is still running');
			}
			return processInstance;
		},
		retry: true,
	});
}

/**
 * Row-level process instance commands. Each sends its command, waits for it to take effect, then
 * invalidates the instances list. Delete is the exception: the instance is gone rather than
 * changed, so there is nothing to wait on and the list refreshes immediately — matching legacy's
 * `shouldSkipResultCheck` default per operation.
 */
function useProcessInstanceOperations(processInstanceKey: string) {
	const {t} = useTranslation();
	const queryClient = useQueryClient();

	const invalidateProcessInstances = () => queryClient.invalidateQueries({queryKey: ['processInstances']});

	const showOperationError = (operationType: OperationType, error: unknown, errorTitle: string) => {
		const isForbiddenIncidentRetry = operationType === 'RESOLVE_INCIDENT' && error instanceof ForbiddenError;
		notificationsStore.displayNotification({
			kind: isForbiddenIncidentRetry ? 'warning' : 'error',
			title: isForbiddenIncidentRetry ? t('operate.processes.instancesTable.operations.forbiddenTitle') : errorTitle,
			subtitle: isForbiddenIncidentRetry
				? t('operate.processes.instancesTable.operations.forbiddenSubtitle')
				: getOperationErrorSubtitle(error),
			isDismissable: true,
		});
	};

	const resolveIncidents = useMutation({
		mutationFn: async () => {
			const {response, error} = await request(endpoints.resolveProcessInstanceIncidents(processInstanceKey));
			if (error !== null) {
				throw mapQueryError(error);
			}
			const {batchOperationKey} = (await response.json()) as {batchOperationKey: string};
			await waitForBatchOperation(queryClient, batchOperationKey);
		},
		onSuccess: invalidateProcessInstances,
		onError: (error) =>
			showOperationError(
				'RESOLVE_INCIDENT',
				error,
				t('operate.processes.instancesTable.operations.resolveIncidentsFailed'),
			),
	});

	const cancel = useMutation({
		mutationFn: async () => {
			const {error} = await request(endpoints.cancelProcessInstance(processInstanceKey));
			if (error !== null) {
				throw mapQueryError(error);
			}
			await waitForInstanceToFinish(queryClient, processInstanceKey);
		},
		onSuccess: invalidateProcessInstances,
		onError: (error) =>
			showOperationError(
				'CANCEL_PROCESS_INSTANCE',
				error,
				t('operate.processes.instancesTable.operations.cancelFailed'),
			),
	});

	const remove = useMutation({
		mutationFn: async () => {
			const {error} = await request(endpoints.deleteProcessInstance(processInstanceKey));
			if (error !== null) {
				throw mapQueryError(error);
			}
			notificationsStore.displayNotification({
				kind: 'info',
				title: t('operate.processes.instancesTable.operations.deleteScheduled'),
				isDismissable: true,
			});
		},
		onSuccess: invalidateProcessInstances,
		onError: (error) =>
			showOperationError(
				'DELETE_PROCESS_INSTANCE',
				error,
				t('operate.processes.instancesTable.operations.deleteFailed'),
			),
	});

	const pendingOperations = new Set<OperationType>();
	if (resolveIncidents.isPending) {
		pendingOperations.add('RESOLVE_INCIDENT');
	}
	if (cancel.isPending) {
		pendingOperations.add('CANCEL_PROCESS_INSTANCE');
	}
	if (remove.isPending) {
		pendingOperations.add('DELETE_PROCESS_INSTANCE');
	}

	return {
		pendingOperations,
		resolveIncidents: resolveIncidents.mutate,
		cancel: cancel.mutate,
		remove: remove.mutate,
	};
}

export {useProcessInstanceOperations};
export type {OperationType};
