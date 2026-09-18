/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect, vi} from 'vitest';
import {QueryClient} from '@tanstack/react-query';
import {createActor} from 'xstate';
import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {
	mockCreateCancellationBatchOperationEndpoint,
	mockGetBatchOperationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createBatchOperation} from '#/shared-test-modules/api-mocks/batch-operations';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {processBulkOperationMachine} from './processBulkOperationMachine';

describe('process bulk operation lifecycle', () => {
	afterEach(() => {
		vi.useRealTimers();
		notificationsStore.reset();
	});

	for (const initial of ['ACTIVE', 'not-indexed', 'unavailable'] as const) {
		it(`should track acceptance separately from ${initial} and never resubmit to recover progress`, async ({
			worker,
		}) => {
			vi.useFakeTimers({toFake: ['setTimeout', 'clearTimeout']});
			const queryClient = new QueryClient({defaultOptions: {queries: {retry: false, gcTime: Infinity}}});
			const actor = createActor(processBulkOperationMachine, {input: {queryClient}}).start();
			let submissions = 0;
			let reads = 0;
			worker.events.on('request:start', ({request}) => {
				if (request.method === 'POST') {
					submissions++;
				}
				if (request.method === 'GET') {
					reads++;
				}
			});
			worker.use(
				mockCreateCancellationBatchOperationEndpoint({
					successResponse: HttpResponse.json(
						{batchOperationKey: 'batch-op-1', batchOperationType: 'CANCEL_PROCESS_INSTANCE'},
						{status: 202},
					),
				}),
				mockGetBatchOperationEndpoint({
					successResponse:
						initial === 'ACTIVE'
							? HttpResponse.json(createBatchOperation({state: 'ACTIVE'}))
							: new HttpResponse(null, {status: initial === 'not-indexed' ? 404 : 503}),
				}),
			);
			try {
				actor.send({
					type: 'submit',
					action: 'cancel',
					body: {filter: {processInstanceKey: {$in: ['1']}}},
					filterIdentity: 'tenant-a',
				});
				await expect.poll(() => actor.getSnapshot().context.acceptedKey).toBe('batch-op-1');
				await expect.poll(() => reads).toBe(1);
				await expect.poll(() => queryClient.isFetching()).toBe(0);
				expect(notificationsStore.notifications[0]?.kind).toBe('success');
				expect(actor.getSnapshot().matches('idle')).toBe(true);
				queryClient.setQueryData(['processInstances', 'test'], {items: []});
				if (initial !== 'unavailable') {
					worker.use(mockGetBatchOperationEndpoint({successResponse: HttpResponse.json(createBatchOperation())}));
				}
				await vi.advanceTimersByTimeAsync(5000);
				await expect.poll(() => reads).toBe(2);
				await expect.poll(() => queryClient.isFetching()).toBe(0);
				if (initial === 'unavailable') {
					await vi.advanceTimersByTimeAsync(5000);
					await expect
						.poll(() => notificationsStore.notifications[0]?.title)
						.toBe('Batch operation progress is temporarily unavailable');
				}
				await expect.poll(() => queryClient.getQueryState(['processInstances', 'test'])?.isInvalidated).toBe(true);
				await vi.advanceTimersByTimeAsync(10000);
				expect(submissions).toBe(1);
				expect(reads).toBe(initial === 'unavailable' ? 3 : 2);
			} finally {
				actor.stop();
				queryClient.clear();
				worker.events.removeAllListeners('request:start');
			}
		});
	}
});
