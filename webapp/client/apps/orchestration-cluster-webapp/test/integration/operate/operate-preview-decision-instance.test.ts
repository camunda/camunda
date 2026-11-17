/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse, http} from 'msw';
import {endpoints} from '@camunda/camunda-api-zod-schemas/8.11';
import {test, expect} from '#/pw-modules/test-extend';
import {
	mockCurrentUserEndpoint,
	mockGetDecisionDefinitionXmlEndpoint,
	mockGetDecisionInstanceEndpoint,
	mockGetIncidentProcessInstanceStatisticsByErrorEndpoint,
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockLicenseEndpoint,
	mockQueryDecisionDefinitionsEndpoint,
	mockQueryDecisionInstancesEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {
	createDecisionInstance,
	createQueryDecisionInstancesResponse,
} from '#/shared-test-modules/api-mocks/decision-instances';
import {createQueryDecisionDefinitionsResponse} from '#/shared-test-modules/api-mocks/decision-definitions';
import {DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE} from '#/shared-test-modules/api-mocks/decision-definition-xmls';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createPaginatedResponse, createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';

const DECISION_INSTANCE_ID = '4294980768';
const RELATED_DECISION_INSTANCE_ID = '4294980769';
const DECISION_INSTANCE = createDecisionInstance({
	decisionEvaluationInstanceKey: DECISION_INSTANCE_ID,
	decisionDefinitionKey: '2251799813685253',
	decisionDefinitionId: 'invoiceClassification',
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
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockGetProcessDefinitionInstanceStatisticsEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse()),
		}),
		mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse()),
		}),
		mockQueryDecisionDefinitionsEndpoint({
			successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse()),
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

test.describe('Operate Decision Instance DS preview (/operate-preview/decisions/:id)', () => {
	test('should render the header, decision panel, DRD and variables', async ({page, operateDecisionInstancePage}) => {
		await operateDecisionInstancePage.gotoPreview(DECISION_INSTANCE_ID);

		await expect(page.getByTestId('instance-header')).toContainText(DECISION_INSTANCE_ID);
		await expect(page).toHaveTitle(`Operate: Decision Instance ${DECISION_INSTANCE_ID} of Invoice Classification`);
		await expect(operateDecisionInstancePage.decisionTableLabel).toBeVisible();
		await expect(operateDecisionInstancePage.drdPanel.getByTestId('state-overlay-EVALUATED')).toBeVisible();
		await expect(page.getByRole('tab', {name: 'Inputs and outputs'})).toBeVisible();
		await expect(page.getByRole('tab', {name: 'Result'})).toBeVisible();
	});

	test('should show evaluated inputs, matched outputs and the result', async ({page, operateDecisionInstancePage}) => {
		await operateDecisionInstancePage.gotoPreview(DECISION_INSTANCE_ID);

		const variablesPanel = page.getByTestId('decision-instance-variables-panel');
		await expect(variablesPanel.getByText('300')).toBeVisible();
		await expect(variablesPanel.getByText('"manager approval"')).toBeVisible();

		await page.getByRole('tab', {name: 'Result'}).click();
		await expect(page.getByTestId('results-json-viewer')).toContainText('manager approval');
	});

	test('should maximize, minimize, close and reopen the DRD and keep it after a reload', async ({
		page,
		operateDecisionInstancePage,
	}) => {
		await operateDecisionInstancePage.gotoPreview(DECISION_INSTANCE_ID);
		await expect(operateDecisionInstancePage.drdPanel).toBeVisible();

		await operateDecisionInstancePage.maximizeDrd.click();
		await expect(operateDecisionInstancePage.minimizeDrd).toBeFocused();
		await expect(page.getByTestId('instance-header')).not.toBeVisible();

		await page.reload();
		await expect(operateDecisionInstancePage.minimizeDrd).toBeVisible();
		await operateDecisionInstancePage.minimizeDrd.click();
		await expect(operateDecisionInstancePage.drdPanel).toBeVisible();

		await operateDecisionInstancePage.closeDrd.click();
		await expect(operateDecisionInstancePage.drdPanel).not.toBeVisible();
		await page.reload();
		await expect(operateDecisionInstancePage.drdPanel).not.toBeVisible();

		await page.getByRole('button', {name: 'Open decision requirements diagram'}).click();
		await expect(operateDecisionInstancePage.drdPanel).toBeVisible();
	});

	test('should navigate to the related evaluated decision selected in the DRD', async ({
		network,
		page,
		operateDecisionInstancePage,
	}) => {
		const relatedInstance = createDecisionInstance({
			...DECISION_INSTANCE,
			decisionDefinitionId: 'calc-key-figures',
			decisionDefinitionName: 'Calculate Credit History Key Figures',
			decisionEvaluationInstanceKey: RELATED_DECISION_INSTANCE_ID,
			state: 'FAILED',
		});
		network.use(
			http.get(
				endpoints.getDecisionInstance.getUrl({decisionEvaluationInstanceKey: RELATED_DECISION_INSTANCE_ID}),
				() => HttpResponse.json(relatedInstance),
			),
			http.get(endpoints.getDecisionInstance.getUrl({decisionEvaluationInstanceKey: DECISION_INSTANCE_ID}), () =>
				HttpResponse.json(DECISION_INSTANCE),
			),
			mockQueryDecisionInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryDecisionInstancesResponse({items: [DECISION_INSTANCE, relatedInstance]}),
				),
			}),
		);
		await operateDecisionInstancePage.gotoPreview(DECISION_INSTANCE_ID);

		const relatedDecision = operateDecisionInstancePage.drdPanel.getByRole('button', {
			name: 'Calculate Credit History Key Figures',
		});
		await relatedDecision.focus();
		await relatedDecision.press('Enter');

		await expect(page).toHaveURL(new RegExp(`/operate-preview/decisions/${RELATED_DECISION_INSTANCE_ID}$`));
		await expect(page.getByTestId('instance-header')).toContainText('Calculate Credit History Key Figures');
	});

	test('should redirect to the decisions list with a notification when the instance is not found', async ({
		network,
		page,
		operateDecisionInstancePage,
	}) => {
		network.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 404}), {status: 404}),
			}),
		);
		await operateDecisionInstancePage.gotoPreview(DECISION_INSTANCE_ID);

		await expect(page).toHaveURL(/\/operate-preview\/decisions(\?|$)/);
		await expect(page.getByText(`Couldn't find decision instance ${DECISION_INSTANCE_ID}`)).toBeVisible();
	});
});
