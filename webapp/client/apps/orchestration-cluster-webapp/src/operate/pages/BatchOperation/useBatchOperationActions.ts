/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {useMutation, useQuery, useQueryClient, type QueryClient} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {useNavigate} from '@tanstack/react-router';
import type {BatchOperation} from '@camunda/camunda-api-zod-schemas/8.10';
import {ForbiddenError} from '#/shared/errors';
import {request, requestErrorSchema} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {endpoints} from '#/shared/http/endpoints';
import {notificationsStore} from '#/shared/notifications/notifications.store';

type BatchOperationAction = 'suspend' | 'resume' | 'cancel';

const KNOWN_ACTIONS: ReadonlyArray<BatchOperationAction> = ['suspend', 'resume', 'cancel'];

const TERMINAL_STATES: ReadonlyArray<BatchOperation['state']> = [
	'COMPLETED',
	'PARTIALLY_COMPLETED',
	'FAILED',
	'CANCELED',
];

const CONVERGED_STATES_BY_ACTION: Record<BatchOperationAction, ReadonlyArray<BatchOperation['state']>> = {
	suspend: ['SUSPENDED', ...TERMINAL_STATES],
	resume: ['ACTIVE', ...TERMINAL_STATES],
	cancel: [...TERMINAL_STATES],
};

const MAX_POLL_ATTEMPTS = 6;
const POLL_RETRY_DELAY_MS = 1000;
const SINGLE_REQUEST_TIMEOUT_MS = 10000;
const MAX_ACTION_LIFECYCLE_MS =
	SINGLE_REQUEST_TIMEOUT_MS +
	MAX_POLL_ATTEMPTS * SINGLE_REQUEST_TIMEOUT_MS +
	(MAX_POLL_ATTEMPTS - 1) * POLL_RETRY_DELAY_MS;
const PENDING_ACTION_PERSISTENCE_EXPIRY_MS = MAX_ACTION_LIFECYCLE_MS + 5000;

class RequestTimeoutError extends Error {
	constructor(timeoutMs: number) {
		super(`request timed out after ${timeoutMs}ms`);
		this.name = 'RequestTimeoutError';
	}
}

function withTimeout<T>(promise: Promise<T>, timeoutMs: number): Promise<T> {
	return new Promise((resolve, reject) => {
		const timer = setTimeout(() => reject(new RequestTimeoutError(timeoutMs)), timeoutMs);
		promise.then(
			(value) => {
				clearTimeout(timer);
				resolve(value);
			},
			(error) => {
				clearTimeout(timer);
				reject(error);
			},
		);
	});
}

function isAbortError(error: unknown): boolean {
	return error instanceof DOMException && error.name === 'AbortError';
}

async function withAbortTimeout<T>(run: (signal: AbortSignal) => Promise<T>, timeoutMs: number): Promise<T> {
	const controller = new AbortController();
	const timer = setTimeout(() => controller.abort(), timeoutMs);
	try {
		return await run(controller.signal);
	} catch (error) {
		if (isAbortError(error)) {
			throw new RequestTimeoutError(timeoutMs);
		}
		throw error;
	} finally {
		clearTimeout(timer);
	}
}

async function requestOrThrow(input: Request): Promise<Response> {
	const {response, error} = await request(input);
	if (error !== null) {
		if (isAbortError(error.networkError)) {
			throw error.networkError;
		}
		throw mapQueryError(error);
	}
	return response;
}

class BatchOperationConvergenceUnknownError extends Error {
	constructor() {
		super('batch operation status could not be confirmed after the action was sent');
		this.name = 'BatchOperationConvergenceUnknownError';
	}
}

class BatchOperationGoneError extends Error {
	constructor() {
		super('batch operation no longer exists');
		this.name = 'BatchOperationGoneError';
	}
}

