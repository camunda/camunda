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

test('should match the 404 page snapshot', async ({page, notFoundPage}) => {
	await page.goto('/nonexistent-path');
	await expect(notFoundPage.heading).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the forbidden page snapshot', async ({network, page, forbiddenPage}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration()),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
	);

	await page.goto('/operate');
	await expect(forbiddenPage.heading).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the generic error page snapshot', async ({network, page}) => {
	network.use(mockCurrentUserEndpoint({successResponse: new HttpResponse(null, {status: 401})}));

	await page.goto('/login?redirect=http://evil.com');
	await expect(page.getByRole('heading', {name: 'Something went wrong'})).toBeVisible();

	await expect(page).toHaveScreenshot();
});
