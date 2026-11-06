/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect} from 'vitest';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {
	Outlet,
	RouterProvider,
	createMemoryHistory,
	createRootRouteWithContext,
	createRoute,
	createRouter,
} from '@tanstack/react-router';
import {http, HttpResponse} from 'msw';
import {render} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import {endpoints, type BatchOperationState} from '@camunda/camunda-api-zod-schemas/8.11';
import {it} from '#/vitest-modules/test-extend';
import {
	mockGetBatchOperationEndpoint,
	mockSuspendBatchOperationEndpoint,
	mockResumeBatchOperationEndpoint,
	mockCancelBatchOperationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createBatchOperation} from '#/shared-test-modules/api-mocks/batch-operations';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {BatchOperationActions} from './BatchOperationActions';

const BATCH_OPERATION_KEY = 'batch-op-1';

async function renderActions(batchOperationState: BatchOperationState) {
	const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});
	const rootRoute = createRootRouteWithContext<{queryClient: QueryClient}>()({component: () => <Outlet />});
	const testRoute = createRoute({
		getParentRoute: () => rootRoute,
		path: '/operate-preview/batch-operations/$batchOperationKey',
		component: () => (
			<BatchOperationActions batchOperationKey={BATCH_OPERATION_KEY} batchOperationState={batchOperationState} />
		),
	});
	const router = createRouter({
		routeTree: rootRoute.addChildren([testRoute]),
		history: createMemoryHistory({initialEntries: [`/operate-preview/batch-operations/${BATCH_OPERATION_KEY}`]}),
		defaultPendingMinMs: 0,
		defaultNotFoundComponent: () => null,
		context: {queryClient},
	});
	await router.load();

	const screen = await render(
		<QueryClientProvider client={queryClient}>
			<RouterProvider router={router} />
		</QueryClientProvider>,
	);

	return {screen, queryClient};
}

function holdGetBatchOperation(state: BatchOperationState) {
	let release: () => void = () => {};
	const gate = new Promise<void>((resolve) => {
		release = resolve;
	});
	const handler = http.get(endpoints.getBatchOperation.getUrl({batchOperationKey: ':batchOperationKey'}), async () => {
		await gate;
		return HttpResponse.json(createBatchOperation({state}));
	});

	return {handler, release};
}

describe('<BatchOperationActions />', () => {
	afterEach(() => {
		notificationsStore.reset();
		sessionStorage.clear();
	});

	it.for(['CREATED', 'ACTIVE'] as const)('should show suspend and cancel for a %s batch operation', async (state) => {
		const {screen} = await renderActions(state);

		await expect.element(screen.getByRole('button', {name: 'Suspend'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'More actions'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Resume'})).not.toBeInTheDocument();
	});

	it('should show resume and cancel for a suspended batch operation', async () => {
		const {screen} = await renderActions('SUSPENDED');

		await expect.element(screen.getByRole('button', {name: 'Resume'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'More actions'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Suspend'})).not.toBeInTheDocument();
	});

	it.for(['COMPLETED', 'PARTIALLY_COMPLETED', 'FAILED', 'CANCELED'] as const)(
		'should show no actions for a %s batch operation',
		async (state) => {
			const {screen} = await renderActions(state);

			await expect.element(screen.getByRole('button', {name: 'Suspend'})).not.toBeInTheDocument();
			await expect.element(screen.getByRole('button', {name: 'Resume'})).not.toBeInTheDocument();
			await expect.element(screen.getByRole('button', {name: 'More actions'})).not.toBeInTheDocument();
		},
	);

	it('should suspend and disable the button while the action is pending, then re-enable it', async ({worker}) => {
		const getBatchOperation = holdGetBatchOperation('SUSPENDED');
		worker.use(
			mockSuspendBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			getBatchOperation.handler,
		);

		const {screen} = await renderActions('ACTIVE');
		const suspendButton = screen.getByRole('button', {name: 'Suspend'});
		try {
			await userEvent.click(suspendButton);

			await expect.element(suspendButton).toBeDisabled();
		} finally {
			getBatchOperation.release();
		}

		await expect.element(suspendButton).not.toBeDisabled();
	});

	it('should resume a suspended batch operation', async ({worker}) => {
		const getBatchOperation = holdGetBatchOperation('ACTIVE');
		worker.use(
			mockResumeBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			getBatchOperation.handler,
		);

		const {screen} = await renderActions('SUSPENDED');
		const resumeButton = screen.getByRole('button', {name: 'Resume'});
		try {
			await userEvent.click(resumeButton);

			await expect.element(resumeButton).toBeDisabled();
		} finally {
			getBatchOperation.release();
		}

		await expect.element(resumeButton).not.toBeDisabled();
	});

	it('should cancel a batch operation from the overflow menu', async ({worker}) => {
		worker.use(
			mockCancelBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(createBatchOperation({state: 'CANCELED'})),
			}),
		);

		const {screen, queryClient} = await renderActions('ACTIVE');
		await userEvent.click(screen.getByRole('button', {name: 'More actions'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Cancel'}));

		await expect
			.poll(() => queryClient.getQueryData<{state: string}>(['batchOperation', BATCH_OPERATION_KEY])?.state)
			.toBe('CANCELED');
		expect(notificationsStore.notifications).toEqual([]);
	});
});
