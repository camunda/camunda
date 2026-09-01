/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useState} from 'react';
import {useQueryClient, type QueryClient} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import type {BatchOperation, BatchOperationType, ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.10';
import {request} from '#/shared/http/request';
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
	// A set rather than a single slot: an instance can offer two actions at once, and each button
	// must stay disabled for the life of its own command rather than until any command finishes.
	const [pendingOperations, setPendingOperations] = useState<ReadonlySet<OperationType>>(new Set());

	const run = useCallback(
		async (operationType: OperationType, send: () => Promise<void>, errorTitle: string) => {
			setPendingOperations((current) => new Set(current).add(operationType));
			try {
				await send();
				await queryClient.invalidateQueries({queryKey: ['processInstances']});
			} catch (error) {
				notificationsStore.displayNotification({
					kind: 'error',
					title: errorTitle,
					subtitle: error instanceof Error ? error.message : undefined,
					isDismissable: true,
				});
			} finally {
				setPendingOperations((current) => {
					const next = new Set(current);
					next.delete(operationType);
					return next;
				});
			}
		},
		[queryClient],
	);

	const resolveIncidents = useCallback(
		() =>
			run(
				'RESOLVE_INCIDENT',
				async () => {
					const {response, error} = await request(endpoints.resolveProcessInstanceIncidents(processInstanceKey));
					if (error !== null) {
						throw mapQueryError(error);
					}
					const {batchOperationKey} = (await response.json()) as {batchOperationKey: string};
					await waitForBatchOperation(queryClient, batchOperationKey);
				},
				t('operate.processes.instancesTable.operations.resolveIncidentsFailed'),
			),
		[processInstanceKey, queryClient, run, t],
	);

	const cancel = useCallback(
		() =>
			run(
				'CANCEL_PROCESS_INSTANCE',
				async () => {
					const {error} = await request(endpoints.cancelProcessInstance(processInstanceKey));
					if (error !== null) {
						throw mapQueryError(error);
					}
					await waitForInstanceToFinish(queryClient, processInstanceKey);
				},
				t('operate.processes.instancesTable.operations.cancelFailed'),
			),
		[processInstanceKey, queryClient, run, t],
	);

	const remove = useCallback(
		() =>
			run(
				'DELETE_PROCESS_INSTANCE',
				async () => {
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
				t('operate.processes.instancesTable.operations.deleteFailed'),
			),
		[processInstanceKey, run, t],
	);

	return {pendingOperations, resolveIncidents, cancel, remove};
}

export {useProcessInstanceOperations};
export type {OperationType};
