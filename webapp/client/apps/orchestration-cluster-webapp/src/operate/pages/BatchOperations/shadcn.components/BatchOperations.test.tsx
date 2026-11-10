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
import {batchOperationsSearchSchema} from '../batchOperations.queries';
import {mockQueryBatchOperationsEndpoint} from '#/shared-test-modules/mock-handlers';
import {
	createBatchOperation,
	createQueryBatchOperationsResponse,
} from '#/shared-test-modules/api-mocks/batch-operations';
import {BatchOperations} from './BatchOperations';

const PATH = '/operate-preview/batch-operations';

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

const RESPONSE_WITH_UNTYPED_OPERATION = HttpResponse.json(
	createQueryBatchOperationsResponse({
		items: [
			createBatchOperation({batchOperationKey: 'op-1', batchOperationType: null}),
			createBatchOperation({batchOperationKey: 'op-2', batchOperationType: 'RESOLVE_INCIDENT'}),
		],
		page: {totalItems: 2, startCursor: null, endCursor: null, hasMoreTotalItems: false},
	}),
);

function responseExceedingPageSize(batchOperationType: 'RESOLVE_INCIDENT' | 'CANCEL_PROCESS_INSTANCE') {
	return HttpResponse.json(
		createQueryBatchOperationsResponse({
			items: [createBatchOperation({batchOperationKey: 'op-1', batchOperationType})],
			page: {totalItems: 25, startCursor: null, endCursor: null, hasMoreTotalItems: false},
		}),
	);
}

const RESPONSE_EXCEEDING_PAGE_SIZE = responseExceedingPageSize('RESOLVE_INCIDENT');

function renderPage() {
	return renderWithRouter(() => <BatchOperations page={1} pageSize={20} sort="endDate+DESC" />, {path: PATH});
}

function renderRoute(initialEntry = PATH) {
	return renderWithRouter(
		() => {
			const {page, pageSize, sort} = batchOperationsSearchSchema.parse(useSearch({strict: false}));
			return <BatchOperations page={page} pageSize={pageSize} sort={sort} />;
		},
		{path: PATH, initialEntry},
	);
}

