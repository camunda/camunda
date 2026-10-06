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
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockCreateCancellationBatchOperationEndpoint,
	mockGetBatchOperationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createBatchOperation} from '#/shared-test-modules/api-mocks/batch-operations';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {processBulkOperationMachine} from './processBulkOperationMachine';

const PROCESSES_PATH = '/operate/processes';
const BASEPATH_CASES = [
	{basepath: '', expectedPathPrefix: ''},
	{basepath: '/camunda', expectedPathPrefix: '/camunda'},
	{
		basepath: '/camunda/physical-tenants/tenant-a',
		expectedPathPrefix: '/camunda/physical-tenants/tenant-a',
	},
] as const;
const RouterHarness = () => null;

describe('process bulk operation lifecycle', () => {
	afterEach(() => {
		vi.useRealTimers();
		notificationsStore.reset();
	});

	it.for(['ACTIVE', 'not-indexed', 'unavailable'] as const)(
		'should track acceptance separately from %s and never resubmit to recover progress',
		async (initial, {worker}) => {
			vi.useFakeTimers({toFake: ['setTimeout', 'clearTimeout']});
			const queryClient = new QueryClient({defaultOptions: {queries: {retry: false, gcTime: Infinity}}});
			const actor = createActor(processBulkOperationMachine, {
				input: {queryClient, navigate: async () => undefined},
			}).start();
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
		},
	);

	it.for(BASEPATH_CASES)(
		'should navigate to batch operation details from the success notification at basepath "$basepath"',
		async ({basepath, expectedPathPrefix}, {worker}) => {
			worker.use(
				mockCreateCancellationBatchOperationEndpoint({
					successResponse: HttpResponse.json(
						{batchOperationKey: 'batch-op-1', batchOperationType: 'CANCEL_PROCESS_INSTANCE'},
						{status: 202},
					),
				}),
				mockGetBatchOperationEndpoint({
					successResponse: HttpResponse.json(createBatchOperation({batchOperationKey: 'batch-op-1', state: 'ACTIVE'})),
				}),
			);

			const screen = await renderWithRouter(RouterHarness, {
				path: PROCESSES_PATH,
				basepath,
				initialEntry: `${basepath}${PROCESSES_PATH}`,
			});
			const actor = createActor(processBulkOperationMachine, {
				input: {queryClient: screen.queryClient, navigate: screen.router.navigate},
			}).start();
			try {
				actor.send({
					type: 'submit',
					action: 'cancel',
					body: {filter: {processInstanceKey: {$in: ['1']}}},
					filterIdentity: 'tenant-a',
				});

				await expect
					.poll(() => notificationsStore.notifications.find(({kind}) => kind === 'success')?.onActionButtonClick)
					.toBeTypeOf('function');
				notificationsStore.notifications.find(({kind}) => kind === 'success')?.onActionButtonClick?.();

				await expect
					.poll(() => screen.router.history.location.href)
					.toBe(`${expectedPathPrefix}/operate/batch-operations/batch-op-1`);
			} finally {
				actor.stop();
				screen.queryClient.clear();
			}
		},
	);

	it.for(BASEPATH_CASES)(
		'should navigate to batch operation details from the tracking-failure notification at basepath "$basepath"',
		async ({basepath, expectedPathPrefix}, {worker}) => {
			vi.useFakeTimers({
				toFake: ['setTimeout', 'clearTimeout'],
			});
			let reads = 0;
			worker.events.on('request:start', ({request}) => {
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
					successResponse: new HttpResponse(null, {status: 503}),
				}),
			);

			const screen = await renderWithRouter(RouterHarness, {
				path: PROCESSES_PATH,
				basepath,
				initialEntry: `${basepath}${PROCESSES_PATH}`,
			});
			const actor = createActor(processBulkOperationMachine, {
				input: {queryClient: screen.queryClient, navigate: screen.router.navigate},
			}).start();
			try {
				actor.send({
					type: 'submit',
					action: 'cancel',
					body: {filter: {processInstanceKey: {$in: ['1']}}},
					filterIdentity: 'tenant-a',
				});

				await expect.poll(() => actor.getSnapshot().context.acceptedKey).toBe('batch-op-1');
				await expect.poll(() => reads).toBe(1);
				await vi.advanceTimersByTimeAsync(5000);
				await expect.poll(() => reads).toBe(2);
				await vi.advanceTimersByTimeAsync(5000);
				await expect.poll(() => reads).toBe(3);

				await expect
					.poll(
						() =>
							notificationsStore.notifications.find(
								({title}) => title === 'Batch operation progress is temporarily unavailable',
							)?.onActionButtonClick,
					)
					.toBeTypeOf('function');
				notificationsStore.notifications
					.find(({title}) => title === 'Batch operation progress is temporarily unavailable')
					?.onActionButtonClick?.();

				await expect
					.poll(() => screen.router.history.location.href)
					.toBe(`${expectedPathPrefix}/operate/batch-operations/batch-op-1`);
			} finally {
				actor.stop();
				screen.queryClient.clear();
				worker.events.removeAllListeners('request:start');
			}
		},
	);
});
