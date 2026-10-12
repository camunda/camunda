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
	mockQueryDecisionDefinitionsEndpoint,
	mockQueryDecisionInstancesEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createQueryDecisionDefinitionsResponse} from '#/shared-test-modules/api-mocks/decision-definitions';
import {createQueryDecisionInstancesResponse} from '#/shared-test-modules/api-mocks/decision-instances';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';

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
		mockGetProcessDefinitionInstanceStatisticsEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse()),
		}),
		mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse()),
		}),
	);
});

test.describe('Operate Decisions DS preview (/operate-preview/decisions)', () => {
	test('should render the placeholder shell', async ({operatePreviewDecisionsPage}) => {
		await operatePreviewDecisionsPage.goto();

		await expect(operatePreviewDecisionsPage.heading).toBeAttached();
		await expect(operatePreviewDecisionsPage.filtersPanelPlaceholder).toBeVisible();
		await expect(operatePreviewDecisionsPage.decisionPanelPlaceholder).toBeVisible();
		await expect(operatePreviewDecisionsPage.instancesTablePlaceholder).toBeVisible();
	});

	test('should keep the Carbon Decisions route working', async ({network, operateDecisionsPage, page}) => {
		network.use(
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse()),
			}),
			mockQueryDecisionInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionInstancesResponse()),
			}),
		);
		await operateDecisionsPage.goto('?evaluated=true&failed=true');

		await expect(page.getByRole('combobox', {name: 'Name', exact: true})).toBeVisible();
	});
});
