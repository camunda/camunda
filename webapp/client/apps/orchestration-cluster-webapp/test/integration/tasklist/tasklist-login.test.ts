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
	mockQueryProcessDefinitionsEndpoint,
	mockQueryUserTasksEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createQueryProcessDefinitionsResponse} from '#/shared-test-modules/api-mocks/process-definitions';
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
	);
});

test('should redirect the Tasklist index to Tasklist login and return after login', async ({
	network,
	page,
	tasklistIndexPage,
	tasklistLoginPage,
}) => {
	network.use(
		mockQueryUserTasksEndpoint({successResponse: HttpResponse.json(createQueryUserTasksResponse({items: []}))}),
	);

	await tasklistIndexPage.goto();

	await expect(page).toHaveURL('/tasklist/login');
	await expect(tasklistLoginPage.title).toBeVisible();
	await expect(tasklistLoginPage.usernameInput).toBeVisible();

	network.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}));

	await tasklistLoginPage.fillCredentials('demo', 'demo');
	await tasklistLoginPage.submitButton.click();

	await expect(page).toHaveURL('/tasklist');
	await expect(tasklistIndexPage.tasksPanelHeading('All open tasks')).toBeVisible();
});

test('should preserve a nested Tasklist URL through login', async ({
	network,
	page,
	tasklistLoginPage,
	tasklistProcessesPage,
}) => {
	network.use(
		mockQueryProcessDefinitionsEndpoint({
			successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
		}),
	);

	await tasklistProcessesPage.goto('?search=invoice');

	await expect(page).toHaveURL((url) => {
		return (
			url.pathname === '/tasklist/login' && url.searchParams.get('redirect') === '/tasklist/processes?search=invoice'
		);
	});
	await expect(tasklistLoginPage.usernameInput).toBeVisible();

	network.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}));

	await tasklistLoginPage.fillCredentials('demo', 'demo');
	await tasklistLoginPage.submitButton.click();

	await expect(page).toHaveURL('/tasklist/processes?search=invoice');
	await expect(tasklistProcessesPage.heading).toBeVisible();
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
		test(`should reject ${redirect} as a Tasklist redirect`, async ({tasklistLoginPage}) => {
			await tasklistLoginPage.goto(redirect);

			await expect(tasklistLoginPage.genericErrorHeading).toBeVisible();
		});
	}
});
