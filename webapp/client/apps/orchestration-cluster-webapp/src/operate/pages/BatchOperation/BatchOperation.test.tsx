/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect} from 'vitest';
import {render} from 'vitest-browser-react';
import {HttpResponse} from 'msw';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockGetBatchOperationEndpoint,
	mockQueryBatchOperationItemsEndpoint,
	mockSuspendBatchOperationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createBatchOperation,
	createQueryBatchOperationItemsResponse,
} from '#/shared-test-modules/api-mocks/batch-operations';
import {createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {BatchOperation, BatchOperationSkeleton} from './BatchOperation';

const BATCH_OPERATION_KEY = 'migrate-operation-123';

const EMPTY_ITEMS_RESPONSE = HttpResponse.json(createQueryBatchOperationItemsResponse());

function renderPage() {
	return renderWithRouter(() => <BatchOperation batchOperationKey={BATCH_OPERATION_KEY} />, {
		path: '/operate/batch-operations/$batchOperationKey',
		initialEntry: `/operate/batch-operations/${BATCH_OPERATION_KEY}`,
	});
}

describe('<BatchOperation />', () => {
	afterEach(() => {
		notificationsStore.reset();
	});

	it('should render the page title and operation details tiles', async ({worker}) => {
		worker.use(
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(
					createBatchOperation({
						batchOperationKey: BATCH_OPERATION_KEY,
						batchOperationType: 'MIGRATE_PROCESS_INSTANCE',
					}),
				),
			}),
			mockQueryBatchOperationItemsEndpoint({successResponse: EMPTY_ITEMS_RESPONSE}),
		);

		const screen = await renderPage();

		await expect.element(screen.getByRole('heading', {name: 'Migrate Process Instance'})).toBeVisible();
		await expect.element(screen.getByText('Summary of items')).toBeVisible();
		await expect.element(screen.getByText('Start date')).toBeVisible();
		await expect.element(screen.getByText('End date')).toBeVisible();
		await expect.element(screen.getByText('Actor')).toBeVisible();
	});

	it('should render the batch state and actor', async ({worker}) => {
		worker.use(
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(
					createBatchOperation({batchOperationKey: BATCH_OPERATION_KEY, state: 'COMPLETED', actorId: 'demo'}),
				),
			}),
			mockQueryBatchOperationItemsEndpoint({successResponse: EMPTY_ITEMS_RESPONSE}),
		);

		const screen = await renderPage();

		await expect.element(screen.getByText(/^Completed$/)).toBeVisible();
		await expect.element(screen.getByText('demo')).toBeVisible();
	});

	it('should render suspend and cancel actions for an active batch operation', async ({worker}) => {
		worker.use(
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(
					createBatchOperation({batchOperationKey: BATCH_OPERATION_KEY, state: 'ACTIVE'}),
				),
			}),
			mockQueryBatchOperationItemsEndpoint({successResponse: EMPTY_ITEMS_RESPONSE}),
		);

		const screen = await renderPage();

		await expect.element(screen.getByRole('button', {name: 'Suspend'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'More actions'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Resume'})).not.toBeInTheDocument();
	});

	it('should show an error notification when the batch operation fails to load', async ({worker}) => {
		worker.use(
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
			mockQueryBatchOperationItemsEndpoint({successResponse: EMPTY_ITEMS_RESPONSE}),
		);

		const screen = await renderPage();

		await expect.element(screen.getByText('Failed to load batch operation details')).toBeVisible();
	});

	it('should show the forbidden state when the user lacks permission', async ({worker}) => {
		worker.use(
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 403}), {status: 403}),
			}),
			mockQueryBatchOperationItemsEndpoint({successResponse: EMPTY_ITEMS_RESPONSE}),
		);

		const screen = await renderPage();

		await expect.element(screen.getByText('403 - You do not have permission to view this information')).toBeVisible();
	});

	it('should redirect to the batch operations list and notify when the batch operation is not found', async ({
		worker,
	}) => {
		worker.use(
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 404}), {status: 404}),
			}),
			mockQueryBatchOperationItemsEndpoint({successResponse: EMPTY_ITEMS_RESPONSE}),
		);

		const renderedErrors: string[] = [];
		// Capture transient alerts that disappear before the redirect completes.
		const observer = new MutationObserver((mutations) => {
			for (const mutation of mutations) {
				for (const node of mutation.addedNodes) {
					if (node.textContent?.includes('Failed to load batch operation details')) {
						renderedErrors.push(node.textContent);
					}
				}
			}
		});
		observer.observe(document.body, {childList: true, subtree: true});

		try {
			const screen = await renderPage();

			await expect.poll(() => screen.router.state.location.pathname).toBe('/operate/batch-operations');
			await expect
				.poll(() => notificationsStore.notifications.map((notification) => notification.title))
				.toContain(`Batch operation ${BATCH_OPERATION_KEY} could not be found`);
			expect(renderedErrors).toEqual([]);
		} finally {
			observer.disconnect();
		}
	});

	it('should render normally on a later visit that finds the batch operation readable again', async ({worker}) => {
		worker.use(
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 404}), {status: 404}),
			}),
			mockQueryBatchOperationItemsEndpoint({successResponse: EMPTY_ITEMS_RESPONSE}),
		);

		const firstVisit = await renderPage();
		await expect.poll(() => firstVisit.router.state.location.pathname).toBe('/operate/batch-operations');
		await expect
			.poll(() => notificationsStore.notifications.map((notification) => notification.title))
			.toContain(`Batch operation ${BATCH_OPERATION_KEY} could not be found`);
		await firstVisit.unmount();
		notificationsStore.reset();

		worker.use(
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(
					createBatchOperation({batchOperationKey: BATCH_OPERATION_KEY, batchOperationType: 'CANCEL_PROCESS_INSTANCE'}),
				),
			}),
		);

		const secondVisit = await renderPage();

		await expect.element(secondVisit.getByRole('heading', {name: 'Cancel Process Instance'})).toBeVisible();
		expect(secondVisit.router.state.location.pathname).toBe(`/operate/batch-operations/${BATCH_OPERATION_KEY}`);
		expect(notificationsStore.notifications).toEqual([]);
	});

	it('should redirect and notify when a follow-up read after a successful action finds it gone', async ({worker}) => {
		worker.use(
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(
					createBatchOperation({batchOperationKey: BATCH_OPERATION_KEY, state: 'ACTIVE'}),
				),
			}),
			mockQueryBatchOperationItemsEndpoint({successResponse: EMPTY_ITEMS_RESPONSE}),
			mockSuspendBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);

		const screen = await renderPage();
		await userEvent.click(screen.getByRole('button', {name: 'Suspend'}));

		worker.use(
			mockGetBatchOperationEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 404}), {status: 404}),
			}),
		);

		await expect.poll(() => screen.router.state.location.pathname, {timeout: 8000}).toBe('/operate/batch-operations');
		await expect
			.poll(() => notificationsStore.notifications, {timeout: 8000})
			.toEqual([
				expect.objectContaining({
					kind: 'error',
					title: `Batch operation ${BATCH_OPERATION_KEY} could not be found`,
				}),
			]);
	}, 10000);
});

describe('<BatchOperationSkeleton />', () => {
	it('should render placeholder tiles for every tile the loaded page shows', async () => {
		const screen = await render(<BatchOperationSkeleton />);

		await expect.element(screen.getByText('Batch state')).toBeVisible();
		await expect.element(screen.getByText('Summary of items')).toBeVisible();
		await expect.element(screen.getByText('Start date')).toBeVisible();
		await expect.element(screen.getByText('End date')).toBeVisible();
		await expect.element(screen.getByText('Actor')).toBeVisible();
	});
});
