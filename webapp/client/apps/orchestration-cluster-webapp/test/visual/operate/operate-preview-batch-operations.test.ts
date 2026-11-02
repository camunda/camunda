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
import {movePointerAwayFromNavigation} from './movePointerAwayFromNavigation';

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

test('should match the DS batch operations page snapshot with items', async ({
	network,
	operatePreviewBatchOperationsPage,
	page,
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
			),
		}),
	);

	await operatePreviewBatchOperationsPage.goto();
	await expect(operatePreviewBatchOperationsPage.cellByText('Cancel process instance')).toBeVisible();

	await movePointerAwayFromNavigation(page);
	await expect(page).toHaveScreenshot();
});

test('should match the DS batch operations page snapshot with empty state', async ({
	network,
	operatePreviewBatchOperationsPage,
	page,
}) => {
	network.use(
		mockQueryBatchOperationsEndpoint({
			successResponse: HttpResponse.json(createQueryBatchOperationsResponse()),
		}),
	);

	await operatePreviewBatchOperationsPage.goto();
	await expect(operatePreviewBatchOperationsPage.emptyState).toBeVisible();

	await movePointerAwayFromNavigation(page);
	await expect(page).toHaveScreenshot();
});
