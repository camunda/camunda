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
	mockCreateDecisionInstancesDeletionBatchOperationEndpoint,
	mockCurrentUserEndpoint,
	mockGetDecisionDefinitionXmlEndpoint,
	mockGetIncidentProcessInstanceStatisticsByErrorEndpoint,
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockLicenseEndpoint,
	mockQueryDecisionDefinitionsEndpoint,
	mockQueryDecisionInstancesEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {DMN_XML} from '#/shared-test-modules/api-mocks/decision-definition-xmls';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {
	createDecisionDefinition,
	createQueryDecisionDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/decision-definitions';
import {
	createDecisionInstance,
	createQueryDecisionInstancesResponse,
} from '#/shared-test-modules/api-mocks/decision-instances';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';

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
		mockGetProcessDefinitionInstanceStatisticsEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse()),
		}),
		mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse()),
		}),
		mockQueryDecisionDefinitionsEndpoint({
			successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse()),
		}),
	);
});

test.describe('Operate Decisions DS preview (/operate-preview/decisions)', () => {
	test('should render the placeholder shell', async ({network, operatePreviewDecisionsPage}) => {
		network.use(
			mockQueryDecisionInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionInstancesResponse()),
			}),
		);
		await operatePreviewDecisionsPage.goto();

		await expect(operatePreviewDecisionsPage.heading).toBeAttached();
		await expect(operatePreviewDecisionsPage.moreFiltersCombobox).toBeVisible();
		await expect(operatePreviewDecisionsPage.decisionPanel).toBeVisible();
		await expect(operatePreviewDecisionsPage.decisionPanel.getByText('There is no decision selected')).toBeVisible();
		await expect(operatePreviewDecisionsPage.instancesTable).toBeVisible();
	});

	test('should clear a stale decision selection from the URL and notify', async ({
		network,
		operatePreviewDecisionsPage,
		page,
	}) => {
		network.use(
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse({items: []})),
			}),
			mockQueryDecisionInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionInstancesResponse()),
			}),
		);
		await operatePreviewDecisionsPage.goto('?evaluated=true&failed=true&decisionDefinitionId=missing');

		await expect(page).not.toHaveURL(/decisionDefinitionId/);
		await expect(page.getByText("Couldn't find the decision", {exact: true})).toBeVisible();
		await expect(operatePreviewDecisionsPage.decisionPanel.getByText('There is no decision selected')).toBeVisible();
	});

	test('should offer deleting the selected decision definition version', async ({
		network,
		operatePreviewDecisionsPage,
	}) => {
		network.use(
			mockQueryDecisionInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionInstancesResponse()),
			}),
			mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(DMN_XML)}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(
					createQueryDecisionDefinitionsResponse({
						items: [
							createDecisionDefinition({name: 'Invoice Classification', decisionDefinitionId: 'invoice', version: 1}),
						],
					}),
				),
			}),
		);
		await operatePreviewDecisionsPage.goto('?decisionDefinitionId=invoice&decisionDefinitionVersion=1');

		await expect(
			operatePreviewDecisionsPage.decisionPanel.getByRole('button', {name: /Delete decision definition/}),
		).toBeVisible();
	});

	test('should delete the selected decision instances in bulk', async ({
		network,
		operatePreviewDecisionsPage,
		page,
	}) => {
		network.use(
			mockQueryDecisionInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryDecisionInstancesResponse({
						items: [
							createDecisionInstance({decisionEvaluationInstanceKey: '111'}),
							createDecisionInstance({decisionEvaluationInstanceKey: '222'}),
						],
					}),
				),
			}),
			mockCreateDecisionInstancesDeletionBatchOperationEndpoint({
				successResponse: HttpResponse.json({
					batchOperationKey: 'batch-1',
					batchOperationType: 'DELETE_DECISION_INSTANCE',
				}),
			}),
		);
		await operatePreviewDecisionsPage.goto('?evaluated=true&failed=true');

		await operatePreviewDecisionsPage.instancesTable.getByRole('checkbox').nth(1).check();
		await page.getByRole('button', {name: 'Delete', exact: true}).click();
		const deletionRequest = page.waitForRequest(
			(request) => request.method() === 'POST' && request.url().includes('decision-instances/deletion'),
		);
		await page.getByRole('alertdialog').getByRole('button', {name: 'Delete', exact: true}).click();

		await expect(page.getByText(/The batch operation .* has been started/)).toBeVisible();
		expect((await deletionRequest).postDataJSON()).toMatchObject({
			filter: {decisionEvaluationInstanceKey: {$in: ['111']}},
		});
	});

	test('should collapse and expand the filter sidebar', async ({network, operatePreviewDecisionsPage, page}) => {
		network.use(
			mockQueryDecisionInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionInstancesResponse()),
			}),
		);
		await operatePreviewDecisionsPage.goto('?evaluated=true&failed=true');

		await page.getByRole('button', {name: 'Collapse Filter'}).click();
		await expect(page.getByTestId('collapsed-panel')).toBeVisible();

		await page.getByRole('button', {name: 'Expand Filter'}).click();
		await expect(page.getByTestId('expanded-panel')).toBeVisible();
	});

	test('should sort the instances by evaluation date', async ({network, operatePreviewDecisionsPage, page}) => {
		network.use(
			mockQueryDecisionInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryDecisionInstancesResponse({
						items: [createDecisionInstance({decisionEvaluationInstanceKey: '111'})],
					}),
				),
			}),
		);
		await operatePreviewDecisionsPage.goto('?evaluated=true&failed=true');
		await expect(operatePreviewDecisionsPage.instancesTable).toBeVisible();

		const sortRequest = page.waitForRequest(
			(request) =>
				request.method() === 'POST' &&
				request.url().includes('decision-instances/search') &&
				JSON.stringify(request.postDataJSON()?.sort) === JSON.stringify([{field: 'evaluationDate', order: 'asc'}]),
		);
		await operatePreviewDecisionsPage.instancesTable.getByRole('button', {name: 'Evaluation date'}).click();

		await sortRequest;
	});

	test('should load the next page when scrolling to the end of the instances table', async ({
		network,
		operatePreviewDecisionsPage,
		page,
	}) => {
		const firstPage = Array.from({length: 50}, (_, index) =>
			createDecisionInstance({decisionEvaluationInstanceKey: `first-${index}`}),
		);
		network.use(
			mockQueryDecisionInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryDecisionInstancesResponse({items: firstPage, page: {totalItems: 120}}),
				),
			}),
		);
		await operatePreviewDecisionsPage.goto('?evaluated=true&failed=true');
		await expect(operatePreviewDecisionsPage.instancesTable).toBeVisible();

		const nextPageRequest = page.waitForRequest(
			(request) =>
				request.method() === 'POST' &&
				request.url().includes('decision-instances/search') &&
				request.postDataJSON()?.page?.from === 50,
		);
		await operatePreviewDecisionsPage.instancesTable.getByRole('row').last().scrollIntoViewIfNeeded();
		await operatePreviewDecisionsPage.instancesTable.hover();
		await page.mouse.wheel(0, 100000);

		await nextPageRequest;
	});

	test('should render the decision viewer in dark mode', async ({network, operatePreviewDecisionsPage, page}) => {
		await page.emulateMedia({colorScheme: 'dark'});
		network.use(
			mockQueryDecisionInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionInstancesResponse()),
			}),
			mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(DMN_XML)}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(
					createQueryDecisionDefinitionsResponse({
						items: [
							createDecisionDefinition({name: 'Invoice Classification', decisionDefinitionId: 'invoice', version: 1}),
						],
					}),
				),
			}),
		);
		await operatePreviewDecisionsPage.goto('?decisionDefinitionId=invoice&decisionDefinitionVersion=1');

		await expect(operatePreviewDecisionsPage.decisionViewer).toBeVisible();
		await expect(operatePreviewDecisionsPage.decisionViewer.getByText('Invoice Amount')).toBeVisible();
	});

	test('should keep the Carbon Decisions route working', async ({network, operateDecisionsPage, page}) => {
		network.use(
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse()),
			}),
			mockQueryDecisionInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionInstancesResponse()),
			}),
		);
		await operateDecisionsPage.goto('?evaluated=true&failed=true');

		await expect(page.getByRole('combobox', {name: 'Name', exact: true})).toBeVisible();
	});
});