describe('<BatchOperations /> (DS)', () => {
	afterEach(async () => {
		await i18n.changeLanguage('en');
	});

	it('should render the empty message without table headers when there are no batch operations', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: EMPTY_RESPONSE}));

		const screen = await renderPage();

		await expect.element(screen.getByText('No batch operations found')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Actor'})).not.toBeInTheDocument();
	});

	it('should render each batch operation with its link, state, actor and item count', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: RESPONSE_WITH_OPERATIONS}));

		const screen = await renderPage();

		await expect.element(screen.getByRole('link', {name: 'Cancel Process Instance'})).toBeVisible();
		await expect.element(screen.getByRole('link', {name: 'Resolve Incident'})).toBeVisible();
		await expect.element(screen.getByText(/^Completed$/)).toBeVisible();
		await expect.element(screen.getByText(/^Active$/)).toBeVisible();
		await expect.element(screen.getByText('demo')).toBeVisible();
		await expect.element(screen.getByText('admin')).toBeVisible();
		await expect.element(screen.getByText('5', {exact: true})).toBeVisible();
	});

	it('should render a placeholder for a batch operation without a type', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: RESPONSE_WITH_UNTYPED_OPERATION}));

		const screen = await renderPage();

		await expect.element(screen.getByText('--')).toBeVisible();
		await expect.element(screen.getByText('Resolve Incident')).toBeVisible();
	});

	it('should not render pagination when all items fit on one page', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: RESPONSE_WITH_OPERATIONS}));

		const screen = await renderPage();

		await expect.element(screen.getByText('Resolve Incident')).toBeVisible();
		await expect.element(screen.getByText('Items per page:')).not.toBeInTheDocument();
	});

	it('should render the item range and page count when items exceed the page size', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: RESPONSE_EXCEEDING_PAGE_SIZE}));

		const screen = await renderPage();

		await expect.element(screen.getByText('1–20 of 25 items · Page 1 of 2')).toBeVisible();
	});

	it('should translate all visible pagination controls when the language changes', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: RESPONSE_EXCEEDING_PAGE_SIZE}));
		const screen = await renderRoute();
		await expect.element(screen.getByText('1–20 of 25 items · Page 1 of 2')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Batch state'})).toBeVisible();

		await i18n.changeLanguage('de');

		await expect.element(screen.getByText('1–20 von 25 Elementen · Seite 1 von 2')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Stapelstatus'})).toBeVisible();
		await expect.element(screen.getByRole('combobox', {name: 'Elemente pro Seite:'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Nächste Seite'})).toBeVisible();
	});

	it('should show loading rather than the empty message while the first response is pending', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: EMPTY_RESPONSE, delay: 'infinite'}));

		const screen = await renderRoute();

		await expect.element(screen.getByRole('table')).toBeVisible();
		await expect.element(screen.getByText('No batch operations found')).not.toBeInTheDocument();
	});

	it('should update the document title when the language changes', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: EMPTY_RESPONSE}));
		document.title = 'Stale title';
		const screen = await renderRoute();
		await expect.element(screen.getByText('No batch operations found')).toBeVisible();
		expect(document.title).toBe('Operate: Batch Operations');

		await i18n.changeLanguage('de');
		await expect.element(screen.getByText('Keine Stapeloperationen gefunden')).toBeVisible();
		expect(document.title).toBe('Operate: Stapeloperationen');
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

	it('should keep the rows of a failed refresh and recover on retry', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: RESPONSE_EXCEEDING_PAGE_SIZE}));
		const screen = await renderRoute();
		await expect.element(screen.getByRole('link', {name: 'Resolve Incident'})).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: 'Next page'}));
		await expect.element(screen.getByText('21–25 of 25 items · Page 2 of 2')).toBeVisible();

		worker.use(
			mockQueryBatchOperationsEndpoint({
				schema: z.object({page: z.object({from: z.literal(20), limit: z.literal(20)})}),
				successResponse: RESPONSE_EXCEEDING_PAGE_SIZE,
				failureResponse: HttpResponse.json({}, {status: 500}),
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Previous page'}));

		await expect.element(screen.getByRole('alert')).toMatchTextContent('Failed to refresh batch operations');
		await expect.element(screen.getByRole('link', {name: 'Resolve Incident'})).toBeVisible();
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: EMPTY_RESPONSE}));
		await userEvent.click(screen.getByRole('button', {name: 'Try again'}));

		await expect.element(screen.getByText('No batch operations found')).toBeVisible();
		await expect.element(screen.getByRole('alert')).not.toBeInTheDocument();
	});

	it('should request the clicked sort column and the next page of results', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: RESPONSE_EXCEEDING_PAGE_SIZE}));
		const screen = await renderRoute();
		await expect.element(screen.getByRole('link', {name: 'Resolve Incident'})).toBeVisible();

		worker.use(
			mockQueryBatchOperationsEndpoint({
				schema: z.object({
					page: z.object({from: z.literal(0), limit: z.literal(20)}),
					sort: z.array(z.object({field: z.literal('actorId'), order: z.literal('DESC')})),
				}),
				successResponse: responseExceedingPageSize('CANCEL_PROCESS_INSTANCE'),
				failureResponse: HttpResponse.json({}, {status: 500}),
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Actor'}));
		await expect.element(screen.getByRole('link', {name: 'Cancel Process Instance'})).toBeVisible();

		worker.use(
			mockQueryBatchOperationsEndpoint({
				schema: z.object({
					page: z.object({from: z.literal(20), limit: z.literal(20)}),
					sort: z.array(z.object({field: z.literal('actorId'), order: z.literal('DESC')})),
				}),
				successResponse: RESPONSE_EXCEEDING_PAGE_SIZE,
				failureResponse: HttpResponse.json({}, {status: 500}),
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Next page'}));

		await expect.element(screen.getByText('21–25 of 25 items · Page 2 of 2')).toBeVisible();
		await expect.element(screen.getByRole('link', {name: 'Resolve Incident'})).toBeVisible();
	});

	it('should retain usable rows while requesting another page', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: RESPONSE_EXCEEDING_PAGE_SIZE}));
		const screen = await renderRoute();
		await expect.element(screen.getByRole('link', {name: 'Resolve Incident'})).toBeVisible();

		worker.use(mockQueryBatchOperationsEndpoint({successResponse: EMPTY_RESPONSE, delay: 'infinite'}));
		await userEvent.click(screen.getByRole('button', {name: 'Next page'}));

		await expect.element(screen.getByText('21–25 of 25 items · Page 2 of 2')).toBeVisible();
		await expect.element(screen.getByRole('link', {name: 'Resolve Incident'})).toBeVisible();
	});

	it('should use the validated sort and offset from a bookmarked URL', async ({worker}) => {
		worker.use(
			mockQueryBatchOperationsEndpoint({
				schema: z.object({
					sort: z.array(z.object({field: z.literal('startDate'), order: z.literal('ASC')})),
					page: z.object({from: z.literal(100), limit: z.literal(50)}),
				}),
				successResponse: RESPONSE_EXCEEDING_PAGE_SIZE,
				failureResponse: HttpResponse.json({}, {status: 500}),
			}),
		);

		const screen = await renderRoute(`${PATH}?page=3&pageSize=50&sort=startDate%2BASC`);

		await expect.element(screen.getByRole('link', {name: 'Resolve Incident'})).toBeVisible();
	});

	it('should request the selected page size and return to the first page', async ({worker}) => {
		worker.use(mockQueryBatchOperationsEndpoint({successResponse: RESPONSE_EXCEEDING_PAGE_SIZE}));
		const screen = await renderRoute(`${PATH}?page=2`);
		await expect.element(screen.getByText('21–25 of 25 items · Page 2 of 2')).toBeVisible();

		worker.use(
			mockQueryBatchOperationsEndpoint({
				schema: z.object({page: z.object({from: z.literal(0), limit: z.literal(50)})}),
				successResponse: responseExceedingPageSize('CANCEL_PROCESS_INSTANCE'),
				failureResponse: HttpResponse.json({}, {status: 500}),
			}),
		);
		await userEvent.click(screen.getByRole('combobox', {name: 'Items per page:'}));
		await userEvent.click(screen.getByRole('option', {name: '50'}));

		await expect.element(screen.getByRole('link', {name: 'Cancel Process Instance'})).toBeVisible();
	});
});
