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
	mockSystemConfigurationEndpoint,
	mockLicenseEndpoint,
	mockQueryDecisionDefinitionsEndpoint,
	mockQueryDecisionInstancesEndpoint,
	mockGetDecisionDefinitionXmlEndpoint,
	mockDeleteResourceEndpoint,
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockGetIncidentProcessInstanceStatisticsByErrorEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {
	createDecisionDefinition,
	createQueryDecisionDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/decision-definitions';
import {createQueryDecisionInstancesResponse} from '#/shared-test-modules/api-mocks/decision-instances';
import {DMN_XML} from '#/shared-test-modules/api-mocks/decision-definition-xmls';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';

const DEFINITION = createDecisionDefinition({
	decisionDefinitionId: 'invoiceClassification',
	decisionRequirementsKey: '2251799813686001',
	tenantId: '<default>',
});
const SEARCH =
	'?decisionDefinitionId=invoiceClassification&decisionDefinitionVersion=1&tenantId=%3Cdefault%3E&businessId=order-1';

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['operate']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockGetProcessDefinitionInstanceStatisticsEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse()),
		}),
		mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse()),
		}),
		mockQueryDecisionDefinitionsEndpoint({
			successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse({items: [DEFINITION]})),
		}),
		mockQueryDecisionInstancesEndpoint({successResponse: HttpResponse.json(createQueryDecisionInstancesResponse())}),
		mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(DMN_XML)}),
	);
});

test('should delete the selected DRD, refresh decisions and preserve tenant and other filters', async ({
	network,
	page,
	operateDecisionsPage,
}) => {
	network.use(
		mockDeleteResourceEndpoint({
			successResponse: HttpResponse.json({resourceKey: DEFINITION.decisionRequirementsKey, batchOperation: null}),
		}),
	);
	await operateDecisionsPage.goto('?businessId=previous');
	await operateDecisionsPage.goto(SEARCH);
	await expect(operateDecisionsPage.deleteDefinitionButton).toBeVisible();
	await operateDecisionsPage.deleteDefinitionButton.click();
	await expect(page.getByText('My DRD', {exact: true})).toBeVisible();

	network.use(
		mockQueryDecisionDefinitionsEndpoint({
			successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse()),
		}),
	);
	const deletion = page.waitForRequest((request) => request.url().endsWith('/deletion'));
	const refresh = page.waitForRequest((request) => request.url().endsWith('/v2/decision-definitions/search'));
	await operateDecisionsPage.confirmation.click();
	await operateDecisionsPage.confirmDeleteButton.click();

	const request = await deletion;
	expect(new URL(request.url()).pathname).toBe(`/v2/resources/${DEFINITION.decisionRequirementsKey}/deletion`);
	expect(request.method()).toBe('POST');
	expect(request.postDataJSON()).toEqual({deleteHistory: true});
	await refresh;
	await expect(page.getByText('Operation created', {exact: true})).toBeVisible();
	await expect(operateDecisionsPage.deleteDefinitionButton).not.toBeVisible();
	await expect(page.getByText('There is no Decision selected')).toBeVisible();
	await expect(page).toHaveURL(
		(url) =>
			!url.searchParams.has('decisionDefinitionId') &&
			!url.searchParams.has('decisionDefinitionVersion') &&
			url.searchParams.get('tenantId') === '<default>' &&
			url.searchParams.get('businessId') === 'order-1',
	);
	await page.reload();
	await expect(page.getByRole('combobox', {name: 'Name', exact: true})).toHaveValue('');
	await expect(operateDecisionsPage.deleteDefinitionButton).not.toBeVisible();
	await page.goBack();
	await expect(page).toHaveURL(
		(url) => url.searchParams.get('businessId') === 'previous' && !url.searchParams.has('decisionDefinitionId'),
	);
	await expect(page.getByText('Decision could not be found', {exact: true})).not.toBeVisible();
});

test('should recover from forbidden deletion without losing the selected version', async ({
	network,
	page,
	operateDecisionsPage,
}) => {
	network.use(mockDeleteResourceEndpoint({successResponse: new HttpResponse(null, {status: 403})}));
	await operateDecisionsPage.goto(SEARCH);
	const dashboardPreload = page.waitForRequest((request) =>
		request.url().endsWith('/v2/process-definitions/statistics/process-instances'),
	);
	await operateDecisionsPage.preloadDashboard();
	await dashboardPreload;
	await operateDecisionsPage.deleteDefinition();

	await expect(page.getByText("You don't have permission to perform this operation")).toBeVisible();
	await expect(operateDecisionsPage.deleteDefinitionButton).toBeEnabled();
	await expect(page).toHaveURL((url) => url.searchParams.get('decisionDefinitionVersion') === '1');
	network.use(
		mockDeleteResourceEndpoint({
			successResponse: HttpResponse.json({resourceKey: DEFINITION.decisionRequirementsKey, batchOperation: null}),
		}),
	);
	await operateDecisionsPage.deleteDefinition();
	await expect(page.getByText('Operation created', {exact: true})).toBeVisible();
});
