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
	tasklistIndexPage,
	tasklistLoginPage,
}) => {
	await tasklistLoginPage.gotoTasklist();

	await expect(page).toHaveURL('/tasklist/login');
	await expect(tasklistLoginPage.title).toBeVisible();
	await expect(tasklistLoginPage.usernameInput).toBeVisible();

	network.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}));

	await tasklistLoginPage.fillCredentials('demo', 'demo');
	await tasklistLoginPage.submitButton.click();

	await expect(page).toHaveURL('/tasklist');
	await expect(tasklistIndexPage.noTasksMessage).toBeVisible();
});

test('should preserve a Tasklist URL through login', async ({
	network,
	page,
	tasklistIndexPage,
	tasklistLoginPage,
}) => {
	await tasklistLoginPage.gotoTasklist('?filter=assigned');

	await expect(page).toHaveURL((url) => {
		return url.pathname === '/tasklist/login' && url.searchParams.get('redirect') === '/tasklist?filter=assigned';
	});
	await expect(tasklistLoginPage.usernameInput).toBeVisible();

	network.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}));

	await tasklistLoginPage.fillCredentials('demo', 'demo');
	await tasklistLoginPage.submitButton.click();

	await expect(page).toHaveURL('/tasklist?filter=assigned');
	await expect(tasklistIndexPage.noTasksMessage).toBeVisible();
});

test('should show an error for wrong credentials', async ({network, tasklistLoginPage}) => {
	network.use(mockLoginEndpoint({successResponse: new HttpResponse(null, {status: 401})}));

	await tasklistLoginPage.goto();
	await tasklistLoginPage.fillCredentials('demo', 'wrong-password');
	await tasklistLoginPage.submitButton.click();

	await expect(tasklistLoginPage.errorMessage).toContainText(/username and password do not match/i);
});

test('should show a generic error message', async ({network, tasklistLoginPage}) => {
	network.use(mockLoginEndpoint({successResponse: new HttpResponse(null, {status: 500})}));

	await tasklistLoginPage.goto();
	await tasklistLoginPage.fillCredentials('demo', 'demo');
	await tasklistLoginPage.submitButton.click();

	await expect(tasklistLoginPage.errorMessage).toContainText(/credentials could not be verified/i);
});

test('should show a loading state while the login form is submitting', async ({network, tasklistLoginPage}) => {
	network.use(
		mockLoginEndpoint({
			successResponse: new HttpResponse(null, {status: 200}),
			delay: 500,
		}),
	);

	await tasklistLoginPage.goto();
	await tasklistLoginPage.fillCredentials('demo', 'demo');
	await tasklistLoginPage.submitButton.click();

	await expect(tasklistLoginPage.loadingButton).toBeVisible();
	await expect(tasklistLoginPage.loadingButton).toHaveAttribute('aria-busy', 'true');
	await expect(tasklistLoginPage.loadingButton).toHaveAttribute('aria-disabled', 'true');
});

test.describe('redirect validation', () => {
	for (const redirect of [
		'/operate',
		'/tasklisting',
		'//evil.example',
		'https://evil.example',
		'/tasklist/login',
		'/tasklist/login?redirect=/tasklist',
	]) {
		test(`should reject ${redirect} as aredirect`, async ({tasklistLoginPage}) => {
			await tasklistLoginPage.goto(redirect);

			await expect(tasklistLoginPage.genericErrorHeading).toBeVisible();
		});
	}
});
