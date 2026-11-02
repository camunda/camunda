/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import {HttpResponse} from 'msw';
import {
	mockCurrentUserEndpoint,
	mockLicenseEndpoint,
	mockQueryBatchOperationsEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {
	createBatchOperation,
	createQueryBatchOperationsResponse,
} from '#/shared-test-modules/api-mocks/batch-operations';

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(createCurrentUser({authorizedComponents: ['operate']})),
		}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['operate']}})),
		}),
		mockLicenseEndpoint({
			successResponse: HttpResponse.json(createLicense()),
		}),
	);
});

test.describe('Operate Batch Operations DS preview (/operate-preview/batch-operations)', () => {
	test('should render batch operations with state and item counts', async ({
		network,
		operatePreviewBatchOperationsPage,
	}) => {
		network.use(
			mockQueryBatchOperationsEndpoint({
				successResponse: HttpResponse.json(
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
						],
						page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				),
			}),
		);

		await operatePreviewBatchOperationsPage.goto();

		await expect(operatePreviewBatchOperationsPage.heading).toBeVisible();
		await expect(operatePreviewBatchOperationsPage.cellByText('Cancel process instance')).toBeVisible();
		await expect(operatePreviewBatchOperationsPage.cellByText('demo')).toBeVisible();
		await expect(operatePreviewBatchOperationsPage.cellByText('Batch operation status: Completed')).toBeVisible();
	});

	test('should render the empty state when no batch operations exist', async ({
		network,
		operatePreviewBatchOperationsPage,
	}) => {
		network.use(
			mockQueryBatchOperationsEndpoint({
				successResponse: HttpResponse.json(createQueryBatchOperationsResponse()),
			}),
		);

		await operatePreviewBatchOperationsPage.goto();

		await expect(operatePreviewBatchOperationsPage.emptyState).toBeVisible();
	});

	test('should render pagination when totalItems exceeds the page size', async ({
		network,
		operatePreviewBatchOperationsPage,
	}) => {
		network.use(
			mockQueryBatchOperationsEndpoint({
				successResponse: HttpResponse.json(
					createQueryBatchOperationsResponse({
						items: Array.from({length: 20}, (_, i) => createBatchOperation({batchOperationKey: `op-${i + 1}`})),
						page: {totalItems: 50, startCursor: null, endCursor: null, hasMoreTotalItems: true},
					}),
				),
			}),
		);

		await operatePreviewBatchOperationsPage.goto();

		await expect(operatePreviewBatchOperationsPage.pageIndicator).toBeVisible();
	});
});
