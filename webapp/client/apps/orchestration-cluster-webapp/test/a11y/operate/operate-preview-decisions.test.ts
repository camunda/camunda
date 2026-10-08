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
	mockLicenseEndpoint,
	mockQueryDecisionDefinitionsEndpoint,
	mockQueryDecisionInstancesEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {
	createDecisionInstance,
	createQueryDecisionInstancesResponse,
} from '#/shared-test-modules/api-mocks/decision-instances';
import {
	createDecisionDefinition,
	createQueryDecisionDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/decision-definitions';
import {DMN_XML} from '#/shared-test-modules/api-mocks/decision-definition-xmls';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';

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
		mockQueryDecisionDefinitionsEndpoint({
			successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse()),
		}),
		mockQueryDecisionInstancesEndpoint({
			successResponse: HttpResponse.json(
				createQueryDecisionInstancesResponse({
					items: [
						createDecisionInstance({decisionEvaluationInstanceKey: '1', decisionDefinitionName: 'Invoice Approval'}),
						createDecisionInstance({
							decisionEvaluationInstanceKey: '2',
							state: 'FAILED',
							processInstanceKey: undefined,
						}),
					],
				}),
			),
		}),
	);
});

test('should have no accessibility violations in the decisions shell with the instances table', async ({
	page,
	operatePreviewDecisionsPage,
	makeAxeBuilder,
}) => {
	await operatePreviewDecisionsPage.goto('?evaluated=true&failed=true');
	await expect(operatePreviewDecisionsPage.moreFiltersButton).toBeVisible();
	await expect(operatePreviewDecisionsPage.instancesTable).toBeVisible();
	await expect(page.getByText('Invoice Approval')).toBeVisible();

	const results = await makeAxeBuilder().analyze();
	expect(results.violations).toEqual([]);
});

test('should have no accessibility violations with a selected row and the bulk delete dialog open', async ({
	page,
	operatePreviewDecisionsPage,
	makeAxeBuilder,
}) => {
	await operatePreviewDecisionsPage.goto('?evaluated=true&failed=true');
	await page.getByRole('checkbox', {name: 'Select row 1'}).click();
	await expect(page.getByRole('status')).toHaveText('1 item selected');
	await page.getByRole('button', {name: 'Delete', exact: true}).click();
	await expect(page.getByRole('alertdialog')).toBeVisible();

	const results = await makeAxeBuilder().analyze();
	expect(results.violations).toEqual([]);
});

test('should have no accessibility violations with populated optional filters', async ({
	page,
	operatePreviewDecisionsPage,
	makeAxeBuilder,
}) => {
	await page.goto(
		`${operatePreviewDecisionsPage.decisionsUrl}?evaluated=true&failed=true&businessId=eq_order-1&processInstanceKey=2251799813685249&evaluationDateFrom=2024-01-01T00:00:00.000Z&evaluationDateTo=2024-01-02T00:00:00.000Z`,
	);
	await expect(page.getByLabel('Business ID', {exact: true})).toHaveValue('order-1');

	const results = await makeAxeBuilder().analyze();
	expect(results.violations).toEqual([]);
});

test('should have no accessibility violations with a selected decision definition', async ({
	page,
	network,
	operatePreviewDecisionsPage,
	makeAxeBuilder,
}) => {
	network.use(
		mockQueryDecisionDefinitionsEndpoint({
			successResponse: HttpResponse.json(
				createQueryDecisionDefinitionsResponse({
					items: [
						createDecisionDefinition({
							name: 'Invoice Classification',
							decisionDefinitionId: 'invoiceClassification',
							version: 1,
						}),
					],
				}),
			),
		}),
		mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(DMN_XML)}),
	);
	await operatePreviewDecisionsPage.goto(
		'?evaluated=true&failed=true&decisionDefinitionId=invoiceClassification&decisionDefinitionVersion=1',
	);
	await expect(page.getByRole('heading', {name: 'Invoice Classification'})).toBeVisible();
	await expect(operatePreviewDecisionsPage.decisionPanel.getByTestId('decision-viewer')).toBeVisible();
	await expect(page.getByText('Invoice Amount')).toBeVisible();

	const results = await makeAxeBuilder().analyze();
	expect(results.violations).toEqual([]);
});
