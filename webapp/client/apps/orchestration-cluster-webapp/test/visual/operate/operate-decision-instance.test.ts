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
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {
	createDecisionInstance,
	createQueryDecisionInstancesResponse,
} from '#/shared-test-modules/api-mocks/decision-instances';
import {DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE} from '#/shared-test-modules/api-mocks/decision-definition-xmls';

const DMN_XML_WITHOUT_DRD = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" id="no_drd_defs" name="No DRD" namespace="http://camunda.org/schema/1.0/dmn">
  <decision id="invoiceClassification" name="Invoice Classification">
    <literalExpression id="literalExpression_1">
      <text>"manager approval"</text>
    </literalExpression>
  </decision>
</definitions>`;

const DECISION_INSTANCE_KEY = '4294980768';
const DECISION_INSTANCE = createDecisionInstance({
	decisionEvaluationInstanceKey: DECISION_INSTANCE_KEY,
	decisionDefinitionId: 'invoiceClassification',
	decisionDefinitionName: 'Invoice Classification',
	evaluatedInputs: [{inputId: 'clause1', inputName: 'Invoice Amount', inputValue: '300'}],
	matchedRules: [
		{
			ruleId: 'DecisionRule_2',
			ruleIndex: 2,
			evaluatedOutputs: [
				{
					outputId: 'clause3',
					outputName: 'Classification',
					outputValue: '"manager approval"',
					ruleId: 'DecisionRule_2',
					ruleIndex: 2,
				},
			],
		},
	],
	result: '"manager approval"',
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
		mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.json(DECISION_INSTANCE)}),
		mockQueryDecisionInstancesEndpoint({
			successResponse: HttpResponse.json(createQueryDecisionInstancesResponse({items: [DECISION_INSTANCE]})),
		}),
		mockGetDecisionDefinitionXmlEndpoint({
			successResponse: HttpResponse.text(DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE),
		}),
	);
});

test('should match the decision instance page snapshot with the matched rule highlighted', async ({
	operateDecisionInstancePage,
	page,
}) => {
	await operateDecisionInstancePage.goto(DECISION_INSTANCE_KEY);

	await expect(page.getByTestId('instance-header')).toBeVisible();
	await expect(operateDecisionInstancePage.decisionTableLabel).toBeVisible();
	await expect(operateDecisionInstancePage.drdPanel.getByTestId('state-overlay-EVALUATED')).toBeVisible();
	await expect(page.getByTestId('decision-instance-variables-panel').getByText('"manager approval"')).toBeVisible();
	await expect(page).toHaveScreenshot();
});

test('should match the decision instance page snapshot with the DRD maximized', async ({
	operateDecisionInstancePage,
	page,
}) => {
	await operateDecisionInstancePage.goto(DECISION_INSTANCE_KEY);
	await expect(operateDecisionInstancePage.drdPanel.getByTestId('state-overlay-EVALUATED')).toBeVisible();

	await page.getByRole('button', {name: 'Maximize DRD panel'}).click();

	await expect(page.getByRole('button', {name: 'Minimize DRD panel'})).toBeFocused();
	await expect(page.getByTestId('drd').getByText(DECISION_INSTANCE_KEY)).toBeVisible();
	await expect(page).toHaveScreenshot();
});

test('should match the decision instance page snapshot when the decision has no DRD', async ({
	operateDecisionInstancePage,
	network,
	page,
}) => {
	network.use(
		mockGetDecisionDefinitionXmlEndpoint({
			successResponse: HttpResponse.text(DMN_XML_WITHOUT_DRD),
		}),
	);

	await operateDecisionInstancePage.goto(DECISION_INSTANCE_KEY);

	await expect(page.getByText('No diagram available')).toBeVisible();
	await expect(page).toHaveScreenshot();
});
