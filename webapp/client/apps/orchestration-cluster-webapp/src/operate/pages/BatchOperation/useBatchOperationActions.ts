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

// The action succeeds once the batch operation leaves the state(s) it was in before the action was
// sent — whichever legal state it lands on next (including a concurrently-reached terminal state).
// Observing the "expected" intermediate state (e.g. SUSPENDED for a resume) is not required.
const TRANSITIONAL_STATES_BY_ACTION: Record<BatchOperationAction, ReadonlyArray<BatchOperation['state']>> = {
	suspend: ['CREATED', 'ACTIVE'],
	resume: ['SUSPENDED'],
	cancel: ['CREATED', 'ACTIVE', 'SUSPENDED'],
};

// Once a batch operation reaches one of these, it's done — the domain model has no path back to a
// non-terminal state. Guards a display-cache write that arrives out of order (e.g. this action's own
// poll response was delayed behind a concurrently-running sibling action's, or a stale refetch of the
// display query itself lands after this write) from regressing an already-settled outcome.
const TERMINAL_STATES: ReadonlyArray<BatchOperation['state']> = [
	'COMPLETED',
	'PARTIALLY_COMPLETED',
	'FAILED',
	'CANCELED',
];

// Bounds how long we wait for the batch operation to converge after a successful action. Unbounded
// retry would leave the control disabled forever (and never notify) against a permission revoked or
// the batch operation deleted mid-poll — the action itself already succeeded, so give up and say the
// outcome is unconfirmed rather than hang or claim the action failed. `retry` counts retries *after*
// the initial attempt, so this bounds the poll to MAX_POLL_ATTEMPTS attempts in total.
const MAX_POLL_ATTEMPTS = 6;
const POLL_RETRY_DELAY_MS = 1000;

// The shared `request()` helper wraps a plain `fetch` with no timeout of its own — a stalled
// connection (accepted but never answered) never resolves or rejects, so it would never reach a
// retry predicate or let `Promise.all` settle, no matter how "bounded" the surrounding logic is.
// This wraps any individual request awaited in this file so a hang becomes an ordinary rejection.
const REQUEST_TIMEOUT_MS = 10000;

// Distinct from any other rejection withTimeout can forward: only this one means the *wrapper* gave
// up while the underlying request may still be in flight (withTimeout races a timer against the
// promise, it doesn't abort it) — as opposed to the request itself settling with a definite error.
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

class BatchOperationConvergenceUnknownError extends Error {
	constructor() {
		super('batch operation status could not be confirmed after the action was sent');
		this.name = 'BatchOperationConvergenceUnknownError';
	}
}

// The poll discovered the batch operation is gone (404), not just still converging — a terminal
// outcome, not a transitional one. The caller notifies and redirects directly instead of relying on
// invalidating the display query: that query's own refetch would go through the app's default query
// retry policy (reactQueryClient sets no override, so TanStack's default of 3 retries applies), so it
// could re-hit the same 404 up to 3 more times before the page's read-path 404 effect ever saw it.
class BatchOperationGoneDuringPollError extends Error {
	constructor() {
		super('batch operation no longer exists');
		this.name = 'BatchOperationGoneDuringPollError';
	}
}

function pendingActionsQueryKey(batchOperationKey: string) {
	return ['batchOperationPendingActions', batchOperationKey] as const;
}

function goneQueryKey(batchOperationKey: string) {
	return ['batchOperationGone', batchOperationKey] as const;
}

function isNotFoundError(error: unknown): boolean {
	const requestError = requestErrorSchema.safeParse(error);
	return requestError.success && requestError.data.response?.status === 404;
}

