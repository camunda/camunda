/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {describe, expect, vi} from 'vitest';
import {HttpResponse} from 'msw';
import {mockQueryBatchOperationsEndpoint} from '#/shared-test-modules/mock-handlers';
import {
	createBatchOperation,
	createQueryBatchOperationsResponse,
} from '#/shared-test-modules/api-mocks/batch-operations';
import {tracking} from '#/shared/tracking';
import {BatchOperations} from './BatchOperations';

const EMPTY_RESPONSE = HttpResponse.json(createQueryBatchOperationsResponse());

const RESPONSE_WITH_OPERATIONS = HttpResponse.json(
	createQueryBatchOperationsResponse({
		items: [
			createBatchOperation({
				batchOperationKey: 'op-1',
				batchOperationType: 'CANCEL_PROCESS_INSTANCE',
				state: 'COMPLETED',
				actorId: 'demo',
				operationsTotalCount: 5,
				operationsCompletedCount: 5,
				operationsFailedCount: 0,
			}),
			createBatchOperation({
				batchOperationKey: 'op-2',
				batchOperationType: 'RESOLVE_INCIDENT',
				state: 'ACTIVE',
				actorId: 'admin',
				operationsTotalCount: 10,
				operationsCompletedCount: 3,
				operationsFailedCount: 1,
			}),
		],
		page: {totalItems: 2, startCursor: null, endCursor: null, hasMoreTotalItems: false},
	}),
);

const RESPONSE_EXCEEDING_PAGE_SIZE = HttpResponse.json(
	createQueryBatchOperationsResponse({
		items: [createBatchOperation({batchOperationKey: 'op-1', batchOperationType: 'RESOLVE_INCIDENT'})],
		page: {totalItems: 25, startCursor: null, endCursor: null, hasMoreTotalItems: false},
	}),
);

function renderPage(props?: {page?: number; pageSize?: number; sort?: string}) {
	return renderWithRouter(
		() => <BatchOperations page={props?.page ?? 1} pageSize={props?.pageSize ?? 20} sort={props?.sort} />,
		{path: '/operate/batch-operations'},
	);
}

describe('<BatchOperations />', () => {
	it('should render empty state when there are no batch operations', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: EMPTY_RESPONSE}));

		const screen = await renderPage();

		await expect.element(screen.getByText('No batch operations found')).toBeVisible();
	});

	it('should render batch operations in the table', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: RESPONSE_WITH_OPERATIONS}));

		const screen = await renderPage();

		await expect.element(screen.getByText('Cancel Process Instance')).toBeVisible();
		await expect.element(screen.getByText('Resolve Incident')).toBeVisible();
		await expect.element(screen.getByText('demo')).toBeVisible();
		await expect.element(screen.getByText('admin')).toBeVisible();
	});

	it('should render batch operation states', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: RESPONSE_WITH_OPERATIONS}));

		const screen = await renderPage();

		// Use regex to avoid case-insensitive collision with "completed" from item count labels
		await expect.element(screen.getByText(/^Completed$/)).toBeVisible();
		await expect.element(screen.getByText(/^Active$/)).toBeVisible();
	});

	it('should render operation type links to the detail page', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: RESPONSE_WITH_OPERATIONS}));

		const screen = await renderPage();

		await expect.element(screen.getByRole('link', {name: 'Cancel Process Instance'})).toBeVisible();
	});

	it('should render item counts for each operation', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: RESPONSE_WITH_OPERATIONS}));

		const screen = await renderPage();

		await expect.element(screen.getByText('5', {exact: true})).toBeVisible();
	});

	it('should not render pagination when all items fit on one page', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: RESPONSE_WITH_OPERATIONS}));

		const screen = await renderPage();

		await expect.element(screen.getByText('Resolve Incident')).toBeVisible();
		await expect.element(screen.getByText('Items per page:')).not.toBeInTheDocument();
	});

	it('should render pagination when items exceed the page size', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: RESPONSE_EXCEEDING_PAGE_SIZE}));

		const screen = await renderPage();

		await expect.element(screen.getByText('1–20 of 25 items')).toBeVisible();
	});

	it('should track when an operation detail link is opened', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: RESPONSE_WITH_OPERATIONS}));
		const trackSpy = vi.spyOn(tracking, 'track');

		const screen = await renderPage();

		// The detail route is not migrated yet, so the link is a hard anchor; prevent the
		// browser test harness from following it while still letting the click handler fire.
		document.addEventListener('click', (event) => event.preventDefault(), {capture: true, once: true});
		await screen.getByRole('link', {name: 'Cancel Process Instance'}).click();

		expect(trackSpy).toHaveBeenCalledWith({
			eventName: 'operate:batch-operation-details-opened',
			batchOperationType: 'CANCEL_PROCESS_INSTANCE',
			batchOperationState: 'COMPLETED',
		});

		trackSpy.mockRestore();
	});

	it('should track when a column is sorted', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: RESPONSE_WITH_OPERATIONS}));
		const trackSpy = vi.spyOn(tracking, 'track');

		const screen = await renderPage();

		await screen.getByRole('button', {name: 'Operation'}).click();

		expect(trackSpy).toHaveBeenCalledWith({
			eventName: 'operate:batch-operations-sorted',
			sortBy: 'operationType',
			sortOrder: 'desc',
		});

		trackSpy.mockRestore();
	});
});
