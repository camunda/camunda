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
import {DMN_XML} from '#/shared-test-modules/api-mocks/decision-definition-xmls';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {
	createDecisionDefinition,
	createQueryDecisionDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/decision-definitions';
import {createQueryDecisionInstancesResponse} from '#/shared-test-modules/api-mocks/decision-instances';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';

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
		await expect(operatePreviewDecisionsPage.moreFiltersButton).toBeVisible();
		await expect(operatePreviewDecisionsPage.decisionPanel).toBeVisible();
		await expect(operatePreviewDecisionsPage.decisionPanel.getByText('There is no Decision selected')).toBeVisible();
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
		await expect(operatePreviewDecisionsPage.decisionPanel.getByText('There is no Decision selected')).toBeVisible();
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
			operatePreviewDecisionsPage.decisionPanel.getByRole('button', {name: /Delete Decision Definition/}),
		).toBeVisible();
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