// Returns the converged BatchOperation so the caller can seed the display cache with it directly.
// Invalidating the display query instead and letting it refetch independently would race: reads are
// eventually consistent, so that refetch could still land on a replica reporting the pre-action
// state even though this poll already confirmed the transition on another.
async function waitForBatchOperationTransition(
	queryClient: QueryClient,
	batchOperationKey: string,
	action: BatchOperationAction,
): Promise<BatchOperation> {
	try {
		// A distinct cache key from ['batchOperation', batchOperationKey] — that one is the page's own
		// display cache, and the retry condition here depends on `action`, which isn't part of it.
		return await queryClient.fetchQuery({
			queryKey: ['batchOperationTransition', batchOperationKey, action] as const,
			queryFn: async (): Promise<BatchOperation> => {
				// The whole fetch-and-parse is one timed unit: `request()` itself only resolves once
				// `fetch` has headers, so `response.json()` reading a slow/stalled body afterwards would
				// otherwise escape the timeout and never reach the retry predicate below.
				const batchOperation = await withTimeout(
					(async () => {
						const {response, error} = await request(endpoints.getBatchOperation({batchOperationKey}));
						if (error !== null) {
							throw mapQueryError(error);
						}
						return response.json() as Promise<BatchOperation>;
					})(),
					REQUEST_TIMEOUT_MS,
				);
				if (TRANSITIONAL_STATES_BY_ACTION[action].includes(batchOperation.state)) {
					throw new Error(`batch operation has not left its pre-${action} state yet`);
				}
				return batchOperation;
			},
			// A confirmed 404 is terminal, not something more polling will resolve — stop immediately
			// rather than spend the whole bound rediscovering the same answer.
			retry: (failureCount, error) => !isNotFoundError(error) && failureCount < MAX_POLL_ATTEMPTS - 1,
			retryDelay: POLL_RETRY_DELAY_MS,
		});
	} catch (error) {
		if (isNotFoundError(error)) {
			throw new BatchOperationGoneDuringPollError();
		}
		throw new BatchOperationConvergenceUnknownError();
	}
}

