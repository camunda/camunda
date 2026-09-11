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
const SEARCH = '?decisionDefinitionId=invoiceClassification&decisionDefinitionVersion=1&tenantId=%3Cdefault%3E';

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

test('should have no accessibility violations in the delete DRD confirmation modal', async ({
	page,
	operateDecisionsPage,
	makeAxeBuilder,
}) => {
	await operateDecisionsPage.goto(SEARCH);
	await expect(operateDecisionsPage.deleteDefinitionButton).toBeVisible();
	await operateDecisionsPage.deleteDefinitionButton.click();
	await expect(page.getByText('My DRD', {exact: true})).toBeVisible();
	await page.getByRole('dialog', {name: 'Delete DRD', exact: true}).evaluate(async (dialog) => {
		const animations: {finished: Promise<unknown>}[] = dialog.parentElement!.getAnimations({subtree: true});
		await Promise.all(animations.map((animation) => animation.finished));
	});

	const accessibilityScanResults = await makeAxeBuilder().include('[role="dialog"][aria-label="Delete DRD"]').analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});