async function requestOrThrowTreatingNotFoundAsGone(input: Request): Promise<Response> {
	try {
		return await requestOrThrow(input);
	} catch (error) {
		if (isNotFoundError(error)) {
			throw new BatchOperationGoneError();
		}
		throw error;
	}
}

function pendingActionsQueryKey(batchOperationKey: string) {
	return ['batchOperationPendingActions', batchOperationKey] as const;
}

function goneQueryKey(batchOperationKey: string) {
	return ['batchOperationGone', batchOperationKey] as const;
}

function isBatchOperationAlreadyKnownGone(queryClient: QueryClient, batchOperationKey: string): boolean {
	return queryClient.getQueryData(goneQueryKey(batchOperationKey)) === true;
}

function markBatchOperationGone(queryClient: QueryClient, batchOperationKey: string) {
	queryClient.setQueryData(goneQueryKey(batchOperationKey), true);
}

const PENDING_ACTION_STORAGE_KEY_PREFIX = 'batchOperationPendingAction:';

function pendingActionStorageKey(batchOperationKey: string, action: BatchOperationAction): string {
	return `${PENDING_ACTION_STORAGE_KEY_PREFIX}${batchOperationKey}:${action}`;
}

function withBestEffortStorageAccess<T>(operation: () => T, fallback: T): T {
	try {
		return operation();
	} catch {
		return fallback;
	}
}

function sweepExpiredPersistedActions() {
	withBestEffortStorageAccess(() => {
		const now = Date.now();
		for (let index = sessionStorage.length - 1; index >= 0; index--) {
			const key = sessionStorage.key(index);
			if (key === null || !key.startsWith(PENDING_ACTION_STORAGE_KEY_PREFIX)) {
				continue;
			}
			const expiresAt = Number(sessionStorage.getItem(key));
			if (!Number.isFinite(expiresAt) || expiresAt <= now) {
				sessionStorage.removeItem(key);
			}
		}
	}, undefined);
}

function persistPendingAction(batchOperationKey: string, action: BatchOperationAction) {
	withBestEffortStorageAccess(
		() =>
			sessionStorage.setItem(
				pendingActionStorageKey(batchOperationKey, action),
				String(Date.now() + PENDING_ACTION_PERSISTENCE_EXPIRY_MS),
			),
		undefined,
	);
}

function clearPersistedPendingAction(batchOperationKey: string, action: BatchOperationAction) {
	withBestEffortStorageAccess(
		() => sessionStorage.removeItem(pendingActionStorageKey(batchOperationKey, action)),
		undefined,
	);
}

function readUnexpiredPersistedActions(batchOperationKey: string): BatchOperationAction[] {
	sweepExpiredPersistedActions();
	return withBestEffortStorageAccess(
		() =>
			KNOWN_ACTIONS.filter(
				(action) => sessionStorage.getItem(pendingActionStorageKey(batchOperationKey, action)) !== null,
			),
		[],
	);
}

function isNotFoundError(error: unknown): boolean {
	const requestError = requestErrorSchema.safeParse(error);
	return requestError.success && requestError.data.response?.status === 404;
}

async function waitForBatchOperationTransition(
	queryClient: QueryClient,
	batchOperationKey: string,
	action: BatchOperationAction,
): Promise<BatchOperation> {
	try {
		return await queryClient.fetchQuery({
			queryKey: ['batchOperationTransition', batchOperationKey, action] as const,
			queryFn: async (): Promise<BatchOperation> => {
				const batchOperation = await withAbortTimeout(async (signal) => {
					const response = await requestOrThrow(
						new Request(endpoints.getBatchOperation({batchOperationKey}), {signal}),
					);
					return response.json() as Promise<BatchOperation>;
				}, SINGLE_REQUEST_TIMEOUT_MS);
				if (!CONVERGED_STATES_BY_ACTION[action].includes(batchOperation.state)) {
					throw new Error(`batch operation has not converged for ${action} yet`);
				}
				return batchOperation;
			},
			retry: (failureCount, error) =>
				!isNotFoundError(error) && !(error instanceof ForbiddenError) && failureCount < MAX_POLL_ATTEMPTS - 1,
			retryDelay: POLL_RETRY_DELAY_MS,
		});
	} catch (error) {
		if (isNotFoundError(error)) {
			throw new BatchOperationGoneError();
		}
		if (error instanceof ForbiddenError) {
			throw error;
		}
		throw new BatchOperationConvergenceUnknownError();
	}
}

