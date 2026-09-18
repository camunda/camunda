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
	| 'RESOLVE_INCIDENT'
	| 'CANCEL_PROCESS_INSTANCE'
	| 'DELETE_PROCESS_INSTANCE'
	| 'SUSPEND_PROCESS_INSTANCE'
	| 'RESUME_PROCESS_INSTANCE'
>;

// Listed as "still going" rather than "done" so a state the API adds later ends the wait instead
// of spinning on it. FAILED and SUSPENDED both end it: the row then reports what happened through
// the operation-state column rather than leaving a spinner up forever.
const IN_PROGRESS_BATCH_OPERATION_STATES: BatchOperation['state'][] = ['CREATED', 'ACTIVE'];
const TERMINAL_PROCESS_INSTANCE_STATES: ProcessInstance['state'][] = ['COMPLETED', 'TERMINATED'];

// A 403 on these means "you can't do this", not "something broke" — legacy shows the same
// permission warning for all three rather than a generic error. Cancel and Delete are
// deliberately excluded, not missing: legacy's own delete-instance mutation test asserts a plain
// error propagates on 403 with no special-cased warning, so a generic error here matches legacy.
const PERMISSION_WARNING_OPERATIONS: OperationType[] = [
	'RESOLVE_INCIDENT',
	'SUSPEND_PROCESS_INSTANCE',
	'RESUME_PROCESS_INSTANCE',
];

// Matches legacy's `useChangeProcessInstanceState` bound for suspend/resume specifically: enough
// attempts for secondary-storage indexing lag, finite so a state that's never reached (e.g. the
// instance was canceled elsewhere while this command was in flight) still surfaces as a failure
// instead of spinning forever. Cancel keeps the unbounded wait below — legacy's own
// `useCancelProcessInstance` never gives up either, since canceling a large instance tree can
// legitimately take longer than this bound.
const SUSPEND_RESUME_POLL_RETRY = {retry: 30, retryDelay: 1000};

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
	await waitForInstanceState(queryClient, processInstanceKey, 'finish', (state) =>
		TERMINAL_PROCESS_INSTANCE_STATES.includes(state),
	);
}

/**
 * Suspend/resume also return 204 with no body, as legacy does. A resumed instance may report
 * either ACTIVE or an already-finished state depending on timing, so "resumed" means "left
 * SUSPENDED" rather than a single exact state, matching legacy's `useChangeProcessInstanceState`.
 * `retryConfig` defaults to Cancel's unbounded wait; suspend/resume pass the bounded one above.
 */
async function waitForInstanceState(
	queryClient: QueryClient,
	processInstanceKey: string,
	label: string,
	hasReachedExpectedState: (state: ProcessInstance['state']) => boolean,
	retryConfig: {retry: number | true; retryDelay?: number} = {retry: true},
) {
	await queryClient.fetchQuery({
		queryKey: ['processInstanceState', processInstanceKey, label] as const,
		queryFn: async (): Promise<ProcessInstance> => {
			const {response, error} = await request(endpoints.getProcessInstance(processInstanceKey));
			if (error !== null) {
				throw mapQueryError(error);
			}
			const processInstance: ProcessInstance = await response.json();
			if (!hasReachedExpectedState(processInstance.state)) {
				throw new Error(`process instance has not reached the expected state (${label})`);
			}
			return processInstance;
		},
		...retryConfig,
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
		const isForbiddenOperation =
			PERMISSION_WARNING_OPERATIONS.includes(operationType) && error instanceof ForbiddenError;
		notificationsStore.displayNotification({
			kind: isForbiddenOperation ? 'warning' : 'error',
			title: isForbiddenOperation ? t('operate.processes.instancesTable.operations.forbiddenTitle') : errorTitle,
			subtitle: isForbiddenOperation
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

	const suspend = useMutation({
		mutationFn: async () => {
			const {error} = await request(endpoints.suspendProcessInstance(processInstanceKey));
			if (error !== null) {
				throw mapQueryError(error);
			}
			await waitForInstanceState(
				queryClient,
				processInstanceKey,
				'suspend',
				(state) => state === 'SUSPENDED',
				SUSPEND_RESUME_POLL_RETRY,
			);
		},
		onSuccess: invalidateProcessInstances,
		onError: (error) =>
			showOperationError(
				'SUSPEND_PROCESS_INSTANCE',
				error,
				t('operate.processes.instancesTable.operations.suspendFailed'),
			),
	});

	const resume = useMutation({
		mutationFn: async () => {
			const {error} = await request(endpoints.resumeProcessInstance(processInstanceKey));
			if (error !== null) {
				throw mapQueryError(error);
			}
			await waitForInstanceState(
				queryClient,
				processInstanceKey,
				'resume',
				(state) => state !== 'SUSPENDED',
				SUSPEND_RESUME_POLL_RETRY,
			);
		},
		onSuccess: invalidateProcessInstances,
		onError: (error) =>
			showOperationError(
				'RESUME_PROCESS_INSTANCE',
				error,
				t('operate.processes.instancesTable.operations.resumeFailed'),
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
	if (suspend.isPending) {
		pendingOperations.add('SUSPEND_PROCESS_INSTANCE');
	}
	if (resume.isPending) {
		pendingOperations.add('RESUME_PROCESS_INSTANCE');
	}

	return {
		pendingOperations,
		resolveIncidents: resolveIncidents.mutate,
		cancel: cancel.mutate,
		remove: remove.mutate,
		suspend: suspend.mutate,
		resume: resume.mutate,
	};
}

export {useProcessInstanceOperations};
export type {OperationType};
