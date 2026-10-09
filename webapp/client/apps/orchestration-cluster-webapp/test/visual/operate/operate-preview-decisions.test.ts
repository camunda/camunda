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
	mockLicenseEndpoint,
	mockQueryDecisionDefinitionsEndpoint,
	mockQueryDecisionInstancesEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createQueryDecisionDefinitionsResponse} from '#/shared-test-modules/api-mocks/decision-definitions';
import {createQueryDecisionInstancesResponse} from '#/shared-test-modules/api-mocks/decision-instances';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
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
			successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse({items: []})),
		}),
		mockQueryDecisionInstancesEndpoint({
			successResponse: HttpResponse.json(createQueryDecisionInstancesResponse()),
		}),
	);
});

test('should match the resized decisions panels snapshot after dragging the resize handle', async ({
	operatePreviewDecisionsPage,
	page,
}) => {
	await operatePreviewDecisionsPage.goto();
	await expect(operatePreviewDecisionsPage.instancesTable).toBeVisible();

	await operatePreviewDecisionsPage.panelResizeHandle.dragTo(operatePreviewDecisionsPage.decisionPanel, {
		force: true,
	});

	await expect(page.locator('body')).not.toHaveClass(/cursor-ns-resize/);
	await expect
		.poll(() =>
			page.evaluate(() => {
				const panelStates = JSON.parse(localStorage.getItem('operate.panelStates') ?? '{}');
				return panelStates['decisions-instances-vertical-panel']?.[0];
			}),
		)
		.toBeLessThan(50);
	await expect(page).toHaveScreenshot();
});
