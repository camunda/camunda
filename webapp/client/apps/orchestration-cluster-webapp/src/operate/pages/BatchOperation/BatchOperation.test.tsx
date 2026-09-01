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
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockGetBatchOperationEndpoint, mockQueryBatchOperationItemsEndpoint} from '#/shared-test-modules/mock-handlers';
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

		const screen = await renderPage();

		await expect.poll(() => screen.router.state.location.pathname).toBe('/operate/batch-operations');
		await expect
			.poll(() => notificationsStore.notifications.map((notification) => notification.title))
			.toContain(`Batch operation ${BATCH_OPERATION_KEY} could not be found`);
	});
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
