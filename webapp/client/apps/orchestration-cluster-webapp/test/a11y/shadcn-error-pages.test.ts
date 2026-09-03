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

test('should have no accessibility violations on the 404 page', async ({page, notFoundPage, makeAxeBuilder}) => {
	await page.goto('/shadcn/nonexistent-path');
	await expect(notFoundPage.heading).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations on the forbidden page', async ({
	network,
	page,
	forbiddenPage,
	makeAxeBuilder,
}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration()),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
	);

	await page.goto('/shadcn/tasklist');
	await expect(forbiddenPage.heading).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations on the generic error page', async ({network, page, makeAxeBuilder}) => {
	network.use(mockCurrentUserEndpoint({successResponse: new HttpResponse(null, {status: 401})}));

	await page.goto('/shadcn/tasklist/login?redirect=http://evil.com');
	await expect(page.getByRole('heading', {name: 'Something went wrong'})).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations on the tasklist 404 page', async ({
	network,
	page,
	notFoundPage,
	makeAxeBuilder,
}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['tasklist']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
	);

	await page.goto('/shadcn/tasklist/nonexistent/page');
	await expect(notFoundPage.heading).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});
