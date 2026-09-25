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
import {HttpResponse} from 'msw';
import {render} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import type {BatchOperationState} from '@camunda/camunda-api-zod-schemas/8.10';
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

// useBatchOperationActions calls useNavigate() (for the follow-up-404 path), so every render needs a
// real router context, not just a QueryClientProvider. This mirrors #/vitest-modules/render-with-router
// but accepts an external QueryClient, so the two remount tests below can reuse the same cache across
// separate mounts the way a real route remount would.
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

describe('<BatchOperationActions />', () => {
	afterEach(() => {
		notificationsStore.reset();
		sessionStorage.clear();
	});

	async function expectActions(
		screen: Awaited<ReturnType<typeof renderActions>>,
		{showsSuspend, showsResume, showsCancel}: {showsSuspend: boolean; showsResume: boolean; showsCancel: boolean},
	) {
		if (showsSuspend) {
			await expect.element(screen.getByRole('button', {name: 'Suspend'})).toBeVisible();
		} else {
			await expect.element(screen.getByRole('button', {name: 'Suspend'})).not.toBeInTheDocument();
		}

		if (showsResume) {
			await expect.element(screen.getByRole('button', {name: 'Resume'})).toBeVisible();
		} else {
			await expect.element(screen.getByRole('button', {name: 'Resume'})).not.toBeInTheDocument();
		}

		if (showsCancel) {
			await expect.element(screen.getByRole('button', {name: 'More actions'})).toBeVisible();
		} else {
			await expect.element(screen.getByRole('button', {name: 'More actions'})).not.toBeInTheDocument();
		}
	}

	it('should show suspend and cancel for a created batch operation', async () => {
		await expectActions(await renderActions('CREATED'), {showsSuspend: true, showsResume: false, showsCancel: true});
	});

	it('should show suspend and cancel for an active batch operation', async () => {
		await expectActions(await renderActions('ACTIVE'), {showsSuspend: true, showsResume: false, showsCancel: true});
	});

	it('should show resume and cancel for a suspended batch operation', async () => {
		await expectActions(await renderActions('SUSPENDED'), {showsSuspend: false, showsResume: true, showsCancel: true});
	});

	it('should show no actions for a completed batch operation', async () => {
		await expectActions(await renderActions('COMPLETED'), {
			showsSuspend: false,
			showsResume: false,
			showsCancel: false,
		});
	});

	it('should show no actions for a partially completed batch operation', async () => {
		await expectActions(await renderActions('PARTIALLY_COMPLETED'), {
			showsSuspend: false,
			showsResume: false,
			showsCancel: false,
		});
	});

	it('should show no actions for a failed batch operation', async () => {
		await expectActions(await renderActions('FAILED'), {showsSuspend: false, showsResume: false, showsCancel: false});
	});

	it('should show no actions for a canceled batch operation', async () => {
		await expectActions(await renderActions('CANCELED'), {showsSuspend: false, showsResume: false, showsCancel: false});
	});

	it('should suspend and disable the button while the mutation is in flight, then re-enable it', async ({worker}) => {
		worker.use(
			mockSuspendBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204}), delay: 200}),
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(createBatchOperation({state: 'SUSPENDED'})),
			}),
		);

		const screen = await renderActions('ACTIVE');
		const suspendButton = screen.getByRole('button', {name: 'Suspend'});

		await userEvent.click(suspendButton);

		await expect.element(suspendButton).toBeDisabled();
		await expect.element(suspendButton).not.toBeDisabled();
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
		worker.use(
			mockResumeBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			// The batch already reached a terminal state by the time of the first poll — resume must
			// not wait for ACTIVE to be observed first.
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(createBatchOperation({state: 'COMPLETED'})),
			}),
		);

		const screen = await renderActions('SUSPENDED');
		const resumeButton = screen.getByRole('button', {name: 'Resume'});

		await userEvent.click(resumeButton);

		// Confirm the mutation actually went pending (proving the POST + transition poll ran) before
		// asserting it settles — otherwise this would pass just as well if the poll were removed.
		await expect.element(resumeButton).toBeDisabled();
		await expect.element(resumeButton).not.toBeDisabled();
		expect(notificationsStore.notifications).toEqual([]);
	});

	it('should not treat a stale CREATED read as resume having converged', async ({worker}) => {
		// CREATED converging immediately (the bug) and CREATED correctly being retried past (the fix)
		// both eventually end with the button enabled, so that alone can't tell them apart — count GETs
		// to the batch operation's own endpoint specifically (not incidental browser requests like font
		// loads) and require more than one before accepting the outcome, proving the first (CREATED)
		// read was actually rejected rather than mistaken for convergence.
		let pollRequestCount = 0;
		worker.events.on('request:start', ({request}) => {
			if (request.method === 'GET' && request.url.endsWith(`/batch-operations/${BATCH_OPERATION_KEY}`)) {
				pollRequestCount += 1;
			}
		});

		try {
			worker.use(
				mockResumeBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
				mockGetBatchOperationEndpoint({
					successResponse: HttpResponse.json(createBatchOperation({state: 'CREATED'})),
				}),
			);

			const screen = await renderActions('SUSPENDED');
			const resumeButton = screen.getByRole('button', {name: 'Resume'});
			await userEvent.click(resumeButton);
			await expect.element(resumeButton).toBeDisabled();
			await expect.poll(() => pollRequestCount).toBeGreaterThan(0);

			worker.use(
				mockGetBatchOperationEndpoint({successResponse: HttpResponse.json(createBatchOperation({state: 'ACTIVE'}))}),
			);
			await expect.element(resumeButton).not.toBeDisabled();
			expect(pollRequestCount).toBeGreaterThan(1);
			expect(notificationsStore.notifications).toEqual([]);
		} finally {
			worker.events.removeAllListeners('request:start');
		}
	});

	it('should cancel and converge on a partial-completion outcome without waiting for CANCELED specifically', async ({
		worker,
	}) => {
		worker.use(
			mockCancelBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204}), delay: 200}),
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(createBatchOperation({state: 'PARTIALLY_COMPLETED'})),
			}),
		);

		const screen = await renderActions('ACTIVE');
		await userEvent.click(screen.getByRole('button', {name: 'More actions'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Cancel'}));

		// The OverflowMenu closes the item as soon as it's clicked, so re-open it to confirm the
		// mutation actually went pending (proving the POST + transition poll ran) before asserting it
		// settles — otherwise this would pass just as well if the poll were removed.
		await expect.element(screen.getByRole('button', {name: 'More actions'})).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: 'More actions'}));
		await expect.element(screen.getByRole('menuitem', {name: 'Cancel'})).toBeDisabled();
		// Still the same open menu — no need to reclick the trigger, which would toggle it shut.
		await expect.element(screen.getByRole('menuitem', {name: 'Cancel'})).not.toBeDisabled();
		expect(notificationsStore.notifications).toEqual([]);
	});

	it('should recover from a transient polling failure and still converge on success', async ({worker}) => {
		// "Disabled" alone doesn't prove the retry path ran — it becomes true as soon as the mutation
		// starts, before the first poll GET is even sent. Count the poll's own GETs and wait for one to
		// have actually landed against the failing mock before swapping it, so the success mock can't
		// win a race against the first attempt and let this pass without exercising a retry at all.
		// The worker is shared across tests in this file, so the listener is removed in `finally` —
		// otherwise it keeps counting every later test's GETs too.
		let pollRequestCount = 0;
		worker.events.on('request:start', ({request}) => {
			if (request.method === 'GET' && request.url.endsWith(`/batch-operations/${BATCH_OPERATION_KEY}`)) {
				pollRequestCount += 1;
			}
		});

		try {
			worker.use(
				mockSuspendBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
				mockGetBatchOperationEndpoint({
					successResponse: HttpResponse.json(createProblemDetails({status: 503}), {status: 503}),
				}),
			);

			const screen = await renderActions('ACTIVE');
			const suspendButton = screen.getByRole('button', {name: 'Suspend'});
			await userEvent.click(suspendButton);

			await expect.element(suspendButton).toBeDisabled();
			await expect.poll(() => pollRequestCount).toBeGreaterThan(0);

			worker.use(
				mockGetBatchOperationEndpoint({
					successResponse: HttpResponse.json(createBatchOperation({state: 'SUSPENDED'})),
				}),
			);

			await expect.element(suspendButton).not.toBeDisabled();
			expect(pollRequestCount).toBeGreaterThan(1);
			expect(notificationsStore.notifications).toEqual([]);
		} finally {
			worker.events.removeAllListeners('request:start');
		}
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

		// The poll keeps hitting the same failing GET for the whole test — it must give up rather
		// than retry forever, and must not claim the suspend action itself failed (it already
		// succeeded; only confirming its outcome timed out). MAX_POLL_ATTEMPTS - 1 retries at
		// POLL_RETRY_DELAY_MS apart is right at a default assertion timeout's boundary, so give this
		// one explicit margin rather than rely on the test's own (already generous) timeout.
		await expect.element(suspendButton, {timeout: 8000}).not.toBeDisabled();
		await expect
			.poll(() => notificationsStore.notifications)
			.toEqual([
				expect.objectContaining({
					kind: 'warning',
					title: "Couldn't confirm batch operation status",
					subtitle: 'The action was sent. Refresh the page to see its current state.',
				}),
			]);
	}, 10000);

	it('should warn that status is unconfirmed rather than claim failure when the action request itself times out', async ({
		worker,
	}) => {
		// delay: 'infinite' never resolves the mock, so it's withTimeout's own bound — not the mock
		// settling — that ends the wait. The command may still be processed server-side once we give
		// up on it, so this must not tell the user the action failed (it might not have).
		worker.use(
			mockSuspendBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204}), delay: 'infinite'}),
		);

		const screen = await renderActions('ACTIVE');
		const suspendButton = screen.getByRole('button', {name: 'Suspend'});
		await userEvent.click(suspendButton);

		await expect.element(suspendButton).toBeDisabled();
		// The mocked POST is held for the full REQUEST_TIMEOUT_MS (10s) before withTimeout gives up on
		// it, past the default assertion timeout — give this one explicit margin.
		await expect.element(suspendButton, {timeout: 12000}).not.toBeDisabled();
		await expect
			.poll(() => notificationsStore.notifications, {timeout: 12000})
			.toEqual([
				expect.objectContaining({
					kind: 'warning',
					title: "Couldn't confirm batch operation status",
					subtitle: 'The action was sent. Refresh the page to see its current state.',
				}),
			]);
	}, 15000);

	it('should notify and redirect immediately when the poll discovers the batch operation is gone', async ({worker}) => {
		worker.use(
			mockSuspendBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 404}), {status: 404}),
			}),
		);

		const screen = await renderActions('ACTIVE');
		await userEvent.click(screen.getByRole('button', {name: 'Suspend'}));

		// A confirmed 404 is terminal — it must not spend the poll's retry budget rediscovering it, so
		// this settles almost immediately rather than after MAX_POLL_ATTEMPTS.
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
		worker.use(
			mockSuspendBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(createBatchOperation({state: 'ACTIVE'})),
			}),
		);

		const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});

		const firstRender = await renderWithSharedRouter(queryClient, 'ACTIVE');
		await userEvent.click(firstRender.getByRole('button', {name: 'Suspend'}));
		await expect.element(firstRender.getByRole('button', {name: 'Suspend'})).toBeDisabled();

		await firstRender.unmount();

		const secondRender = await renderWithSharedRouter(queryClient, 'ACTIVE');

		// The first instance's mutation is still polling in the background — a fresh mount must not
		// show a freshly-enabled control that would let the user fire a duplicate suspend command.
		await expect.element(secondRender.getByRole('button', {name: 'Suspend'})).toBeDisabled();

		worker.use(
			mockGetBatchOperationEndpoint({successResponse: HttpResponse.json(createBatchOperation({state: 'SUSPENDED'}))}),
		);

		await expect.element(secondRender.getByRole('button', {name: 'Suspend'})).not.toBeDisabled();
	});

	it('should track two concurrently pending actions independently across a remount', async ({worker}) => {
		worker.use(
			mockSuspendBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204}), delay: 200}),
			mockCancelBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204}), delay: 'infinite'}),
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(createBatchOperation({state: 'ACTIVE'})),
			}),
		);

		const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});

		const firstRender = await renderWithSharedRouter(queryClient, 'ACTIVE');
		// ACTIVE offers both Suspend and Cancel at once — start both before either settles.
		await userEvent.click(firstRender.getByRole('button', {name: 'Suspend'}));
		await userEvent.click(firstRender.getByRole('button', {name: 'More actions'}));
		await userEvent.click(firstRender.getByRole('menuitem', {name: 'Cancel'}));

		await firstRender.unmount();

		const secondRender = await renderWithSharedRouter(queryClient, 'ACTIVE');

		// Both must still be tracked after the remount, independently of each other.
		await expect.element(secondRender.getByRole('button', {name: 'Suspend'})).toBeDisabled();
		await userEvent.click(secondRender.getByRole('button', {name: 'More actions'}));
		await expect.element(secondRender.getByRole('menuitem', {name: 'Cancel'})).toBeDisabled();

		worker.use(
			mockGetBatchOperationEndpoint({successResponse: HttpResponse.json(createBatchOperation({state: 'SUSPENDED'}))}),
		);

		// Suspend settling must not clear cancel's still-pending entry. The menu opened by the click
		// above is still open (nothing has closed it), so re-check the same item without reopening —
		// clicking the trigger again would toggle it shut instead.
		await expect.element(secondRender.getByRole('button', {name: 'Suspend'})).not.toBeDisabled();
		await expect.element(secondRender.getByRole('menuitem', {name: 'Cancel'})).toBeDisabled();

		// Cancel's POST was mocked with an infinite delay so every assertion above is guaranteed to
		// observe it still pending, regardless of timing — but a request already dispatched against
		// that handler stays bound to it; swapping the mock here would only affect a *new* request, and
		// there isn't one. Left unresolved, useBatchOperationActions' own REQUEST_TIMEOUT_MS is what
		// actually bounds it, settling as "unconfirmed" (see the dedicated POST-timeout test) rather
		// than "failed" and clearing the pending marker — so this test doesn't leak a permanently
		// pending mutation into whichever test runs next against this shared worker.
		await expect.element(secondRender.getByRole('menuitem', {name: 'Cancel'}), {timeout: 12000}).not.toBeDisabled();
	}, 15000);

	it('should keep a control disabled across a hard refresh, not just an in-app remount', async ({worker}) => {
		// A hard refresh recreates the QueryClient from scratch — there's no earlier mutation or
		// in-memory pending state to inherit, only what a previous tab already persisted before the
		// refresh. Seed exactly that (bypassing a real click, which would leave an actual mutation
		// running with nothing left to unmount it) to isolate the resume-on-mount behavior itself.
		sessionStorage.setItem(`batchOperationPendingAction:${BATCH_OPERATION_KEY}:suspend`, String(Date.now() + 60000));

		worker.use(
			mockGetBatchOperationEndpoint({successResponse: HttpResponse.json(createBatchOperation({state: 'ACTIVE'}))}),
		);

		const screen = await renderActions('ACTIVE');

		// Resumed on mount purely from the persisted marker — no command was sent this render, only
		// waiting for the one a previous tab already sent.
		await expect.element(screen.getByRole('button', {name: 'Suspend'})).toBeDisabled();

		worker.use(
			mockGetBatchOperationEndpoint({successResponse: HttpResponse.json(createBatchOperation({state: 'SUSPENDED'}))}),
		);
		await expect.element(screen.getByRole('button', {name: 'Suspend'})).not.toBeDisabled();
		expect(sessionStorage.getItem(`batchOperationPendingAction:${BATCH_OPERATION_KEY}:suspend`)).toBeNull();
	});
});
