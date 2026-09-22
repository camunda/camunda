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
	mockLoginEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: new HttpResponse(null, {status: 401})}),
		mockLoginEndpoint({successResponse: new HttpResponse(null, {status: 200})}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['admin']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
	);
});

test('should redirect the Admin index to Admin login and return after login', async ({
	network,
	page,
	adminIndexPage,
	adminLoginPage,
}) => {
	await adminLoginPage.gotoAdmin();

	await expect(page).toHaveURL('/admin/login');
	await expect(adminLoginPage.title).toBeVisible();
	await expect(adminLoginPage.usernameInput).toBeVisible();

	network.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}));

	await adminLoginPage.fillCredentials('demo', 'demo');
	await adminLoginPage.submitButton.click();

	await expect(page).toHaveURL('/admin');
	await expect(adminIndexPage.heading).toBeVisible();
});

test('should preserve an Admin URL through login', async ({network, page, adminIndexPage, adminLoginPage}) => {
	await adminLoginPage.gotoAdmin('/users');

	await expect(page).toHaveURL((url) => {
		return url.pathname === '/admin/login' && url.searchParams.get('redirect') === '/admin/users';
	});
	await expect(adminLoginPage.usernameInput).toBeVisible();

	network.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}));

	await adminLoginPage.fillCredentials('demo', 'demo');
	await adminLoginPage.submitButton.click();

	await expect(page).toHaveURL('/admin/users');
	await expect(adminIndexPage.sectionHeading('Users')).toBeVisible();
});

test('should show an error for wrong credentials', async ({network, adminLoginPage}) => {
	network.use(mockLoginEndpoint({successResponse: new HttpResponse(null, {status: 401})}));

	await adminLoginPage.goto();
	await adminLoginPage.fillCredentials('demo', 'wrong-password');
	await adminLoginPage.submitButton.click();

	await expect(adminLoginPage.errorMessage).toContainText(/username and password do not match/i);
});

test('should show a generic error message', async ({network, adminLoginPage}) => {
	network.use(mockLoginEndpoint({successResponse: new HttpResponse(null, {status: 500})}));

	await adminLoginPage.goto();
	await adminLoginPage.fillCredentials('demo', 'demo');
	await adminLoginPage.submitButton.click();

	await expect(adminLoginPage.errorMessage).toContainText(/couldn't verify credentials/i);
});

test('should show a loading state while the login form is submitting', async ({network, adminLoginPage}) => {
	network.use(
		mockLoginEndpoint({
			successResponse: new HttpResponse(null, {status: 200}),
			delay: 500,
		}),
	);

	await adminLoginPage.goto();
	await adminLoginPage.fillCredentials('demo', 'demo');
	await adminLoginPage.submitButton.click();

	await expect(adminLoginPage.loadingButton).toBeVisible();
	await expect(adminLoginPage.loadingButton).toHaveAttribute('aria-busy', 'true');
	await expect(adminLoginPage.loadingButton).toHaveAttribute('aria-disabled', 'true');
});

test.describe('redirect validation', () => {
	for (const redirect of [
		'/tasklist',
		'/administration',
		'//evil.example',
		'https://evil.example',
		'/admin/login',
		'/admin/login?redirect=/admin',
	]) {
		test(`should reject ${redirect} as a redirect`, async ({adminLoginPage}) => {
			await adminLoginPage.goto(redirect);

			await expect(adminLoginPage.genericErrorHeading).toBeVisible();
		});
	}
});
