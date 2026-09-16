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
import {createDecisionInstance} from '#/shared-test-modules/api-mocks/decision-instances';
import {DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE} from '#/shared-test-modules/api-mocks/decision-definition-xmls';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createPaginatedResponse, createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';

const DECISION_INSTANCE_ID = '4294980768';
const DECISION_DEFINITION_KEY = '2251799813685253';
const DECISION_INSTANCE = createDecisionInstance({
	decisionEvaluationInstanceKey: DECISION_INSTANCE_ID,
	decisionDefinitionKey: DECISION_DEFINITION_KEY,
	decisionDefinitionId: 'invoiceClassification',
});

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
		mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.json(DECISION_INSTANCE)}),
		mockGetDecisionDefinitionXmlEndpoint({
			successResponse: HttpResponse.text(DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE),
		}),
	);
});

test('should render the loading state and then the evaluated DMN panel on direct entry', async ({
	network,
	operateDecisionInstancePage,
}) => {
	network.use(
		mockGetDecisionDefinitionXmlEndpoint({
			successResponse: HttpResponse.text(DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE),
			delay: 1200,
		}),
	);

	await operateDecisionInstancePage.goto(DECISION_INSTANCE_ID);

	await expect(operateDecisionInstancePage.loadingSpinner).toBeVisible();
	await expect(operateDecisionInstancePage.decisionTableLabel).toBeVisible();
});

test('should render panel-level forbidden state when xml access is denied', async ({
	network,
	operateDecisionInstancePage,
}) => {
	network.use(
		mockGetDecisionDefinitionXmlEndpoint({
			successResponse: HttpResponse.json(createProblemDetails({status: 403}), {status: 403}),
		}),
	);

	await operateDecisionInstancePage.goto(DECISION_INSTANCE_ID);

	await expect(operateDecisionInstancePage.xmlForbiddenMessage).toBeVisible();
	await expect(operateDecisionInstancePage.pageErrorHeading).not.toBeVisible();
});

test('should render panel-level generic error when xml loading fails', async ({
	network,
	operateDecisionInstancePage,
}) => {
	network.use(
		mockGetDecisionDefinitionXmlEndpoint({
			successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
		}),
	);

	await operateDecisionInstancePage.goto(DECISION_INSTANCE_ID);

	await expect(operateDecisionInstancePage.panelErrorMessage).toBeVisible();
	await expect(operateDecisionInstancePage.pageErrorHeading).not.toBeVisible();
});

test('should preserve page-level error and allow recovering by retrying the decision-instance query', async ({
	network,
	operateDecisionInstancePage,
}) => {
	network.use(
		mockGetDecisionInstanceEndpoint({
			successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
		}),
	);

	await operateDecisionInstancePage.goto(DECISION_INSTANCE_ID);

	await expect(operateDecisionInstancePage.pageErrorHeading).toBeVisible({timeout: 15000});

	network.use(mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.json(DECISION_INSTANCE)}));
	await operateDecisionInstancePage.pageErrorRetryButton.click();

	await expect(operateDecisionInstancePage.decisionTableLabel).toBeVisible();
});
