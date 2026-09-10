/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockQueryBatchOperationItemsEndpoint} from '#/shared-test-modules/mock-handlers';
import {
	createBatchOperationItem,
	createQueryBatchOperationItemsResponse,
} from '#/shared-test-modules/api-mocks/batch-operations';
import {BatchItemsTable} from './BatchItemsTable';

const BATCH_OPERATION_KEY = 'migrate-operation-123';

describe('<BatchItemsTable />', () => {
	it('should render the empty state when there are no items', async ({worker}) => {
		worker.use(
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse()),
			}),
		);

		const screen = await renderWithRouter(
			() => <BatchItemsTable batchOperationKey={BATCH_OPERATION_KEY} batchOperationType="CANCEL_PROCESS_INSTANCE" />,
			{path: '/operate/batch-operations'},
		);

		await expect.element(screen.getByText('No items found')).toBeVisible();
	});

	it('should render a process instance key column with a link for a non-completed item', async ({worker}) => {
		worker.use(
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(
					createQueryBatchOperationItemsResponse({
						items: [createBatchOperationItem({state: 'FAILED', processInstanceKey: '2251799813685250'})],
						page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				),
			}),
		);

		const screen = await renderWithRouter(
			() => <BatchItemsTable batchOperationKey={BATCH_OPERATION_KEY} batchOperationType="CANCEL_PROCESS_INSTANCE" />,
			{path: '/operate/batch-operations'},
		);

		const link = screen.getByRole('link', {name: 'View process instance 2251799813685250'});
		await expect.element(link).toBeVisible();
		await expect.element(link).toHaveAttribute('href', '/operate/processes/2251799813685250');
	});

	it('should still render the process instance link for a completed item of most operation types', async ({worker}) => {
		worker.use(
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(
					createQueryBatchOperationItemsResponse({
						items: [createBatchOperationItem({state: 'COMPLETED', processInstanceKey: '2251799813685250'})],
						page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				),
			}),
		);

		const screen = await renderWithRouter(
			() => <BatchItemsTable batchOperationKey={BATCH_OPERATION_KEY} batchOperationType="CANCEL_PROCESS_INSTANCE" />,
			{path: '/operate/batch-operations'},
		);

		await expect.element(screen.getByRole('link', {name: 'View process instance 2251799813685250'})).toBeVisible();
	});

	it('should not render a process instance link for a completed process instance deletion item', async ({worker}) => {
		worker.use(
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(
					createQueryBatchOperationItemsResponse({
						items: [
							createBatchOperationItem({
								state: 'COMPLETED',
								processInstanceKey: '2251799813685250',
								operationType: 'DELETE_PROCESS_INSTANCE',
							}),
						],
						page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				),
			}),
		);

		const screen = await renderWithRouter(
			() => <BatchItemsTable batchOperationKey={BATCH_OPERATION_KEY} batchOperationType="DELETE_PROCESS_INSTANCE" />,
			{path: '/operate/batch-operations'},
		);

		await expect.element(screen.getByText('2251799813685250')).toBeVisible();
		await expect.element(screen.getByRole('link')).not.toBeInTheDocument();
	});

	it('should render a decision instance key column for a decision instance deletion operation', async ({worker}) => {
		worker.use(
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(
					createQueryBatchOperationItemsResponse({
						items: [
							createBatchOperationItem({
								itemKey: 'item-1',
								state: 'FAILED',
								operationType: 'DELETE_DECISION_INSTANCE',
							}),
						],
						page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				),
			}),
		);

		const screen = await renderWithRouter(
			() => <BatchItemsTable batchOperationKey={BATCH_OPERATION_KEY} batchOperationType="DELETE_DECISION_INSTANCE" />,
			{path: '/operate/batch-operations'},
		);

		await expect.element(screen.getByRole('columnheader', {name: 'Decision instance key'})).toBeVisible();
		await expect.element(screen.getByRole('link', {name: 'View decision instance item-1'})).toBeVisible();
	});

	it('should render an incident key column for a resolve incident operation', async ({worker}) => {
		worker.use(
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(
					createQueryBatchOperationItemsResponse({
						items: [
							createBatchOperationItem({
								itemKey: 'incident-key-1',
								processInstanceKey: '2251799813685250',
								state: 'COMPLETED',
								operationType: 'RESOLVE_INCIDENT',
							}),
						],
						page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				),
			}),
		);

		const screen = await renderWithRouter(
			() => <BatchItemsTable batchOperationKey={BATCH_OPERATION_KEY} batchOperationType="RESOLVE_INCIDENT" />,
			{path: '/operate/batch-operations'},
		);

		await expect.element(screen.getByRole('columnheader', {name: 'Incident key'})).toBeVisible();
		await expect.element(screen.getByText('incident-key-1')).toBeVisible();
		await expect.element(screen.getByRole('link', {name: 'View process instance 2251799813685250'})).toBeVisible();
	});

	it('should render a failed item with its state', async ({worker}) => {
		worker.use(
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(
					createQueryBatchOperationItemsResponse({
						items: [createBatchOperationItem({state: 'FAILED', errorMessage: 'Something went wrong'})],
						page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				),
			}),
		);

		const screen = await renderWithRouter(
			() => <BatchItemsTable batchOperationKey={BATCH_OPERATION_KEY} batchOperationType="CANCEL_PROCESS_INSTANCE" />,
			{path: '/operate/batch-operations'},
		);

		await expect.element(screen.getByText(/^Failed$/)).toBeVisible();
	});
});
