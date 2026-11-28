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
	mockCreateDecisionInstancesDeletionBatchOperationEndpoint,
	mockGetBatchOperationEndpoint,
	mockQueryBatchOperationItemsEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createBatchOperation,
	createQueryBatchOperationItemsResponse,
} from '#/shared-test-modules/api-mocks/batch-operations';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {
	createDecisionDefinition,
	createQueryDecisionDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/decision-definitions';
import {
	createDecisionInstance,
	createQueryDecisionInstancesResponse,
} from '#/shared-test-modules/api-mocks/decision-instances';
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
	await expect(operateDecisionsPage.decisionPanel.getByRole('heading', {name: 'Select a decision'})).toBeVisible();
	await expect(page).toHaveURL(
		(url) =>
			!url.searchParams.has('decisionDefinitionId') &&
			!url.searchParams.has('decisionDefinitionVersion') &&
			url.searchParams.get('tenantId') === '<default>' &&
			url.searchParams.get('businessId') === 'order-1',
	);
	await page.reload();
	await expect(page.getByRole('combobox', {name: 'Name', exact: true})).toHaveText('Search by decision name');
	await expect(operateDecisionsPage.deleteDefinitionButton).not.toBeVisible();
	await page.goBack();
	await expect(page).toHaveURL(
		(url) => url.searchParams.get('businessId') === 'previous' && !url.searchParams.has('decisionDefinitionId'),
	);
	await expect(page.getByText("Couldn't find the decision", {exact: true})).not.toBeVisible();
});

test('should link to the created batch operation after deleting a DRD', async ({
	network,
	page,
	operateDecisionsPage,
}) => {
	network.use(
		mockDeleteResourceEndpoint({
			successResponse: HttpResponse.json({
				resourceKey: DEFINITION.decisionRequirementsKey,
				batchOperation: {
					batchOperationKey: 'decision-delete-2',
					batchOperationType: 'DELETE_DECISION_INSTANCE',
				},
			}),
		}),
		mockGetBatchOperationEndpoint({
			successResponse: HttpResponse.json(
				createBatchOperation({
					batchOperationKey: 'decision-delete-2',
					batchOperationType: 'DELETE_DECISION_INSTANCE',
				}),
			),
		}),
		mockQueryBatchOperationItemsEndpoint({
			successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse()),
		}),
	);
	await operateDecisionsPage.goto(SEARCH);
	await operateDecisionsPage.deleteDefinition();
	await page.getByRole('button', {name: 'Go to operation details'}).click();

	await expect(page).toHaveURL('/operate-preview/batch-operations/decision-delete-2');
});

test('should open the started batch operation from the decision instance deletion notification within a deployment prefix', async ({
	network,
	page,
}) => {
	network.use(
		mockQueryDecisionInstancesEndpoint({
			successResponse: HttpResponse.json(
				createQueryDecisionInstancesResponse({
					items: [createDecisionInstance({decisionEvaluationInstanceKey: '2251799813702856-1'})],
				}),
			),
		}),
		mockCreateDecisionInstancesDeletionBatchOperationEndpoint({
			successResponse: HttpResponse.json({
				batchOperationKey: 'decision-deletion-1',
				batchOperationType: 'DELETE_DECISION_INSTANCE',
			}),
		}),
		mockGetBatchOperationEndpoint({
			successResponse: HttpResponse.json(
				createBatchOperation({
					batchOperationKey: 'decision-deletion-1',
					batchOperationType: 'DELETE_DECISION_INSTANCE',
					actorId: 'deletion-actor',
				}),
			),
		}),
		mockQueryBatchOperationItemsEndpoint({
			successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse()),
		}),
	);
	await page.route('**/deployment/operate/decisions**', async (route) => {
		const response = await route.fetch();
		await route.fulfill({
			response,
			body: (await response.text()).replace('<html', '<html data-base-name="/deployment"'),
		});
	});
	await page.goto('/deployment/operate/decisions?evaluated=true&failed=true');

	await page.getByRole('checkbox', {name: 'Select row 2251799813702856-1'}).check({force: true});
	await page.getByRole('button', {name: 'Delete', exact: true}).click();
	await page.getByRole('alertdialog').getByRole('button', {name: 'Delete', exact: true}).click();
	await page.getByRole('button', {name: 'Go to operation details'}).click();

	await expect(page).toHaveURL('/deployment/operate-preview/batch-operations/decision-deletion-1');
	await expect(page.getByRole('heading', {name: 'Delete Decision Instance', exact: true})).toBeVisible();
	await expect(page.getByText('deletion-actor', {exact: true})).toBeVisible();
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
