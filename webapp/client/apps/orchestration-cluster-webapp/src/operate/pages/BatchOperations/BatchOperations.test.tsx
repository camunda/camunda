/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {afterEach, describe, expect} from 'vitest';
import {HttpResponse} from 'msw';
import {userEvent} from 'vitest/browser';
import {useSearch} from '@tanstack/react-router';
import {z} from 'zod';
import i18n from 'i18next';
import {batchOperationsSearchSchema} from './batchOperations.queries';
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

// Legacy Operate batches and documents written before the exporter fix carry no type
const RESPONSE_WITH_UNTYPED_OPERATION = HttpResponse.json(
	createQueryBatchOperationsResponse({
		items: [
			createBatchOperation({batchOperationKey: 'op-1', batchOperationType: null}),
			createBatchOperation({batchOperationKey: 'op-2', batchOperationType: 'RESOLVE_INCIDENT'}),
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
		() => (
			<BatchOperations page={props?.page ?? 1} pageSize={props?.pageSize ?? 20} sort={props?.sort ?? 'endDate+desc'} />
		),
		{path: '/operate/batch-operations'},
	);
}

function renderRoute(initialEntry = '/operate/batch-operations') {
	return renderWithRouter(
		() => {
			const {page, pageSize, sort} = batchOperationsSearchSchema.parse(useSearch({strict: false}));
			return <BatchOperations page={page} pageSize={pageSize} sort={sort} />;
		},
		{path: '/operate/batch-operations', initialEntry},
	);
}

describe('<BatchOperations />', () => {
	afterEach(async () => {
		await i18n.changeLanguage('en');
	});

	it('should render empty state when there are no batch operations', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: EMPTY_RESPONSE}));

		const screen = await renderPage();

		await expect.element(screen.getByText('No batch operations found')).toBeVisible();
		await expect.element(screen.getByRole('columnheader')).not.toBeInTheDocument();
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

	it('should render a placeholder for a batch operation without a type', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: RESPONSE_WITH_UNTYPED_OPERATION}));

		const screen = await renderPage();

		await expect.element(screen.getByText('--')).toBeVisible();
		await expect.element(screen.getByText('Resolve Incident')).toBeVisible();
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

	it('should translate all visible pagination controls when the language changes', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: RESPONSE_EXCEEDING_PAGE_SIZE}));
		const screen = await renderRoute();
		await expect.element(screen.getByText('1–20 of 25 items')).toBeVisible();
		await expect.element(screen.getByRole('columnheader', {name: 'Batch state'})).toBeVisible();

		await i18n.changeLanguage('de');

		await expect.element(screen.getByText('1–20 von 25 Elementen')).toBeVisible();
		await expect.element(screen.getByRole('columnheader', {name: 'Stapelstatus'})).toBeVisible();
		await expect.element(screen.getByText('von 2 Seiten')).toBeVisible();
		await expect.element(screen.getByRole('combobox', {name: 'Elemente pro Seite:'})).toBeVisible();
		await expect.element(screen.getByRole('combobox', {name: 'Seite von 2 Seiten'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Nächste Seite'})).toBeVisible();
	});

	it('should show loading before a successful empty response', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: EMPTY_RESPONSE, delay: 250}));

		const screen = await renderRoute();

		await expect.element(screen.getByText('No batch operations found')).not.toBeInTheDocument();
		await expect.element(screen.getByText('No batch operations found')).toBeVisible();
		await expect.element(screen.getByRole('columnheader')).not.toBeInTheDocument();
	});

	it('should update the document title when the language changes', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: EMPTY_RESPONSE}));
		const screen = await renderRoute();
		await expect.element(screen.getByText('No batch operations found')).toBeVisible();

		await i18n.changeLanguage('de');
		await expect.element(screen.getByText('Keine Stapeloperationen gefunden')).toBeVisible();
		expect(document.title).toBe('Operate: Stapeloperationen');
	});

	it('should show loading while refreshing an empty list', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: EMPTY_RESPONSE}));
		const screen = await renderRoute();
		await expect.element(screen.getByText('No batch operations found')).toBeVisible();

		worker.use(mockQueryBatchOperationsEndpoint({successResponse: EMPTY_RESPONSE, delay: 300}));
		const refresh = screen.queryClient.invalidateQueries({queryKey: ['batchOperations']});

		await expect.element(screen.getByRole('columnheader', {name: 'Operation'})).toBeVisible();
		await expect.element(screen.getByText('No batch operations found')).not.toBeInTheDocument();
		await refresh;
		await expect.element(screen.getByText('No batch operations found')).toBeVisible();
	});

	it('should show an initial failure with a retry that recovers to the list', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: HttpResponse.json({}, {status: 500})}));

		const screen = await renderRoute();
		await expect.element(screen.getByRole('heading', {name: 'Something went wrong'})).toBeVisible();
		await expect.element(screen.getByText('No batch operations found')).not.toBeInTheDocument();

		worker.use(mockQueryBatchOperationsEndpoint({successResponse: RESPONSE_WITH_OPERATIONS}));
		await userEvent.click(screen.getByRole('button', {name: 'Try again'}));

		await expect.element(screen.getByRole('link', {name: 'Cancel Process Instance'})).toBeVisible();
	});

	it('should show a forbidden page instead of an empty list', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: HttpResponse.json({}, {status: 403})}));

		const screen = await renderRoute();

		await expect.element(screen.getByRole('heading', {name: 'You need permission'})).toBeVisible();
		await expect.element(screen.getByText('No batch operations found')).not.toBeInTheDocument();
	});

	it('should show a failed refresh while preserving rows and recover on retry', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: RESPONSE_WITH_OPERATIONS}));
		const screen = await renderRoute();
		await expect.element(screen.getByRole('link', {name: 'Cancel Process Instance'})).toBeVisible();

		worker.use(mockQueryBatchOperationsEndpoint({successResponse: HttpResponse.json({}, {status: 500})}));
		await screen.queryClient.invalidateQueries({queryKey: ['batchOperations']});

		await expect.element(screen.getByRole('alert')).toMatchTextContent('Failed to refresh batch operations');
		await expect.element(screen.getByRole('link', {name: 'Cancel Process Instance'})).toBeVisible();
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: EMPTY_RESPONSE}));
		await userEvent.click(screen.getByRole('button', {name: 'Try again'}));

		await expect.element(screen.getByText('No batch operations found')).toBeVisible();
		await expect.element(screen.getByRole('alert')).not.toBeInTheDocument();
	});

	it('should replace cached rows with forbidden access after a failed refresh', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: RESPONSE_WITH_OPERATIONS}));
		const screen = await renderRoute();
		await expect.element(screen.getByRole('link', {name: 'Cancel Process Instance'})).toBeVisible();

		worker.use(mockQueryBatchOperationsEndpoint({successResponse: HttpResponse.json({}, {status: 403})}));
		await screen.queryClient.invalidateQueries({queryKey: ['batchOperations']});

		await expect.element(screen.getByRole('heading', {name: 'You need permission'})).toBeVisible();
		await expect.element(screen.getByRole('link', {name: 'Cancel Process Instance'})).not.toBeInTheDocument();
	});

	it('should request sorted and paginated results and restore them through browser history', async ({worker}) => {
		const requestFor = (from: number, limit: number, field: string, order: 'asc' | 'desc') =>
			mockQueryBatchOperationsEndpoint({
				schema: z.object({
					page: z.object({from: z.literal(from), limit: z.literal(limit)}),
					sort: z.array(z.object({field: z.literal(field), order: z.literal(order)})),
				}),
				successResponse: RESPONSE_EXCEEDING_PAGE_SIZE,
				failureResponse: HttpResponse.json({}, {status: 500}),
			});
		worker.use(requestFor(0, 20, 'endDate', 'desc'));
		const screen = await renderRoute();
		await expect.element(screen.getByText('1–20 of 25 items')).toBeVisible();

		worker.use(requestFor(0, 20, 'actorId', 'desc'));
		await userEvent.click(screen.getByRole('columnheader', {name: 'Actor'}));
		await expect
			.poll(() => batchOperationsSearchSchema.parse(screen.router.state.location.search).sort)
			.toBe('actorId+desc');

		worker.use(requestFor(20, 20, 'actorId', 'desc'));
		await userEvent.click(screen.getByRole('button', {name: 'Next page'}));
		await expect.element(screen.getByText('21–25 of 25 items')).toBeVisible();

		screen.router.history.back();
		await expect.element(screen.getByText('1–20 of 25 items')).toBeVisible();
		expect(batchOperationsSearchSchema.parse(screen.router.state.location.search).sort).toBe('actorId+desc');
		screen.router.history.forward();
		await expect.element(screen.getByText('21–25 of 25 items')).toBeVisible();
	});

	it('should retain usable rows while requesting another page', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: RESPONSE_EXCEEDING_PAGE_SIZE}));
		const screen = await renderRoute();
		await expect.element(screen.getByRole('link', {name: 'Resolve Incident'})).toBeVisible();

		worker.use(mockQueryBatchOperationsEndpoint({successResponse: EMPTY_RESPONSE, delay: 300}));
		await userEvent.click(screen.getByRole('button', {name: 'Next page'}));

		await expect.element(screen.getByRole('link', {name: 'Resolve Incident'})).toBeVisible();
		await expect.element(screen.getByText('No batch operations found')).toBeVisible();
	});

	it('should use the validated sort and offset from a bookmarked URL', async ({worker}) => {
		worker.use(
			mockQueryBatchOperationsEndpoint({
				schema: z.object({
					sort: z.array(z.object({field: z.literal('startDate'), order: z.literal('asc')})),
					page: z.object({from: z.literal(100), limit: z.literal(50)}),
				}),
				successResponse: RESPONSE_EXCEEDING_PAGE_SIZE,
				failureResponse: HttpResponse.json({}, {status: 500}),
			}),
		);
		const screen = await renderRoute('/operate/batch-operations?page=3&pageSize=50&sort=startDate%2Basc');
		await expect.element(screen.getByRole('link', {name: 'Resolve Incident'})).toBeVisible();
	});

	it('should use the legacy sort for a malformed bookmark without sending an invalid field', async ({worker}) => {
		worker.use(
			mockQueryBatchOperationsEndpoint({
				schema: z.object({
					sort: z.array(z.object({field: z.literal('endDate'), order: z.literal('desc')})),
				}),
				successResponse: RESPONSE_WITH_OPERATIONS,
				failureResponse: HttpResponse.json({}, {status: 500}),
			}),
		);

		const screen = await renderRoute('/operate/batch-operations?sort=unknown%2Basc');
		await expect.element(screen.getByRole('link', {name: 'Cancel Process Instance'})).toBeVisible();
	});

	it('should request the selected page size and return to the first page', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: RESPONSE_EXCEEDING_PAGE_SIZE}));
		const screen = await renderRoute('/operate/batch-operations?page=2');
		await expect.element(screen.getByText('21–25 of 25 items')).toBeVisible();

		worker.use(
			mockQueryBatchOperationsEndpoint({
				schema: z.object({page: z.object({from: z.literal(0), limit: z.literal(50)})}),
				successResponse: RESPONSE_EXCEEDING_PAGE_SIZE,
				failureResponse: HttpResponse.json({}, {status: 500}),
			}),
		);
		await userEvent.selectOptions(screen.getByRole('combobox', {name: 'Items per page:'}), '50');
		await expect.element(screen.getByRole('link', {name: 'Resolve Incident'})).toBeVisible();
		expect(batchOperationsSearchSchema.parse(screen.router.state.location.search)).toMatchObject({
			page: 1,
			pageSize: 50,
		});
	});
});

describe('batch operations URL search', () => {
	it.for(['unknown+asc', 'actorId+sideways', 'state', 'state+asc+extra', ''] as const)(
		'should replace malformed sort %s with the legacy default',
		(value) => {
			expect(batchOperationsSearchSchema.parse({sort: value}).sort).toBe('endDate+desc');
		},
	);

	it('should accept every API sort field and valid order', () => {
		for (const field of ['batchOperationKey', 'operationType', 'state', 'startDate', 'endDate', 'actorId']) {
			for (const order of ['asc', 'desc']) {
				expect(batchOperationsSearchSchema.parse({sort: `${field}+${order}`}).sort).toBe(`${field}+${order}`);
			}
		}
	});
});
