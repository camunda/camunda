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
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
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
	);
});

test('should have no accessibility violations in the decisions placeholder shell', async ({
	operatePreviewDecisionsPage,
	makeAxeBuilder,
}) => {
	await operatePreviewDecisionsPage.goto();
	await expect(operatePreviewDecisionsPage.moreFiltersButton).toBeVisible();

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
