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
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {
	createDecisionInstance,
	createQueryDecisionInstancesResponse,
} from '#/shared-test-modules/api-mocks/decision-instances';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {
	createDecisionDefinition,
	createQueryDecisionDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/decision-definitions';
import {DMN_XML} from '#/shared-test-modules/api-mocks/decision-definition-xmls';

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
			successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse({items: []})),
		}),
		mockQueryDecisionInstancesEndpoint({
			successResponse: HttpResponse.json(createQueryDecisionInstancesResponse()),
		}),
	);
});

test('should match the resized decisions panels snapshot after dragging the resize handle', async ({
	operateDecisionsPage,
	page,
}) => {
	await operateDecisionsPage.goto();
	await expect(operateDecisionsPage.instancesTable).toBeVisible();

	await operateDecisionsPage.panelResizeHandle.dragTo(operateDecisionsPage.decisionPanel, {
		force: true,
	});

	await expect(page.locator('body')).not.toHaveClass(/cursor-ns-resize/);
	await expect
		.poll(() =>
			page.evaluate(() => {
				const panelStates = JSON.parse(localStorage.getItem('operate.panelStates') ?? '{}');
				return panelStates['decision-instances-vertical-panel']?.[0];
			}),
		)
		.toBeLessThan(50);
	await expect(page).toHaveScreenshot();
});

test('should match the filters panel snapshot with populated optional filters', async ({network, page}) => {
	network.use(
		mockQueryDecisionInstancesEndpoint({
			successResponse: HttpResponse.json(createQueryDecisionInstancesResponse()),
		}),
	);
	await page.goto(
		'/operate/decisions?evaluated=true&failed=true&businessId=eq_order-1&processInstanceKey=2251799813685249&evaluationDateFrom=2024-01-01T00:00:00.000Z&evaluationDateTo=2024-01-02T00:00:00.000Z',
	);
	const filtersPanel = page.getByRole('region', {name: 'Filter', exact: true});
	await expect(page.getByLabel('Business ID', {exact: true})).toHaveValue('order-1');

	await expect(filtersPanel).toHaveScreenshot();
});

test('should match the evaluation date range field snapshot', async ({operateDecisionsPage, page}) => {
	await operateDecisionsPage.goto();
	await operateDecisionsPage.moreFiltersButton.click();
	await page.getByRole('menuitem', {name: 'Evaluation date range'}).click();
	const dateRange = page.getByRole('group', {name: 'Evaluation date range'});
	await expect(dateRange).toBeVisible();

	await expect(dateRange).toHaveScreenshot();
});

test('should match the instances table snapshot with evaluated and failed instances', async ({
	network,
	operateDecisionsPage,
	page,
}) => {
	network.use(
		mockQueryDecisionInstancesEndpoint({
			successResponse: HttpResponse.json(
				createQueryDecisionInstancesResponse({
					items: [
						createDecisionInstance({decisionEvaluationInstanceKey: '2251799813700001', businessId: 'order-1'}),
						createDecisionInstance({
							decisionEvaluationInstanceKey: '2251799813700002',
							state: 'FAILED',
							decisionDefinitionName: 'Shipping Method',
							decisionDefinitionVersion: 3,
							processInstanceKey: '2251799813685999',
						}),
						createDecisionInstance({
							decisionEvaluationInstanceKey: '2251799813700003',
							decisionDefinitionName: 'Invoice Classification',
							decisionDefinitionVersion: 2,
						}),
					],
				}),
			),
		}),
	);
	await page.goto(`${operateDecisionsPage.decisionsUrl}?evaluated=true&failed=true`);

	await expect(operateDecisionsPage.instancesTable.getByText('2251799813700001')).toBeVisible();
	await expect(operateDecisionsPage.instancesTable).toHaveScreenshot();
});

test('should match the instances table snapshot when no instances match', async ({
	network,
	operateDecisionsPage,
	page,
}) => {
	network.use(
		mockQueryDecisionInstancesEndpoint({
			successResponse: HttpResponse.json(createQueryDecisionInstancesResponse()),
		}),
	);
	await page.goto(`${operateDecisionsPage.decisionsUrl}?evaluated=true&failed=true`);

	await expect(operateDecisionsPage.instancesTable.getByText(/no instances matching/)).toBeVisible();
	await expect(operateDecisionsPage.instancesTable).toHaveScreenshot();
});

test('should match the instances table snapshot when loading fails', async ({network, operateDecisionsPage, page}) => {
	network.use(
		mockQueryDecisionInstancesEndpoint({
			successResponse: new HttpResponse(null, {status: 500}),
		}),
	);
	await page.goto(`${operateDecisionsPage.decisionsUrl}?evaluated=true&failed=true`);

	await expect(operateDecisionsPage.instancesTable.getByText("Couldn't fetch data")).toBeVisible({
		timeout: 30_000,
	});
	await expect(operateDecisionsPage.instancesTable).toHaveScreenshot();
});

test('should match the decisions page snapshot with a selected decision definition', async ({
	operateDecisionsPage,
	network,
	page,
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
		mockQueryDecisionInstancesEndpoint({
			successResponse: HttpResponse.json(
				createQueryDecisionInstancesResponse({
					items: [
						createDecisionInstance({
							decisionEvaluationInstanceKey: '1',
							decisionDefinitionName: 'Invoice Classification',
						}),
					],
				}),
			),
		}),
	);

	await operateDecisionsPage.goto(
		'?evaluated=true&failed=true&decisionDefinitionId=invoiceClassification&decisionDefinitionVersion=1',
	);
	await expect(page.getByRole('heading', {name: 'Invoice Classification'})).toBeVisible();
	await expect(operateDecisionsPage.decisionPanel.getByTestId('decision-viewer')).toBeVisible();
	await expect(page.getByText('Invoice Amount')).toBeVisible();
	await expect(page).toHaveScreenshot();
});

test('should match the decisions page with the filter sidebar collapsed', async ({operateDecisionsPage, page}) => {
	await operateDecisionsPage.goto('?evaluated=true&failed=true');
	await page.getByRole('button', {name: 'Collapse Filter'}).click();
	await expect(page.getByTestId('collapsed-panel')).toBeVisible();

	await expect(page).toHaveScreenshot();
});