function useBatchOperationActions(batchOperationKey: string) {
	const {t} = useTranslation();
	const queryClient = useQueryClient();
	const navigate = useNavigate();

	// A mutation isn't cancelled by unmount, so a "gone" result can arrive after the user has already
	// navigated elsewhere — including back into this *same* operation via a fresh mount. Tracked as
	// shared, key-scoped cache state (not a mounted-ref on the instance that started the poll) so
	// whichever instance is *currently* rendered for this batchOperationKey reacts: a same-key remount
	// still redirects, and a different key never does, since each key has its own cache entry.
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

	// Tracked in the query cache rather than component state: a route remount while an action is
	// still converging (waitForBatchOperationTransition can take several seconds) must not show a
	// freshly-enabled control that could fire a duplicate command, and this cache entry — unlike a
	// mutation's own `isPending` — survives the remount. Suspend and Cancel (or Resume and Cancel) can
	// legitimately be triggered concurrently — they're both enabled at the same time in the same
	// state — so this tracks a set, not a single value: each action removes only its own entry on
	// settle, never clobbering a sibling action that's still in flight.
	const {data: pendingActions = []} = useQuery({
		queryKey: pendingActionsQueryKey(batchOperationKey),
		queryFn: (): BatchOperationAction[] => [],
		initialData: [],
		staleTime: Infinity,
	});
	const addPendingAction = (action: BatchOperationAction) =>
		queryClient.setQueryData(pendingActionsQueryKey(batchOperationKey), (current: BatchOperationAction[] = []) =>
			current.includes(action) ? current : [...current, action],
		);
	const removePendingAction = (action: BatchOperationAction) =>
		queryClient.setQueryData(pendingActionsQueryKey(batchOperationKey), (current: BatchOperationAction[] = []) =>
			current.filter((pending) => pending !== action),
		);

	// Awaited by each mutation's onSuccess so the control stays disabled until this actually lands —
	// otherwise it could re-enable while the page still renders the pre-action cached state. The
	// converged BatchOperation is written straight into the display cache (no race with an eventually-
	// consistent refetch); items and the list aren't returned by the poll, so those still refetch. Each
	// refetch is given its own timeout and never rejects `invalidate`'s own promise — a stalled
	// items/list/process-instances refresh must not leave the control disabled indefinitely when the
	// action itself already converged and the display cache is already correct.
	const invalidate = (convergedBatchOperation: BatchOperation) => {
		queryClient.setQueryData(['batchOperation', batchOperationKey], (current?: BatchOperation) =>
			current && TERMINAL_STATES.includes(current.state) && !TERMINAL_STATES.includes(convergedBatchOperation.state)
				? current
				: convergedBatchOperation,
		);
		return Promise.all([
			// Just the 'batchOperationItems' prefix, not '...batchOperationKey]': the Processes list
			// queries the same endpoint keyed by its own request body (['batchOperationItems', body]),
			// not by batchOperationKey, so a key with batchOperationKey as the second segment would miss
			// it and leave an already-mounted list showing stale per-instance operation state.
			withTimeout(queryClient.invalidateQueries({queryKey: ['batchOperationItems']}), REQUEST_TIMEOUT_MS).catch(
				() => {},
			),
			withTimeout(queryClient.invalidateQueries({queryKey: ['batchOperations']}), REQUEST_TIMEOUT_MS).catch(() => {}),
			// The action can change what the Processes list shows for affected rows (e.g. a canceled or
			// incident-resolved instance's state) — that list doesn't poll once finished, so it needs an
			// explicit nudge or it can show stale rows for its own cache window after this completes.
			withTimeout(queryClient.invalidateQueries({queryKey: ['processInstances']}), REQUEST_TIMEOUT_MS).catch(() => {}),
		]);
	};

	const showActionError = (error: unknown, failedTitle: string) => {
		if (error instanceof BatchOperationGoneDuringPollError) {
			// Suspend and Cancel (or Resume and Cancel) can be in flight together, and both polls can
			// independently discover the same 404 — only the first to arrive should notify; the rest
			// just confirm a flag that's already set, so the user doesn't see duplicate toasts.
			const alreadyKnownGone = queryClient.getQueryData(goneQueryKey(batchOperationKey)) === true;
			if (!alreadyKnownGone) {
				notificationsStore.displayNotification({
					kind: 'error',
					title: t('operate.batchOperation.notFoundNotificationTitle', {batchOperationKey}),
					isDismissable: true,
				});
			}
			queryClient.setQueryData(goneQueryKey(batchOperationKey), true);
			return;
		}

		// A RequestTimeoutError here comes from the action's own POST, not the follow-up poll: withTimeout
		// only abandons waiting, it doesn't abort the fetch, so the command may still land server-side
		// after we've stopped waiting for it. Reporting it as a confirmed failure would be actively wrong
		// (the server may still process it) and risks a genuine duplicate command if the user, believing
		// it failed, retries once the control re-enables — so it gets the same "unconfirmed" treatment as
		// a poll that never converges, not the generic failure message below.
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

	// The pending marker is removed in onSettled — after onSuccess/onError have fully run, including
	// the display-cache write and item/list invalidation — not in the mutation function itself. A
	// remount that lands in the gap between the mutation resolving and its side effects finishing must
	// still see the marker, or it would show a stale pre-action state with the control re-enabled.
	const suspend = useMutation({
		mutationFn: async () => {
			addPendingAction('suspend');
			const {error} = await withTimeout(
				request(endpoints.suspendBatchOperation({batchOperationKey})),
				REQUEST_TIMEOUT_MS,
			);
			if (error !== null) {
				throw mapQueryError(error);
			}
			return waitForBatchOperationTransition(queryClient, batchOperationKey, 'suspend');
		},
		onSuccess: invalidate,
		onError: (error) => showActionError(error, t('operate.batchOperation.actions.suspendFailed')),
		onSettled: () => removePendingAction('suspend'),
	});

	const resume = useMutation({
		mutationFn: async () => {
			addPendingAction('resume');
			const {error} = await withTimeout(
				request(endpoints.resumeBatchOperation({batchOperationKey})),
				REQUEST_TIMEOUT_MS,
			);
			if (error !== null) {
				throw mapQueryError(error);
			}
			return waitForBatchOperationTransition(queryClient, batchOperationKey, 'resume');
		},
		onSuccess: invalidate,
		onError: (error) => showActionError(error, t('operate.batchOperation.actions.resumeFailed')),
		onSettled: () => removePendingAction('resume'),
	});

	const cancel = useMutation({
		mutationFn: async () => {
			addPendingAction('cancel');
			const {error} = await withTimeout(
				request(endpoints.cancelBatchOperation({batchOperationKey})),
				REQUEST_TIMEOUT_MS,
			);
			if (error !== null) {
				throw mapQueryError(error);
			}
			return waitForBatchOperationTransition(queryClient, batchOperationKey, 'cancel');
		},
		onSuccess: invalidate,
		onError: (error) => showActionError(error, t('operate.batchOperation.actions.cancelFailed')),
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

export {useBatchOperationActions};
