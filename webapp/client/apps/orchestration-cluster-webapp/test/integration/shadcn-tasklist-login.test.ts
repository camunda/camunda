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
	mockQueryUserTasksEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createQueryUserTasksResponse} from '#/shared-test-modules/api-mocks/user-tasks';

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: new HttpResponse(null, {status: 401})}),
		mockLoginEndpoint({successResponse: new HttpResponse(null, {status: 200})}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['tasklist']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockQueryUserTasksEndpoint({
			successResponse: HttpResponse.json(createQueryUserTasksResponse({items: []})),
		}),
	);
});

test('should redirect the Tasklist index to Tasklist login and return after login', async ({
	network,
	page,
	shadcnTasklistIndexPage,
	shadcnTasklistLoginPage,
}) => {
	await shadcnTasklistLoginPage.gotoTasklist();

	await expect(page).toHaveURL('/shadcn/tasklist/login');
	await expect(shadcnTasklistLoginPage.title).toBeVisible();
	await expect(shadcnTasklistLoginPage.usernameInput).toBeVisible();

	network.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}));

	await shadcnTasklistLoginPage.fillCredentials('demo', 'demo');
	await shadcnTasklistLoginPage.submitButton.click();

	await expect(page).toHaveURL('/shadcn/tasklist');
	await expect(shadcnTasklistIndexPage.noTasksMessage).toBeVisible();
});

test('should preserve a Tasklist URL through login', async ({
	network,
	page,
	shadcnTasklistIndexPage,
	shadcnTasklistLoginPage,
}) => {
	await shadcnTasklistLoginPage.gotoTasklist('?filter=assigned');

	await expect(page).toHaveURL((url) => {
		return (
			url.pathname === '/shadcn/tasklist/login' &&
			url.searchParams.get('redirect') === '/shadcn/tasklist?filter=assigned'
		);
	});
	await expect(shadcnTasklistLoginPage.usernameInput).toBeVisible();

	network.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}));

	await shadcnTasklistLoginPage.fillCredentials('demo', 'demo');
	await shadcnTasklistLoginPage.submitButton.click();

	await expect(page).toHaveURL('/shadcn/tasklist?filter=assigned');
	await expect(shadcnTasklistIndexPage.noTasksMessage).toBeVisible();
});

test('should show an error for wrong credentials', async ({network, shadcnTasklistLoginPage}) => {
	network.use(mockLoginEndpoint({successResponse: new HttpResponse(null, {status: 401})}));

	await shadcnTasklistLoginPage.goto();
	await shadcnTasklistLoginPage.fillCredentials('demo', 'wrong-password');
	await shadcnTasklistLoginPage.submitButton.click();

	await expect(shadcnTasklistLoginPage.errorMessage).toContainText(/username and password do not match/i);
});

test('should show a generic error message', async ({network, shadcnTasklistLoginPage}) => {
	network.use(mockLoginEndpoint({successResponse: new HttpResponse(null, {status: 500})}));

	await shadcnTasklistLoginPage.goto();
	await shadcnTasklistLoginPage.fillCredentials('demo', 'demo');
	await shadcnTasklistLoginPage.submitButton.click();

	await expect(shadcnTasklistLoginPage.errorMessage).toContainText(/credentials could not be verified/i);
});

test('should show a loading state while the login form is submitting', async ({network, shadcnTasklistLoginPage}) => {
	network.use(
		mockLoginEndpoint({
			successResponse: new HttpResponse(null, {status: 200}),
			delay: 500,
		}),
	);

	await shadcnTasklistLoginPage.goto();
	await shadcnTasklistLoginPage.fillCredentials('demo', 'demo');
	await shadcnTasklistLoginPage.submitButton.click();

	await expect(shadcnTasklistLoginPage.loadingButton).toBeVisible();
	await expect(shadcnTasklistLoginPage.loadingButton).toHaveAttribute('aria-busy', 'true');
	await expect(shadcnTasklistLoginPage.loadingButton).toHaveAttribute('aria-disabled', 'true');
});

test.describe('redirect validation', () => {
	for (const redirect of [
		'/operate',
		'/shadcnning',
		'//evil.example',
		'https://evil.example',
		'/shadcn/tasklist/login',
		'/shadcn/tasklist/login?redirect=/shadcn/tasklist',
	]) {
		test(`should reject ${redirect} as aredirect`, async ({shadcnTasklistLoginPage}) => {
			await shadcnTasklistLoginPage.goto(redirect);

			await expect(shadcnTasklistLoginPage.genericErrorHeading).toBeVisible();
		});
	}
});
