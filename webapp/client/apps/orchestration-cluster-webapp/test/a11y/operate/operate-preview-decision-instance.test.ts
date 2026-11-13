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
	mockGetDecisionDefinitionXmlEndpoint,
	mockGetDecisionInstanceEndpoint,
	mockLicenseEndpoint,
	mockQueryDecisionInstancesEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {
	createDecisionInstance,
	createQueryDecisionInstancesResponse,
} from '#/shared-test-modules/api-mocks/decision-instances';
import {DMN_XML} from '#/shared-test-modules/api-mocks/decision-definition-xmls';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';

const DECISION_INSTANCE_KEY = '4294980768';
const decisionInstance = createDecisionInstance({
	decisionEvaluationInstanceKey: DECISION_INSTANCE_KEY,
	decisionDefinitionId: 'invoiceClassification',
	decisionDefinitionName: 'Invoice Classification',
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
		mockGetDecisionInstanceEndpoint({
			successResponse: HttpResponse.json(decisionInstance),
		}),
		mockQueryDecisionInstancesEndpoint({
			successResponse: HttpResponse.json(createQueryDecisionInstancesResponse({items: [decisionInstance]})),
		}),
		mockGetDecisionDefinitionXmlEndpoint({
			successResponse: HttpResponse.text(DMN_XML),
		}),
	);
});

test('should have no accessibility violations on the decision instance page', async ({page, makeAxeBuilder}) => {
	await page.goto(`/operate-preview/decisions/${DECISION_INSTANCE_KEY}`);
	await expect(page.getByTestId('instance-header')).toBeVisible();
	await expect(page.getByRole('region', {name: 'decision panel'})).toBeVisible();
	await expect(page.getByRole('table').first()).toBeVisible();
	await expect(page.getByRole('region', {name: 'DRD panel'})).toBeVisible();

	const results = await makeAxeBuilder().analyze();
	expect(results.violations).toEqual([]);
});
