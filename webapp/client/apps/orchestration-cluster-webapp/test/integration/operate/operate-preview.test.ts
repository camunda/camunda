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

const STATS_WITH_INSTANCES = createPaginatedResponse({
	items: [
		createProcessDefinitionInstanceStatistics({
			processDefinitionId: 'process-1',
			latestProcessDefinitionName: 'Process One',
			activeInstancesWithoutIncidentCount: 10,
			activeInstancesWithIncidentCount: 3,
		}),
	],
	page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
});

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

test.describe('Operate Dashboard DS preview (/operate-preview)', () => {
	test('should render the shell with both list tiles when there are running instances', async ({
		network,
		operatePreviewPage,
	}) => {
		network.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				successResponse: HttpResponse.json(STATS_WITH_INSTANCES),
			}),
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				successResponse: HttpResponse.json(createPaginatedResponse()),
			}),
		);

		await operatePreviewPage.goto();

		await expect(operatePreviewPage.heading).toBeAttached();
		await expect(operatePreviewPage.metricPanel).toBeAttached();
		await expect(operatePreviewPage.processesByNameTile).toBeVisible();
		await expect(operatePreviewPage.incidentsByErrorTile).toBeVisible();
	});

	test('should render the no-instances empty state when there are no running instances', async ({
		network,
		operatePreviewPage,
	}) => {
		network.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				successResponse: HttpResponse.json(createPaginatedResponse()),
			}),
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				successResponse: HttpResponse.json(createPaginatedResponse()),
			}),
		);

		await operatePreviewPage.goto();

		await expect(operatePreviewPage.incidentsByErrorTile).not.toBeAttached();
		await expect(operatePreviewPage.noInstancesEmptyState).toBeVisible();
		await expect(operatePreviewPage.noInstancesLearnMoreLink).toBeVisible();
		await expect(operatePreviewPage.noInstancesModelerButton).not.toBeAttached();
	});

	test('should render the go-to-modeler button in the empty state when the user has a modeler link', async ({
		network,
		operatePreviewPage,
	}) => {
		network.use(
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json(
					createCurrentUser({authorizedComponents: ['operate'], c8Links: {modeler: 'https://modeler.example.com'}}),
				),
			}),
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				successResponse: HttpResponse.json(createPaginatedResponse()),
			}),
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				successResponse: HttpResponse.json(createPaginatedResponse()),
			}),
		);

		await operatePreviewPage.goto();

		await expect(operatePreviewPage.noInstancesModelerButton).toBeVisible();
		await expect(operatePreviewPage.noInstancesModelerButton).toHaveAttribute('href', 'https://modeler.example.com');
	});

	test('should show sample rows in the list tiles, since real data has not landed yet', async ({
		network,
		page,
		operatePreviewPage,
	}) => {
		network.use(
			mockGetProcessDefinitionInstanceStatisticsEndpoint({
				successResponse: HttpResponse.json(STATS_WITH_INSTANCES),
			}),
			mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
				successResponse: HttpResponse.json(createPaginatedResponse()),
			}),
		);

		await operatePreviewPage.goto();

		await expect(operatePreviewPage.processesByNameSampleRow).toBeVisible();
		await expect(operatePreviewPage.incidentsByErrorSampleRow).toBeVisible();
		await expect(page.getByText('Process One')).not.toBeAttached();
	});
});
