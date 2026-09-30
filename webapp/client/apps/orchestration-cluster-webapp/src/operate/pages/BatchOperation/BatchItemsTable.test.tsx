/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {describe, expect} from 'vitest';
import {HttpResponse} from 'msw';
import {userEvent} from 'vitest/browser';
import {z} from 'zod';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockQueryBatchOperationItemsEndpoint} from '#/shared-test-modules/mock-handlers';
import {
	createBatchOperationItem,
	createQueryBatchOperationItemsResponse,
} from '#/shared-test-modules/api-mocks/batch-operations';
import {createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {BatchItemsTable} from './BatchItemsTable';
import {useBatchOperationItems} from './useBatchOperationItems';

const BATCH_OPERATION_KEY = 'migrate-operation-123';
const BATCH_OPERATION_LIST_PATH = '/operate/batch-operations';
const ITEMS_LOAD_ERROR = HttpResponse.json(createProblemDetails({status: 500}), {status: 500});

function renderBatchItemsTable(
	batchOperationType: React.ComponentProps<typeof BatchItemsTable>['batchOperationType'],
	basepath = '',
) {
	return renderWithRouter(
		() => (
			<div style={{height: '100vh', display: 'flex', flexDirection: 'column'}}>
				<BatchItemsTable batchOperationKey={BATCH_OPERATION_KEY} batchOperationType={batchOperationType} />
			</div>
		),
		{
			path: BATCH_OPERATION_LIST_PATH,
			basepath,
			initialEntry: `${basepath}${BATCH_OPERATION_LIST_PATH}`,
		},
	);
}

function renderPaginationHarness() {
	function Harness() {
		const {fetchNextPage, fetchPreviousPage} = useBatchOperationItems(BATCH_OPERATION_KEY);
		return (
			<div style={{height: '100vh', display: 'flex', flexDirection: 'column'}}>
				<button type="button" onClick={() => void fetchNextPage()}>
					Load next page
				</button>
				<button type="button" onClick={() => void fetchPreviousPage()}>
					Load previous page
				</button>
				<BatchItemsTable batchOperationKey={BATCH_OPERATION_KEY} batchOperationType="MIGRATE_PROCESS_INSTANCE" />
			</div>
		);
	}

	return renderWithRouter(Harness, {path: BATCH_OPERATION_LIST_PATH});
}

function paginatedItemsResponse(offset: number) {
	return HttpResponse.json(
		createQueryBatchOperationItemsResponse({
			items: [
				createBatchOperationItem({
					itemKey: `item-${offset}`,
					processInstanceKey: `2251799813685${offset}`,
				}),
			],
			page: {totalItems: 150, hasMoreTotalItems: true},
		}),
	);
}

describe('<BatchItemsTable />', () => {
	it('should show a skeleton instead of the empty state until the first response arrives', async ({worker}) => {
		worker.use(
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse()),
				delay: 'infinite',
			}),
		);

		const screen = await renderBatchItemsTable('CANCEL_PROCESS_INSTANCE');

		await expect.element(screen.getByTestId('batch-items-table')).not.toBeInTheDocument();
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

	it('should expose an initial failure and allow retry without leaving the detail page', async ({worker}) => {
		worker.use(mockQueryBatchOperationItemsEndpoint({successResponse: ITEMS_LOAD_ERROR}));

		const screen = await renderBatchItemsTable('CANCEL_PROCESS_INSTANCE');

		await expect.element(screen.getByText("Couldn't fetch data")).toBeVisible();
		await expect.element(screen.getByText('No items found')).not.toBeInTheDocument();
		worker.use(
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse()),
				delay: 1000,
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Retry'}));

		await expect.element(screen.getByRole('table')).toBeVisible();
		await expect.element(screen.getByText("Couldn't fetch data")).not.toBeInTheDocument();
		await expect.element(screen.getByText('No items found')).toBeVisible();
		await expect.element(screen.getByText("Couldn't fetch data")).not.toBeInTheDocument();
	});

	it('should expose a failed refetch of an empty result instead of reporting a successful empty state', async ({
		worker,
	}) => {
		worker.use(
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse()),
			}),
		);

		const screen = await renderBatchItemsTable('CANCEL_PROCESS_INSTANCE');
		await expect.element(screen.getByText('No items found')).toBeVisible();
		worker.use(mockQueryBatchOperationItemsEndpoint({successResponse: ITEMS_LOAD_ERROR}));
		await screen.queryClient.invalidateQueries({queryKey: ['batchOperationItems', BATCH_OPERATION_KEY]});

		await expect.element(screen.getByText("Couldn't fetch data")).toBeVisible();
		await expect.element(screen.getByText('No items found')).not.toBeInTheDocument();
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
		await expect.element(screen.getByRole('heading', {name: /60\+ results/})).toBeVisible();
		worker.use(mockQueryBatchOperationItemsEndpoint({successResponse: ITEMS_LOAD_ERROR}));
		await screen.queryClient.invalidateQueries({queryKey: ['batchOperationItems', BATCH_OPERATION_KEY]});

		await expect.element(screen.getByText("Couldn't fetch data")).toBeVisible();
		await expect.element(link).toBeVisible();
		await userEvent.hover(screen.getByTestId('item-state-with-error'));
		await expect.element(screen.getByText('Failure reason: Failed to migrate')).toBeVisible();
		worker.use(
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(
					createQueryBatchOperationItemsResponse({
						items: [createBatchOperationItem({processInstanceKey: '2251799813685251'})],
					}),
				),
				delay: 1000,
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Retry'}));

		await expect.element(screen.getByText('Retrying items')).toBeVisible();
		await expect.element(screen.getByRole('link', {name: 'View process instance 2251799813685251'})).toBeVisible();
		await expect.element(screen.getByText('Retrying items')).not.toBeInTheDocument();
		await expect.element(screen.getByText("Couldn't fetch data")).not.toBeInTheDocument();
	});

	it('should retry the failed next-page offset instead of reloading only cached pages', async ({worker}) => {
		worker.use(mockQueryBatchOperationItemsEndpoint({successResponse: paginatedItemsResponse(0), once: true}));

		const screen = await renderPaginationHarness();
		const firstLink = screen.getByRole('link', {name: 'View process instance 22517998136850'});
		await expect.element(firstLink).toBeVisible();
		worker.use(mockQueryBatchOperationItemsEndpoint({successResponse: ITEMS_LOAD_ERROR, once: true}));
		await userEvent.click(screen.getByRole('button', {name: 'Load next page'}));

		await expect.element(screen.getByText("Couldn't fetch data")).toBeVisible();
		await expect.element(firstLink).toBeVisible();
		worker.use(
			mockQueryBatchOperationItemsEndpoint({
				schema: z.object({page: z.object({from: z.literal(50), limit: z.literal(50)})}),
				successResponse: paginatedItemsResponse(50),
				failureResponse: ITEMS_LOAD_ERROR,
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Retry'}));

		await expect.element(screen.getByRole('link', {name: 'View process instance 225179981368550'})).toBeVisible();
		await expect.element(firstLink).toBeVisible();
		await expect.element(screen.getByText("Couldn't fetch data")).not.toBeInTheDocument();
	});

	it('should retry the failed previous-page offset and retain only the two-page window', async ({worker}) => {
		worker.use(mockQueryBatchOperationItemsEndpoint({successResponse: paginatedItemsResponse(0), once: true}));

		const screen = await renderPaginationHarness();
		const firstLink = screen.getByRole('link', {name: 'View process instance 22517998136850'});
		await expect.element(firstLink).toBeVisible();
		worker.use(mockQueryBatchOperationItemsEndpoint({successResponse: paginatedItemsResponse(50), once: true}));
		await userEvent.click(screen.getByRole('button', {name: 'Load next page'}));
		const secondLink = screen.getByRole('link', {name: 'View process instance 225179981368550'});
		await expect.element(secondLink).toBeVisible();
		worker.use(mockQueryBatchOperationItemsEndpoint({successResponse: paginatedItemsResponse(100), once: true}));
		await userEvent.click(screen.getByRole('button', {name: 'Load next page'}));
		const thirdLink = screen.getByRole('link', {name: 'View process instance 2251799813685100'});
		await expect.element(thirdLink).toBeVisible();
		await expect.element(firstLink).not.toBeInTheDocument();

		worker.use(mockQueryBatchOperationItemsEndpoint({successResponse: ITEMS_LOAD_ERROR, once: true}));
		await userEvent.click(screen.getByRole('button', {name: 'Load previous page'}));
		await expect.element(screen.getByText("Couldn't fetch data")).toBeVisible();
		await expect.element(thirdLink).toBeVisible();
		worker.use(
			mockQueryBatchOperationItemsEndpoint({
				schema: z.object({page: z.object({from: z.literal(0), limit: z.literal(50)})}),
				successResponse: paginatedItemsResponse(0),
				failureResponse: ITEMS_LOAD_ERROR,
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Retry'}));

		await expect.element(firstLink).toBeVisible();
		await expect.element(secondLink).toBeVisible();
		await expect.element(thirdLink).not.toBeInTheDocument();
		await expect.element(screen.getByText("Couldn't fetch data")).not.toBeInTheDocument();
	});

	it('should show a new loading state rather than cached rows when the operation key changes', async ({worker}) => {
		const nextKey = 'migrate-operation-456';
		worker.use(
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(
					createQueryBatchOperationItemsResponse({
						items: [createBatchOperationItem({processInstanceKey: '2251799813685250'})],
					}),
				),
				once: true,
			}),
		);

		function Harness() {
			const [key, setKey] = useState(BATCH_OPERATION_KEY);
			return (
				<>
					<button type="button" onClick={() => setKey(nextKey)}>
						Change operation
					</button>
					<BatchItemsTable batchOperationKey={key} batchOperationType="MIGRATE_PROCESS_INSTANCE" />
				</>
			);
		}

		const screen = await renderWithRouter(Harness, {path: BATCH_OPERATION_LIST_PATH});
		const oldLink = screen.getByRole('link', {name: 'View process instance 2251799813685250'});
		await expect.element(oldLink).toBeVisible();
		worker.use(
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(
					createQueryBatchOperationItemsResponse({
						items: [createBatchOperationItem({processInstanceKey: '2251799813685251'})],
					}),
				),
				delay: 400,
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Change operation'}));

		await expect.element(oldLink).not.toBeInTheDocument();
		await expect.element(screen.getByTestId('batch-items-table')).not.toBeInTheDocument();
		await expect.element(screen.getByText('No items found')).not.toBeInTheDocument();
		await expect.element(screen.getByRole('link', {name: 'View process instance 2251799813685251'})).toBeVisible();
	});

	it.for([
		{basepath: '', expectedPathPrefix: ''},
		{basepath: '/camunda', expectedPathPrefix: '/camunda'},
	])(
		'should render a process instance key column with a link for a non-completed item at basepath "$basepath"',
		async ({basepath, expectedPathPrefix}, {worker}) => {
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

			const screen = await renderBatchItemsTable('CANCEL_PROCESS_INSTANCE', basepath);

			const link = screen.getByRole('link', {name: 'View process instance 2251799813685250'});
			await expect.element(link).toBeVisible();
			await expect.element(link).toHaveAttribute('href', `${expectedPathPrefix}/operate/processes/2251799813685250`);
		},
	);

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

		const screen = await renderBatchItemsTable('CANCEL_PROCESS_INSTANCE');

		await expect.element(screen.getByRole('link', {name: 'View process instance 2251799813685250'})).toBeVisible();
	});

	it.for([
		{basepath: '', expectedPathPrefix: ''},
		{basepath: '/camunda', expectedPathPrefix: '/camunda'},
	])(
		'should render a process instance link for a non-completed process instance deletion item at basepath "$basepath"',
		async ({basepath, expectedPathPrefix}, {worker}) => {
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
							page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
						}),
					),
				}),
			);

			const screen = await renderBatchItemsTable('DELETE_PROCESS_INSTANCE', basepath);

			await expect
				.element(screen.getByRole('link', {name: 'View process instance 2251799813685250'}))
				.toHaveAttribute('href', `${expectedPathPrefix}/operate/processes/2251799813685250`);
		},
	);

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

		const screen = await renderBatchItemsTable('DELETE_PROCESS_INSTANCE');

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

		const screen = await renderBatchItemsTable('DELETE_DECISION_INSTANCE');

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

		const screen = await renderBatchItemsTable('RESOLVE_INCIDENT');

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

		const screen = await renderBatchItemsTable('CANCEL_PROCESS_INSTANCE');

		await expect.element(screen.getByText(/^Failed$/)).toBeVisible();
	});
});
