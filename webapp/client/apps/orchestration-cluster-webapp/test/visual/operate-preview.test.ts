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
	mockGetIncidentProcessInstanceStatisticsByErrorEndpoint,
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockLicenseEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {createProcessDefinitionInstanceStatistics} from '#/shared-test-modules/api-mocks/process-definition-statistics';

test.beforeEach(({network}) => {
	network.use(
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['operate']}})),
		}),
		mockLicenseEndpoint({
			successResponse: HttpResponse.json(createLicense()),
		}),
		mockGetProcessDefinitionInstanceStatisticsEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse()),
		}),
		mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse()),
		}),
	);
});

test('should match the no-instances empty state snapshot', async ({network, operatePreviewPage, page}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(createCurrentUser({authorizedComponents: ['operate']})),
		}),
	);

	await operatePreviewPage.goto();
	await expect(operatePreviewPage.noInstancesEmptyState).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the no-instances empty state snapshot with a modeler link', async ({
	network,
	operatePreviewPage,
	page,
}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(
				createCurrentUser({authorizedComponents: ['operate'], c8Links: {modeler: 'https://modeler.example.com'}}),
			),
		}),
	);

	await operatePreviewPage.goto();
	await expect(operatePreviewPage.noInstancesModelerButton).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the list tiles snapshot with sample rows', async ({network, operatePreviewPage, page}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(createCurrentUser({authorizedComponents: ['operate']})),
		}),
		mockGetProcessDefinitionInstanceStatisticsEndpoint({
			successResponse: HttpResponse.json(
				createPaginatedResponse({
					items: [
						createProcessDefinitionInstanceStatistics({
							processDefinitionId: 'process-1',
							activeInstancesWithoutIncidentCount: 10,
							activeInstancesWithIncidentCount: 3,
						}),
					],
					page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
				}),
			),
		}),
	);

	await operatePreviewPage.goto();
	await expect(operatePreviewPage.processesByNameSampleRow).toBeVisible();

	await expect(page).toHaveScreenshot();
});
