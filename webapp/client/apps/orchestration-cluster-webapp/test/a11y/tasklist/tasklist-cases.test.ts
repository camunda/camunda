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
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['tasklist']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
	);
});

test('should have no accessibility violations in the no open cases empty state', async ({
	tasklistCasesPage,
	makeAxeBuilder,
}) => {
	await tasklistCasesPage.goto();
	await expect(tasklistCasesPage.noOpenCasesHeading).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations in the no matching cases empty state', async ({
	tasklistCasesPage,
	makeAxeBuilder,
}) => {
	await tasklistCasesPage.goto('?search=missing');
	await expect(tasklistCasesPage.noMatchingCasesHeading).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});
