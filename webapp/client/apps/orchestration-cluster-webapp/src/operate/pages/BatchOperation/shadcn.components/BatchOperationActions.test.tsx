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
import {
	mockGetBatchOperationEndpoint,
	mockQueryBatchOperationItemsEndpoint,
	mockSuspendBatchOperationEndpoint,
	mockResumeBatchOperationEndpoint,
	mockCancelBatchOperationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createBatchOperation,
	createQueryBatchOperationItemsResponse,
} from '#/shared-test-modules/api-mocks/batch-operations';
import {BatchOperation} from './BatchOperation';

const BATCH_OPERATION_KEY = 'batch-op-1';

type State = NonNullable<Parameters<typeof createBatchOperation>[0]>['state'];

function mockBatchOperation(state: State, options?: {once?: boolean; delay?: number}) {
	return mockGetBatchOperationEndpoint({
		successResponse: HttpResponse.json(createBatchOperation({batchOperationKey: BATCH_OPERATION_KEY, state})),
		...options,
	});
}

function renderPage() {
	return renderWithRouter(() => <BatchOperation batchOperationKey={BATCH_OPERATION_KEY} />, {
		path: '/operate-preview/batch-operations/$batchOperationKey',
		initialEntry: `/operate-preview/batch-operations/${BATCH_OPERATION_KEY}`,
	});
}

describe('<BatchOperationActions />', () => {
	it.for(['CREATED', 'ACTIVE'] as const)(
		'should show suspend and cancel for a %s batch operation',
		async (state, {worker}) => {
			worker.use(
				mockBatchOperation(state),
				mockQueryBatchOperationItemsEndpoint({
					successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse()),
				}),
			);

			const screen = await renderPage();

			await expect.element(screen.getByRole('button', {name: 'Suspend'})).toBeVisible();
			await expect.element(screen.getByRole('button', {name: 'More actions'})).toBeVisible();
			await expect.element(screen.getByRole('button', {name: 'Resume'})).not.toBeInTheDocument();
		},
	);

	it('should show resume and cancel for a suspended batch operation', async ({worker}) => {
		worker.use(
			mockBatchOperation('SUSPENDED'),
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse()),
			}),
		);

		const screen = await renderPage();

		await expect.element(screen.getByRole('button', {name: 'Resume'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'More actions'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Suspend'})).not.toBeInTheDocument();
	});

	it.for(['COMPLETED', 'PARTIALLY_COMPLETED', 'FAILED', 'CANCELED'] as const)(
		'should show no actions for a %s batch operation',
		async (state, {worker}) => {
			worker.use(
				mockBatchOperation(state),
				mockQueryBatchOperationItemsEndpoint({
					successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse()),
				}),
			);

			const screen = await renderPage();

			await expect.element(screen.getByRole('status', {name: /Batch operation status/})).toBeVisible();
			await expect.element(screen.getByRole('button', {name: 'Suspend'})).not.toBeInTheDocument();
			await expect.element(screen.getByRole('button', {name: 'Resume'})).not.toBeInTheDocument();
			await expect.element(screen.getByRole('button', {name: 'More actions'})).not.toBeInTheDocument();
		},
	);

	it('should suspend an active batch operation and disable the button until it is suspended', async ({worker}) => {
		worker.use(
			mockSuspendBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockBatchOperation('ACTIVE', {once: true}),
			mockBatchOperation('SUSPENDED', {delay: 500}),
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse()),
			}),
		);

		const screen = await renderPage();
		await userEvent.click(screen.getByRole('button', {name: 'Suspend'}));

		await expect.element(screen.getByRole('button', {name: 'Suspend'})).toBeDisabled();
		await expect.element(screen.getByRole('button', {name: 'Resume'})).toBeVisible();
		await expect.element(screen.getByRole('status', {name: 'Batch operation status: Suspended'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Suspend'})).not.toBeInTheDocument();
	});

	it('should resume a suspended batch operation', async ({worker}) => {
		worker.use(
			mockResumeBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockBatchOperation('SUSPENDED', {once: true}),
			mockBatchOperation('ACTIVE'),
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse()),
			}),
		);

		const screen = await renderPage();
		await userEvent.click(screen.getByRole('button', {name: 'Resume'}));

		await expect.element(screen.getByRole('status', {name: 'Batch operation status: Active'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Suspend'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Resume'})).not.toBeInTheDocument();
	});

	it('should cancel a batch operation from the overflow menu', async ({worker}) => {
		worker.use(
			mockCancelBatchOperationEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockBatchOperation('ACTIVE', {once: true}),
			mockBatchOperation('CANCELED'),
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse()),
			}),
		);

		const screen = await renderPage();
		await userEvent.click(screen.getByRole('button', {name: 'More actions'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Cancel'}));

		await expect.element(screen.getByRole('status', {name: 'Batch operation status: Canceled'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'More actions'})).not.toBeInTheDocument();
	});
});
