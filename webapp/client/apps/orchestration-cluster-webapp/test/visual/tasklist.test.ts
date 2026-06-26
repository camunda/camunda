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
	mockGetUserTaskEndpoint,
	mockLicenseEndpoint,
	mockQueryUserTasksEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createQueryUserTasksResponse} from '#/shared-test-modules/api-mocks/user-tasks';

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(createCurrentUser()),
		}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['tasklist']}})),
		}),
		mockLicenseEndpoint({
			successResponse: HttpResponse.json(createLicense()),
		}),
		mockQueryUserTasksEndpoint({
			successResponse: HttpResponse.json(createQueryUserTasksResponse()),
		}),
	);
});

test('should match the tasklist index page snapshot', async ({tasklistIndexPage, page}) => {
	await tasklistIndexPage.goto();
	await expect(tasklistIndexPage.tasksPanelHeading('All open tasks')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the tasklist processes page snapshot', async ({tasklistProcessesPage, page}) => {
	await tasklistProcessesPage.goto();
	await expect(tasklistProcessesPage.heading).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the tasklist 404 page snapshot', async ({notFoundPage, page, network}) => {
	network.use(
		mockGetUserTaskEndpoint({
			successResponse: HttpResponse.json({}, {status: 404}),
		}),
	);
	await page.goto('/tasklist/nonexistent/page');
	await expect(notFoundPage.heading).toBeVisible();

	await expect(page).toHaveScreenshot();
});
