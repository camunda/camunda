/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {HttpResponse} from 'msw';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockQueryBatchOperationItemsEndpoint} from '#/shared-test-modules/mock-handlers';
import {
	createBatchOperationItem,
	createQueryBatchOperationItemsResponse,
} from '#/shared-test-modules/api-mocks/batch-operations';
import {createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {BatchItemsTable} from './BatchItemsTable';

const BATCH_OPERATION_KEY = 'migrate-operation-123';
const ITEMS_LOAD_ERROR = HttpResponse.json(createProblemDetails({status: 500}), {status: 500});
const SINGLE_PAGE = {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false};

function renderBatchItemsTable(batchOperationType: React.ComponentProps<typeof BatchItemsTable>['batchOperationType']) {
	return renderWithRouter(
		() => (
			<div style={{height: '100vh', display: 'flex', flexDirection: 'column'}}>
				<BatchItemsTable batchOperationKey={BATCH_OPERATION_KEY} batchOperationType={batchOperationType} />
			</div>
		),
		{
			path: '/operate-preview/batch-operations/$batchOperationKey',
			initialEntry: `/operate-preview/batch-operations/${BATCH_OPERATION_KEY}`,
		},
	);
}

describe('<BatchItemsTable />', () => {
	it('should show a loading table instead of the empty state until the first response arrives', async ({worker}) => {
		worker.use(
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse()),
				delay: 'infinite',
			}),
		);

		const screen = await renderBatchItemsTable('CANCEL_PROCESS_INSTANCE');

		await expect.element(screen.getByRole('table')).toBeVisible();
		await expect.element(screen.getByText('No items found')).not.toBeInTheDocument();
	});

	it('should render the empty state when there are no items', async ({worker}) => {
		worker.use(
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse()),
			}),
		);

		const screen = await renderBatchItemsTable('CANCEL_PROCESS_INSTANCE');

		await expect.element(screen.getByText('No items found')).toBeVisible();
	});

	it('should expose an initial failure and allow retry', async ({worker}) => {
		worker.use(mockQueryBatchOperationItemsEndpoint({successResponse: ITEMS_LOAD_ERROR}));

		const screen = await renderBatchItemsTable('CANCEL_PROCESS_INSTANCE');

		await expect.element(screen.getByText("Couldn't fetch data")).toBeVisible();
		await expect.element(screen.getByText('No items found')).not.toBeInTheDocument();
		worker.use(
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse()),
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Retry'}));

		await expect.element(screen.getByText('No items found')).toBeVisible();
		await expect.element(screen.getByText("Couldn't fetch data")).not.toBeInTheDocument();
	});

	it('should warn on a failed refetch without hiding cached links or item failures, then recover', async ({worker}) => {
		worker.use(
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(
					createQueryBatchOperationItemsResponse({
						items: [
							createBatchOperationItem({
								state: 'FAILED',
								errorMessage: 'Failed to migrate',
								processInstanceKey: '2251799813685250',
							}),
						],
						page: {totalItems: 60, hasMoreTotalItems: true},
					}),
				),
			}),
		);

		const screen = await renderBatchItemsTable('MIGRATE_PROCESS_INSTANCE');
		const link = screen.getByRole('link', {name: 'View process instance 2251799813685250'});
		await expect.element(link).toBeVisible();
		worker.use(mockQueryBatchOperationItemsEndpoint({successResponse: ITEMS_LOAD_ERROR}));
		window.dispatchEvent(new Event('visibilitychange'));

		await expect.element(screen.getByText("Couldn't fetch data")).toBeVisible();
		await expect.element(link).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: 'Show failure details'}));
		await expect.element(screen.getByText('Failure reason: Failed to migrate')).toBeVisible();
		worker.use(
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(
					createQueryBatchOperationItemsResponse({
						items: [createBatchOperationItem({processInstanceKey: '2251799813685251'})],
					}),
				),
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Retry'}));

		await expect.element(screen.getByRole('link', {name: 'View process instance 2251799813685251'})).toBeVisible();
		await expect.element(screen.getByText("Couldn't fetch data")).not.toBeInTheDocument();
	});

	it('should render a process instance link for a non-completed process instance deletion item', async ({worker}) => {
		worker.use(
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(
					createQueryBatchOperationItemsResponse({
						items: [
							createBatchOperationItem({
								state: 'FAILED',
								processInstanceKey: '2251799813685250',
								operationType: 'DELETE_PROCESS_INSTANCE',
							}),
						],
						page: SINGLE_PAGE,
					}),
				),
			}),
		);

		const screen = await renderBatchItemsTable('DELETE_PROCESS_INSTANCE');

		await expect.element(screen.getByRole('link', {name: 'View process instance 2251799813685250'})).toBeVisible();
	});

	it('should show a fallback instead of a link for an item without a process instance', async ({worker}) => {
		worker.use(
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(
					createQueryBatchOperationItemsResponse({
						items: [createBatchOperationItem({processInstanceKey: '-1'})],
						page: SINGLE_PAGE,
					}),
				),
			}),
		);

		const screen = await renderBatchItemsTable('CANCEL_PROCESS_INSTANCE');

		await expect.element(screen.getByText('No process instance')).toBeVisible();
		await expect.element(screen.getByRole('link')).not.toBeInTheDocument();
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
						page: SINGLE_PAGE,
					}),
				),
			}),
		);

		const screen = await renderBatchItemsTable('DELETE_PROCESS_INSTANCE');

		await expect.element(screen.getByText('2251799813685250')).toBeVisible();
		await expect.element(screen.getByRole('link')).not.toBeInTheDocument();
	});

	it('should render a decision instance key column with a link to the decision instance', async ({worker}) => {
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
						page: SINGLE_PAGE,
					}),
				),
			}),
		);

		const screen = await renderBatchItemsTable('DELETE_DECISION_INSTANCE');

		await expect.element(screen.getByText('Decision instance key', {exact: true})).toBeVisible();
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
						page: SINGLE_PAGE,
					}),
				),
			}),
		);

		const screen = await renderBatchItemsTable('RESOLVE_INCIDENT');

		await expect.element(screen.getByText('Incident key', {exact: true})).toBeVisible();
		await expect.element(screen.getByText('incident-key-1')).toBeVisible();
		await expect.element(screen.getByRole('link', {name: 'View process instance 2251799813685250'})).toBeVisible();
	});

	it('should show the failure reason of a failed item in an expandable row', async ({worker}) => {
		worker.use(
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(
					createQueryBatchOperationItemsResponse({
						items: [
							createBatchOperationItem({itemKey: 'failed-item', state: 'FAILED', errorMessage: 'Something went wrong'}),
							createBatchOperationItem({itemKey: 'completed-item', state: 'COMPLETED'}),
						],
						page: {totalItems: 2, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				),
			}),
		);

		const screen = await renderBatchItemsTable('CANCEL_PROCESS_INSTANCE');

		await expect.element(screen.getByText(/^Failed$/)).toBeVisible();
		await expect.element(screen.getByText('Failure reason: Something went wrong')).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Show failure details'})).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Show failure details'}));

		await expect.element(screen.getByText('Failure reason: Something went wrong')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Hide failure details'})).toBeVisible();
	});
});
