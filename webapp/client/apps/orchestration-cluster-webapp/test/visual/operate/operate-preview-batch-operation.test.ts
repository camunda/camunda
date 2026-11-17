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
	mockGetBatchOperationEndpoint,
	mockLicenseEndpoint,
	mockQueryBatchOperationItemsEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {
	createBatchOperation,
	createBatchOperationItem,
	createQueryBatchOperationItemsResponse,
} from '#/shared-test-modules/api-mocks/batch-operations';
import {movePointerAwayFromNavigation} from './movePointerAwayFromNavigation';

const BATCH_OPERATION_KEY = 'batch-operation-visual';

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
		mockGetBatchOperationEndpoint({
			successResponse: HttpResponse.json(
				createBatchOperation({
					batchOperationKey: BATCH_OPERATION_KEY,
					batchOperationType: 'CANCEL_PROCESS_INSTANCE',
					state: 'ACTIVE',
				}),
			),
		}),
		mockQueryBatchOperationItemsEndpoint({
			successResponse: HttpResponse.json(
				createQueryBatchOperationItemsResponse({
					items: [
						createBatchOperationItem({itemKey: 'item-1', state: 'COMPLETED'}),
						createBatchOperationItem({itemKey: 'item-2', state: 'FAILED', errorMessage: 'Something went wrong'}),
					],
					page: {totalItems: 2, startCursor: null, endCursor: null, hasMoreTotalItems: false},
				}),
			),
		}),
	);
});

test('should match the DS batch operation page snapshot', async ({page}) => {
	await page.goto(`/operate-preview/batch-operations/${BATCH_OPERATION_KEY}`);
	await expect(page.getByRole('heading', {name: 'Cancel Process Instance'})).toBeVisible();
	await expect(page.getByRole('table')).toBeVisible();

	await movePointerAwayFromNavigation(page);
	await expect(page).toHaveScreenshot();
});

test('should match the DS batch operation page snapshot with a failure row expanded', async ({page}) => {
	await page.goto(`/operate-preview/batch-operations/${BATCH_OPERATION_KEY}`);
	await page.getByRole('button', {name: /Show failure details/}).click();
	await expect(page.getByText('Failure reason: Something went wrong')).toBeVisible();

	await movePointerAwayFromNavigation(page);
	await expect(page).toHaveScreenshot();
});
