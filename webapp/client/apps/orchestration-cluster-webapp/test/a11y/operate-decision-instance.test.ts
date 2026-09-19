/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import {test, expect} from '#/pw-modules/test-extend';
import {
	mockCurrentUserEndpoint,
	mockGetDecisionDefinitionXmlEndpoint,
	mockGetDecisionInstanceEndpoint,
	mockGetIncidentProcessInstanceStatisticsByErrorEndpoint,
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockLicenseEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE} from '#/shared-test-modules/api-mocks/decision-definition-xmls';
import {createDecisionInstance} from '#/shared-test-modules/api-mocks/decision-instances';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';

const DECISION_INSTANCE_ID = '4294980768';

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
		mockGetDecisionInstanceEndpoint({
			successResponse: HttpResponse.json(
				createDecisionInstance({
					decisionEvaluationInstanceKey: DECISION_INSTANCE_ID,
					decisionDefinitionId: 'invoiceClassification',
				}),
			),
		}),
		mockGetDecisionDefinitionXmlEndpoint({
			successResponse: HttpResponse.text(DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE),
		}),
	);
});

test('should have no accessibility violations for a loaded decision instance panel', async ({
	makeAxeBuilder,
	operateDecisionInstancePage,
}) => {
	await operateDecisionInstancePage.goto(DECISION_INSTANCE_ID);
	await expect(operateDecisionInstancePage.decisionTableLabel).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().include('[aria-label="decision panel"]').analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});