function useBatchOperationActions(batchOperationKey: string) {
	const {t} = useTranslation();
	const queryClient = useQueryClient();
	const navigate = useNavigate();

	const {data: isGone} = useQuery({
		queryKey: goneQueryKey(batchOperationKey),
		queryFn: (): boolean => false,
		initialData: false,
		staleTime: Infinity,
	});
	useEffect(() => {
		if (isGone) {
			void navigate({to: '/operate/batch-operations', replace: true});
		}
	}, [isGone, navigate]);

	const hadExistingPendingActionsCacheEntry =
		queryClient.getQueryData(pendingActionsQueryKey(batchOperationKey)) !== undefined;
	const {data: pendingActions = []} = useQuery({
		queryKey: pendingActionsQueryKey(batchOperationKey),
		queryFn: (): BatchOperationAction[] => [],
		initialData: () => readUnexpiredPersistedActions(batchOperationKey),
		staleTime: Infinity,
	});
	const addPendingAction = (action: BatchOperationAction) => {
		persistPendingAction(batchOperationKey, action);
		queryClient.setQueryData(pendingActionsQueryKey(batchOperationKey), (current: BatchOperationAction[] = []) =>
			current.includes(action) ? current : [...current, action],
		);
	};
	const removePendingAction = (action: BatchOperationAction) => {
		clearPersistedPendingAction(batchOperationKey, action);
		queryClient.setQueryData(pendingActionsQueryKey(batchOperationKey), (current: BatchOperationAction[] = []) =>
			current.filter((pending) => pending !== action),
		);
	};

	const invalidate = (convergedBatchOperation: BatchOperation) => {
		queryClient.setQueryData(['batchOperation', batchOperationKey], (current?: BatchOperation) =>
			current && TERMINAL_STATES.includes(current.state) ? current : convergedBatchOperation,
		);
		return Promise.all([
			withTimeout(queryClient.invalidateQueries({queryKey: ['batchOperationItems']}), SINGLE_REQUEST_TIMEOUT_MS).catch(
				() => {},
			),
			withTimeout(queryClient.invalidateQueries({queryKey: ['batchOperations']}), SINGLE_REQUEST_TIMEOUT_MS).catch(
				() => {},
			),
			withTimeout(queryClient.invalidateQueries({queryKey: ['processInstances']}), SINGLE_REQUEST_TIMEOUT_MS).catch(
				() => {},
			),
		]);
	};

	const showActionError = (error: unknown, failedTitle: string) => {
		if (error instanceof BatchOperationGoneError) {
			if (!isBatchOperationAlreadyKnownGone(queryClient, batchOperationKey)) {
				notificationsStore.displayNotification({
					kind: 'error',
					title: t('operate.batchOperation.notFoundNotificationTitle', {batchOperationKey}),
					isDismissable: true,
				});
			}
			markBatchOperationGone(queryClient, batchOperationKey);
			return;
		}

		if (error instanceof BatchOperationConvergenceUnknownError || error instanceof RequestTimeoutError) {
			notificationsStore.displayNotification({
				kind: 'warning',
				title: t('operate.batchOperation.actions.convergenceUnknownTitle'),
				subtitle: t('operate.batchOperation.actions.convergenceUnknownSubtitle'),
				isDismissable: true,
			});
			return;
		}

		if (error instanceof ForbiddenError) {
			notificationsStore.displayNotification({
				kind: 'warning',
				title: t('operate.batchOperation.actions.forbiddenTitle'),
				subtitle: t('operate.batchOperation.actions.forbiddenSubtitle'),
				isDismissable: true,
			});
			return;
		}

		const requestError = requestErrorSchema.safeParse(error);
		const isNotFound = requestError.success && requestError.data.response?.status === 404;

		notificationsStore.displayNotification({
			kind: 'error',
			title: failedTitle,
			subtitle: isNotFound ? t('operate.batchOperation.actions.notFoundSubtitle') : undefined,
			isDismissable: true,
		});
	};

	const failedTitleByAction: Record<BatchOperationAction, string> = {
		suspend: t('operate.batchOperation.actions.suspendFailed'),
		resume: t('operate.batchOperation.actions.resumeFailed'),
		cancel: t('operate.batchOperation.actions.cancelFailed'),
	};

	const resumePendingAction = async (action: BatchOperationAction) => {
		try {
			await invalidate(await waitForBatchOperationTransition(queryClient, batchOperationKey, action));
		} catch (error) {
			showActionError(error, failedTitleByAction[action]);
		} finally {
			removePendingAction(action);
		}
	};

	useEffect(() => {
		if (hadExistingPendingActionsCacheEntry) {
			return;
		}
		for (const action of readUnexpiredPersistedActions(batchOperationKey)) {
			void resumePendingAction(action);
		}
		// eslint-disable-next-line react-hooks/exhaustive-deps -- resume once per mount; re-running this for a state change the resumed poll itself causes would resume the same action again
	}, [batchOperationKey]);

	const suspend = useMutation({
		mutationFn: async () => {
			addPendingAction('suspend');
			await withAbortTimeout(
				(signal) =>
					requestOrThrowTreatingNotFoundAsGone(
						new Request(endpoints.suspendBatchOperation({batchOperationKey}), {signal}),
					),
				SINGLE_REQUEST_TIMEOUT_MS,
			);
			return waitForBatchOperationTransition(queryClient, batchOperationKey, 'suspend');
		},
		onSuccess: invalidate,
		onError: (error) => showActionError(error, failedTitleByAction.suspend),
		onSettled: () => removePendingAction('suspend'),
	});

	const resume = useMutation({
		mutationFn: async () => {
			addPendingAction('resume');
			await withAbortTimeout(
				(signal) =>
					requestOrThrowTreatingNotFoundAsGone(
						new Request(endpoints.resumeBatchOperation({batchOperationKey}), {signal}),
					),
				SINGLE_REQUEST_TIMEOUT_MS,
			);
			return waitForBatchOperationTransition(queryClient, batchOperationKey, 'resume');
		},
		onSuccess: invalidate,
		onError: (error) => showActionError(error, failedTitleByAction.resume),
		onSettled: () => removePendingAction('resume'),
	});

	const cancel = useMutation({
		mutationFn: async () => {
			addPendingAction('cancel');
			await withAbortTimeout(
				(signal) =>
					requestOrThrowTreatingNotFoundAsGone(
						new Request(endpoints.cancelBatchOperation({batchOperationKey}), {signal}),
					),
				SINGLE_REQUEST_TIMEOUT_MS,
			);
			return waitForBatchOperationTransition(queryClient, batchOperationKey, 'cancel');
		},
		onSuccess: invalidate,
		onError: (error) => showActionError(error, failedTitleByAction.cancel),
		onSettled: () => removePendingAction('cancel'),
	});

	return {
		suspend: suspend.mutate,
		resume: resume.mutate,
		cancel: cancel.mutate,
		isSuspendPending: suspend.isPending || pendingActions.includes('suspend'),
		isResumePending: resume.isPending || pendingActions.includes('resume'),
		isCancelPending: cancel.isPending || pendingActions.includes('cancel'),
	};
}

export {useBatchOperationActions, isBatchOperationAlreadyKnownGone, markBatchOperationGone};
