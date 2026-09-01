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
import type {BatchOperation, BatchOperationType} from '@camunda/camunda-api-zod-schemas/8.10';
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
const IN_PROGRESS_STATES: BatchOperation['state'][] = ['CREATED', 'ACTIVE'];

/**
 * Waits for the batch operation the command created to leave its transitional state. The
 * instances list is only worth refetching once the operation has actually been applied —
 * invalidating on the 202 would just re-read the unchanged instance.
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
			if (IN_PROGRESS_STATES.includes(batchOperation.state)) {
				throw new Error('batch operation is still running');
			}
			return batchOperation;
		},
		retry: true,
	});
}

/**
 * Row-level process instance commands. Each POSTs the command, waits for the batch operation it
 * creates to settle, then invalidates the instances list. Delete is the exception: the instance
 * is gone rather than changed, so there is no settled state worth waiting for and the list is
 * refreshed immediately — matching legacy's `shouldSkipResultCheck` default per operation.
 */
function useProcessInstanceOperations(processInstanceKey: string) {
	const {t} = useTranslation();
	const queryClient = useQueryClient();
	const [pendingOperation, setPendingOperation] = useState<OperationType | null>(null);

	const run = useCallback(
		async (
			operationType: OperationType,
			send: () => Promise<{batchOperationKey: string} | null>,
			errorTitle: string,
		) => {
			setPendingOperation(operationType);
			try {
				const accepted = await send();
				if (accepted !== null) {
					await waitForBatchOperation(queryClient, accepted.batchOperationKey);
				}
				await queryClient.invalidateQueries({queryKey: ['processInstances']});
			} catch (error) {
				notificationsStore.displayNotification({
					kind: 'error',
					title: errorTitle,
					subtitle: error instanceof Error ? error.message : undefined,
					isDismissable: true,
				});
			} finally {
				setPendingOperation(null);
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
					return response.json();
				},
				t('operate.processes.instancesTable.operations.resolveIncidentsFailed'),
			),
		[processInstanceKey, run, t],
	);

	const cancel = useCallback(
		() =>
			run(
				'CANCEL_PROCESS_INSTANCE',
				async () => {
					const {response, error} = await request(endpoints.cancelProcessInstance(processInstanceKey));
					if (error !== null) {
						throw mapQueryError(error);
					}
					return response.json();
				},
				t('operate.processes.instancesTable.operations.cancelFailed'),
			),
		[processInstanceKey, run, t],
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
					return null;
				},
				t('operate.processes.instancesTable.operations.deleteFailed'),
			),
		[processInstanceKey, run, t],
	);

	return {pendingOperation, resolveIncidents, cancel, remove};
}

export {useProcessInstanceOperations};
