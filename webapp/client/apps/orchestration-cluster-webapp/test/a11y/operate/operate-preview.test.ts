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
	mockGetIncidentProcessInstanceStatisticsByDefinitionEndpoint,
	mockGetIncidentProcessInstanceStatisticsByErrorEndpoint,
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockLicenseEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {createProcessDefinitionInstanceStatistics} from '#/shared-test-modules/api-mocks/process-definition-statistics';
import {
	createIncidentProcessInstanceStatisticsByDefinition,
	createIncidentProcessInstanceStatisticsByError,
} from '#/shared-test-modules/api-mocks/incident-statistics';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';

test('should have no accessibility violations in the no-instances empty state', async ({
	network,
	operatePreviewPage,
	makeAxeBuilder,
}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(
				createCurrentUser({authorizedComponents: ['operate'], c8Links: {modeler: 'https://modeler.example.com'}}),
			),
		}),
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

	await operatePreviewPage.goto();
	await expect(operatePreviewPage.noInstancesModelerButton).toBeVisible();

	// Scoped to the empty state itself: the rest of the preview shell still has
	// unbuilt placeholder tiles (InstancesByProcess/IncidentsByError land in later PRs)
	// that a full-page scan would flag for content this PR doesn't touch.
	const results = await makeAxeBuilder().include('[data-slot="empty-state"]').analyze();
	expect(results.violations).toEqual([]);
});

test('should have no accessibility violations in the list tiles with real process rows and real incident rows', async ({
	network,
	operatePreviewPage,
	makeAxeBuilder,
}) => {
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
		mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
			successResponse: HttpResponse.json(
				createPaginatedResponse({
					items: [
						createIncidentProcessInstanceStatisticsByError({
							errorHashCode: 1,
							errorMessage: 'Payment gateway request timed out',
							activeInstancesWithErrorCount: 5,
						}),
					],
					page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
				}),
			),
		}),
		mockQueryProcessDefinitionsEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessDefinitionsResponse({
					items: [createProcessDefinition({processDefinitionId: 'process-1', state: 'DRAINING'})],
				}),
			),
		}),
		mockGetIncidentProcessInstanceStatisticsByDefinitionEndpoint({
			successResponse: HttpResponse.json(
				createPaginatedResponse({
					items: [
						createIncidentProcessInstanceStatisticsByDefinition({
							processDefinitionId: 'process-1',
							processDefinitionName: 'My Process',
						}),
					],
					page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
				}),
			),
		}),
	);

	await operatePreviewPage.goto();
	await expect(operatePreviewPage.processesByNameRow('My Process')).toBeVisible();
	await expect(operatePreviewPage.incidentsByErrorRow('Payment gateway request timed out')).toBeVisible();

	// Expand the incident row so the definitions drill-down it reveals is part of
	// the scanned DOM too, not just the collapsed error-message row.
	await operatePreviewPage.expandIncidentRowButton.click();

	const results = await makeAxeBuilder().include('[data-slot="data-table"]').analyze();
	expect(results.violations).toEqual([]);
});

test('should have no accessibility violations in the metric panel with running instances', async ({
	network,
	operatePreviewPage,
	makeAxeBuilder,
}) => {
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
		mockGetProcessDefinitionInstanceStatisticsEndpoint({
			successResponse: HttpResponse.json(
				createPaginatedResponse({
					items: [
						createProcessDefinitionInstanceStatistics({
							processDefinitionId: 'process-1',
							latestProcessDefinitionName: 'Process One',
							activeInstancesWithoutIncidentCount: 10,
							activeInstancesWithIncidentCount: 3,
						}),
					],
					page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
				}),
			),
		}),
		mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse()),
		}),
		mockQueryProcessDefinitionsEndpoint({successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse())}),
	);

	await operatePreviewPage.goto();
	await expect(operatePreviewPage.metricPanel).toBeVisible();

	// Scoped to the metric panel itself: IncidentsByError is still an unbuilt
	// placeholder tile that a full-page scan would flag for content this PR doesn't touch.
	const results = await makeAxeBuilder().include('[data-testid="metric-panel"]').analyze();
	expect(results.violations).toEqual([]);
});
