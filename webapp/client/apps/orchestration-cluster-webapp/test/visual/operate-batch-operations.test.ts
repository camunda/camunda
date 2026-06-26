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
			successResponse: HttpResponse.json(createCurrentUser()),
		}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(
				createSystemConfiguration({components: {active: ['operate']}}),
			),
		}),
		mockLicenseEndpoint({
			successResponse: HttpResponse.json(createLicense()),
		}),
	);
});

test('should match the batch operations page snapshot with items', async ({
	network,
	operateBatchOperationsPage,
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
					],
					page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
				}),
			),
		}),
	);

	await operateBatchOperationsPage.goto();
	await expect(operateBatchOperationsPage.table).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the batch operations page snapshot with empty state', async ({
	network,
	operateBatchOperationsPage,
	page,
}) => {
	network.use(
		mockQueryBatchOperationsEndpoint({
			successResponse: HttpResponse.json(createQueryBatchOperationsResponse()),
		}),
	);

	await operateBatchOperationsPage.goto();
	await expect(operateBatchOperationsPage.emptyState).toBeVisible();

	await expect(page).toHaveScreenshot();
});
