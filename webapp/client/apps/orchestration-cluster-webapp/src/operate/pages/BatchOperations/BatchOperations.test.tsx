/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {describe, expect} from 'vitest';
import {HttpResponse} from 'msw';
import {mockQueryBatchOperationsEndpoint} from '#/shared-test-modules/mock-handlers';
import {
	createBatchOperation,
	createQueryBatchOperationsResponse,
} from '#/shared-test-modules/api-mocks/batch-operations';
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

		await expect.element(screen.getByText('5')).toBeVisible();
	});

	it('should render pagination with total item count', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: RESPONSE_WITH_OPERATIONS}));

		const screen = await renderPage();

		await expect.element(screen.getByText('1–2 of 2 items')).toBeVisible();
	});
});
