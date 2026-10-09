/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect, vi} from 'vitest';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {
	Outlet,
	RouterProvider,
	createMemoryHistory,
	createRootRouteWithContext,
	createRoute,
	createRouter,
} from '@tanstack/react-router';
import {HttpResponse, http} from 'msw';
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
import {createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {BatchOperationActions} from './BatchOperationActions';

const BATCH_OPERATION_KEY = 'batch-op-1';
const PATH = '/operate/batch-operations/$batchOperationKey';
const INITIAL_ENTRY = `/operate/batch-operations/${BATCH_OPERATION_KEY}`;

async function renderWithSharedRouter(queryClient: QueryClient, batchOperationState: BatchOperationState) {
	const rootRoute = createRootRouteWithContext<{queryClient: QueryClient}>()({component: () => <Outlet />});
	const testRoute = createRoute({
		getParentRoute: () => rootRoute,
		path: PATH,
		component: () => (
			<BatchOperationActions batchOperationKey={BATCH_OPERATION_KEY} batchOperationState={batchOperationState} />
		),
	});
	const router = createRouter({
		routeTree: rootRoute.addChildren([testRoute]),
		history: createMemoryHistory({initialEntries: [INITIAL_ENTRY]}),
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

	return {...screen, router};
}

function renderActions(batchOperationState: BatchOperationState) {
	const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});
	return renderWithSharedRouter(queryClient, batchOperationState);
}

function holdConvergedRead(state: BatchOperationState) {
	let release!: () => void;
	let requested = false;
	const pending = new Promise<void>((resolve) => {
		release = resolve;
	});

	return {
		handler: http.get(endpoints.getBatchOperation.getUrl({batchOperationKey: ':batchOperationKey'}), async () => {
			requested = true;
			await pending;
			return HttpResponse.json(createBatchOperation({state}));
		}),
		requested: () => requested,
		release,
	};
}

describe('<BatchOperationActions />', () => {
	afterEach(() => {
		vi.useRealTimers();
		notificationsStore.reset();
		sessionStorage.clear();
	});

	it.for(['CREATED', 'ACTIVE'] as const)('should show suspend and cancel for a %s batch operation', async (state) => {
		const screen = await renderActions(state);

		await expect.element(screen.getByRole('button', {name: 'Suspend'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'More actions'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Resume'})).not.toBeInTheDocument();
	});

	it('should show resume and cancel for a suspended batch operation', async () => {
		const screen = await renderActions('SUSPENDED');

		await expect.element(screen.getByRole('button', {name: 'Resume'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'More actions'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Suspend'})).not.toBeInTheDocument();
	});

	it.for(['COMPLETED', 'PARTIALLY_COMPLETED', 'FAILED', 'CANCELED'] as const)(
		'should show no actions for a %s batch operation',
		async (state) => {
			const screen = await renderActions(state);

			await expect.element(screen.getByRole('button', {name: 'Suspend'})).not.toBeInTheDocument();
			await expect.element(screen.getByRole('button', {name: 'Resume'})).not.toBeInTheDocument();
			await expect.element(screen.getByRole('button', {name: 'More actions'})).not.toBeInTheDocument();
		},
	);

	it('should suspend and disable the button while the mutation is in flight, then re-enable it', async ({worker}) => {
		const convergedRead = holdConvergedRead('SUSPENDED');
		worker.use(
			mockSuspendBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			convergedRead.handler,
		);

		const screen = await renderActions('ACTIVE');
		const suspendButton = screen.getByRole('button', {name: 'Suspend'});

		try {
			await userEvent.click(suspendButton);
			await expect.element(suspendButton).toBeDisabled();
			await expect.poll(convergedRead.requested).toBe(true);
		} finally {
			convergedRead.release();
			await expect.element(suspendButton).not.toBeDisabled();
		}
	});

	it('should seed the display cache with the confirmed converged state, not a possibly-stale refetch', async ({
		worker,
	}) => {
		const converged = createBatchOperation({batchOperationKey: BATCH_OPERATION_KEY, state: 'SUSPENDED'});
		worker.use(
			mockSuspendBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetBatchOperationEndpoint({successResponse: HttpResponse.json(converged)}),
		);

		const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});
		const screen = await renderWithSharedRouter(queryClient, 'ACTIVE');
		await userEvent.click(screen.getByRole('button', {name: 'Suspend'}));

		await expect.poll(() => queryClient.getQueryData(['batchOperation', BATCH_OPERATION_KEY])).toEqual(converged);
	});

	it('should resume without requiring the intermediate ACTIVE state to be observed', async ({worker}) => {
		vi.useFakeTimers({toFake: ['setTimeout', 'clearTimeout'], shouldAdvanceTime: true});
		worker.use(
			mockResumeBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(createBatchOperation({state: 'SUSPENDED'})),
				delay: 'infinite',
			}),
		);

		const screen = await renderActions('SUSPENDED');
		const resumeButton = screen.getByRole('button', {name: 'Resume'});
		await userEvent.click(resumeButton);

		await expect.element(resumeButton).toBeDisabled();

		worker.use(
			mockGetBatchOperationEndpoint({successResponse: HttpResponse.json(createBatchOperation({state: 'COMPLETED'}))}),
		);
		await vi.advanceTimersByTimeAsync(11000);

		await expect.element(resumeButton).not.toBeDisabled();
		expect(notificationsStore.notifications).toEqual([]);
	});

	it('should not treat a stale CREATED read as resume having converged', async ({worker}) => {
		vi.useFakeTimers({toFake: ['setTimeout', 'clearTimeout'], shouldAdvanceTime: true});
		worker.use(
			mockResumeBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetBatchOperationEndpoint({successResponse: HttpResponse.json(createBatchOperation({state: 'CREATED'}))}),
		);

		const screen = await renderActions('SUSPENDED');
		const resumeButton = screen.getByRole('button', {name: 'Resume'});
		await userEvent.click(resumeButton);

		await expect
			.poll(
				async () => {
					await vi.advanceTimersByTimeAsync(1000);
					return notificationsStore.notifications;
				},
				{timeout: 5000},
			)
			.toEqual([
				expect.objectContaining({
					kind: 'warning',
					title: "Couldn't confirm batch operation status",
					subtitle: 'The action was sent. Refresh the page to see its current state.',
				}),
			]);
		await expect.element(resumeButton).not.toBeDisabled();
	});

	it('should cancel and converge on a partial-completion outcome without waiting for CANCELED specifically', async ({
		worker,
	}) => {
		vi.useFakeTimers({toFake: ['setTimeout', 'clearTimeout'], shouldAdvanceTime: true});
		worker.use(
			mockCancelBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(createBatchOperation({state: 'ACTIVE'})),
				delay: 'infinite',
			}),
		);

		const screen = await renderActions('ACTIVE');
		await userEvent.click(screen.getByRole('button', {name: 'More actions'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Cancel'}));

		await expect.element(screen.getByRole('button', {name: 'More actions'})).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: 'More actions'}));
		await expect.element(screen.getByRole('menuitem', {name: 'Cancel'})).toBeDisabled();

		worker.use(
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(createBatchOperation({state: 'PARTIALLY_COMPLETED'})),
			}),
		);
		await vi.advanceTimersByTimeAsync(11000);

		await expect.element(screen.getByRole('menuitem', {name: 'Cancel'})).not.toBeDisabled();
		expect(notificationsStore.notifications).toEqual([]);
	});

	it('should recover from a transient polling failure and still converge on success', async ({worker}) => {
		vi.useFakeTimers({toFake: ['setTimeout', 'clearTimeout'], shouldAdvanceTime: true});
		worker.use(
			mockSuspendBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 503}), {status: 503}),
				once: true,
			}),
			mockGetBatchOperationEndpoint({successResponse: HttpResponse.json(createBatchOperation({state: 'SUSPENDED'}))}),
		);

		const screen = await renderActions('ACTIVE');
		const suspendButton = screen.getByRole('button', {name: 'Suspend'});
		await userEvent.click(suspendButton);

		await expect.element(suspendButton).toBeDisabled();
		await expect.element(suspendButton).not.toBeDisabled();
		expect(notificationsStore.notifications).toEqual([]);
	});

	it('should show a permission warning and keep the button usable when suspending is forbidden', async ({worker}) => {
		worker.use(
			mockSuspendBatchOperationEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 403}), {status: 403}),
			}),
		);

		const screen = await renderActions('ACTIVE');
		const suspendButton = screen.getByRole('button', {name: 'Suspend'});
		await userEvent.click(suspendButton);

		await expect.element(suspendButton).not.toBeDisabled();
		await expect
			.poll(() => notificationsStore.notifications)
			.toEqual([
				expect.objectContaining({
					kind: 'warning',
					title: "You don't have permission to perform this operation",
					subtitle: 'Contact the administrator if you need access.',
				}),
			]);
	});

	it('should notify and redirect immediately when the action request itself finds it already gone', async ({
		worker,
	}) => {
		worker.use(
			mockResumeBatchOperationEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 404}), {status: 404}),
			}),
		);

		const screen = await renderActions('SUSPENDED');
		await userEvent.click(screen.getByRole('button', {name: 'Resume'}));

		await expect.poll(() => screen.router.state.location.pathname).toBe('/operate/batch-operations');
		await expect
			.poll(() => notificationsStore.notifications)
			.toEqual([
				expect.objectContaining({
					kind: 'error',
					title: `Couldn't find batch operation ${BATCH_OPERATION_KEY}`,
				}),
			]);
	});

	it('should show a permission warning without waiting out the poll when the confirming read is forbidden', async ({
		worker,
	}) => {
		worker.use(
			mockSuspendBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 403}), {status: 403}),
			}),
		);

		const screen = await renderActions('ACTIVE');
		const suspendButton = screen.getByRole('button', {name: 'Suspend'});
		await userEvent.click(suspendButton);

		await expect
			.poll(() => notificationsStore.notifications, {timeout: 750})
			.toEqual([
				expect.objectContaining({
					kind: 'warning',
					title: "You don't have permission to perform this operation",
					subtitle: 'Contact the administrator if you need access.',
				}),
			]);
		await expect.element(suspendButton).not.toBeDisabled();
	});

	it('should show a generic error notification without a subtitle for other cancel failures', async ({worker}) => {
		worker.use(
			mockCancelBatchOperationEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);

		const screen = await renderActions('ACTIVE');
		await userEvent.click(screen.getByRole('button', {name: 'More actions'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Cancel'}));

		await expect
			.poll(() => notificationsStore.notifications)
			.toEqual([
				expect.objectContaining({
					kind: 'error',
					title: 'Operation cannot be canceled',
					subtitle: undefined,
				}),
			]);
	});

	it('should give up and warn that status is unconfirmed when polling fails persistently after a successful action', async ({
		worker,
	}) => {
		vi.useFakeTimers({toFake: ['setTimeout', 'clearTimeout'], shouldAdvanceTime: true});
		worker.use(
			mockSuspendBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);

		const screen = await renderActions('ACTIVE');
		const suspendButton = screen.getByRole('button', {name: 'Suspend'});
		await userEvent.click(suspendButton);

		await expect.element(suspendButton).toBeDisabled();
		await expect
			.poll(
				async () => {
					await vi.advanceTimersByTimeAsync(1000);
					return notificationsStore.notifications;
				},
				{timeout: 5000},
			)
			.toEqual([
				expect.objectContaining({
					kind: 'warning',
					title: "Couldn't confirm batch operation status",
					subtitle: 'The action was sent. Refresh the page to see its current state.',
				}),
			]);
		await expect.element(suspendButton).not.toBeDisabled();
	});

	it('should warn that status is unconfirmed rather than claim failure when the action request itself times out', async ({
		worker,
	}) => {
		vi.useFakeTimers({toFake: ['setTimeout', 'clearTimeout'], shouldAdvanceTime: true});
		worker.use(
			mockSuspendBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204}), delay: 'infinite'}),
		);

		const screen = await renderActions('ACTIVE');
		const suspendButton = screen.getByRole('button', {name: 'Suspend'});
		await userEvent.click(suspendButton);

		await expect.element(suspendButton).toBeDisabled();

		await vi.advanceTimersByTimeAsync(10000);

		await expect.element(suspendButton).not.toBeDisabled();
		await expect
			.poll(() => notificationsStore.notifications)
			.toEqual([
				expect.objectContaining({
					kind: 'warning',
					title: "Couldn't confirm batch operation status",
					subtitle: 'The action was sent. Refresh the page to see its current state.',
				}),
			]);
	});

	it('should notify and redirect immediately when the poll discovers the batch operation is gone', async ({worker}) => {
		worker.use(
			mockSuspendBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 404}), {status: 404}),
			}),
		);

		const screen = await renderActions('ACTIVE');
		await userEvent.click(screen.getByRole('button', {name: 'Suspend'}));

		await expect.poll(() => screen.router.state.location.pathname).toBe('/operate/batch-operations');
		await expect
			.poll(() => notificationsStore.notifications)
			.toEqual([
				expect.objectContaining({
					kind: 'error',
					title: `Couldn't find batch operation ${BATCH_OPERATION_KEY}`,
				}),
			]);
	});

	it('should keep a control disabled across a remount while its action is still converging', async ({worker}) => {
		vi.useFakeTimers({toFake: ['setTimeout', 'clearTimeout'], shouldAdvanceTime: true});
		worker.use(
			mockSuspendBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(createBatchOperation({state: 'ACTIVE'})),
				delay: 'infinite',
			}),
		);

		const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});

		const firstRender = await renderWithSharedRouter(queryClient, 'ACTIVE');
		await userEvent.click(firstRender.getByRole('button', {name: 'Suspend'}));
		await expect.element(firstRender.getByRole('button', {name: 'Suspend'})).toBeDisabled();

		await firstRender.unmount();

		const secondRender = await renderWithSharedRouter(queryClient, 'ACTIVE');

		await expect.element(secondRender.getByRole('button', {name: 'Suspend'})).toBeDisabled();

		worker.use(
			mockGetBatchOperationEndpoint({successResponse: HttpResponse.json(createBatchOperation({state: 'SUSPENDED'}))}),
		);
		await vi.advanceTimersByTimeAsync(11000);

		await expect.element(secondRender.getByRole('button', {name: 'Suspend'})).not.toBeDisabled();
	});

	it('should track two concurrently pending actions independently across a remount', async ({worker}) => {
		vi.useFakeTimers({toFake: ['setTimeout', 'clearTimeout'], shouldAdvanceTime: true});
		worker.use(
			mockSuspendBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockCancelBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204}), delay: 'infinite'}),
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(createBatchOperation({state: 'ACTIVE'})),
			}),
		);

		const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});

		const firstRender = await renderWithSharedRouter(queryClient, 'ACTIVE');
		await userEvent.click(firstRender.getByRole('button', {name: 'Suspend'}));
		await userEvent.click(firstRender.getByRole('button', {name: 'More actions'}));
		await userEvent.click(firstRender.getByRole('menuitem', {name: 'Cancel'}));

		await firstRender.unmount();

		const secondRender = await renderWithSharedRouter(queryClient, 'ACTIVE');

		await expect.element(secondRender.getByRole('button', {name: 'Suspend'})).toBeDisabled();
		await userEvent.click(secondRender.getByRole('button', {name: 'More actions'}));
		await expect.element(secondRender.getByRole('menuitem', {name: 'Cancel'})).toBeDisabled();

		worker.use(
			mockGetBatchOperationEndpoint({successResponse: HttpResponse.json(createBatchOperation({state: 'SUSPENDED'}))}),
		);

		await expect.element(secondRender.getByRole('button', {name: 'Suspend'})).not.toBeDisabled();
		await expect.element(secondRender.getByRole('menuitem', {name: 'Cancel'})).toBeDisabled();

		await vi.advanceTimersByTimeAsync(10000);

		await expect.element(secondRender.getByRole('menuitem', {name: 'Cancel'})).not.toBeDisabled();
	});

	it('should keep a control disabled across a hard refresh, not just an in-app remount', async ({worker}) => {
		vi.useFakeTimers({toFake: ['setTimeout', 'clearTimeout'], shouldAdvanceTime: true});
		sessionStorage.setItem(`batchOperationPendingAction:${BATCH_OPERATION_KEY}:suspend`, String(Date.now() + 60000));
		worker.use(
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(createBatchOperation({state: 'ACTIVE'})),
				delay: 'infinite',
			}),
		);

		const screen = await renderActions('ACTIVE');

		await expect.element(screen.getByRole('button', {name: 'Suspend'})).toBeDisabled();

		worker.use(
			mockGetBatchOperationEndpoint({successResponse: HttpResponse.json(createBatchOperation({state: 'SUSPENDED'}))}),
		);
		await vi.advanceTimersByTimeAsync(11000);

		await expect.element(screen.getByRole('button', {name: 'Suspend'})).not.toBeDisabled();
		expect(sessionStorage.getItem(`batchOperationPendingAction:${BATCH_OPERATION_KEY}:suspend`)).toBeNull();
	});
});
