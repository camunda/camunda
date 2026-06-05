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
import {mockSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {mockLicense} from '#/shared-test-modules/api-mocks/license';
import {mockCurrentUser} from '#/shared-test-modules/api-mocks/current-user';

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(mockCurrentUser),
		}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json({
				...mockSystemConfiguration,
				components: {active: ['tasklist']},
			}),
		}),
		mockLicenseEndpoint({
			successResponse: HttpResponse.json(mockLicense),
		}),
	);
});

test.describe('Tasklist processes page', () => {
	test('should render Tasklist Processes page', async ({tasklistProcessesPage}) => {
		await tasklistProcessesPage.goto();

		await expect(tasklistProcessesPage.heading).toBeVisible();
	});

	test('should show Tasks and Processes nav items', async ({tasklistProcessesPage}) => {
		await tasklistProcessesPage.goto();

		await expect(tasklistProcessesPage.tasksNavItem).toBeVisible();
		await expect(tasklistProcessesPage.processesNavItem).toBeVisible();
	});

	test('should navigate from Processes to Tasks', async ({tasklistProcessesPage, page}) => {
		await tasklistProcessesPage.goto();

		await tasklistProcessesPage.tasksNavItem.click();

		await expect(page).toHaveURL('/tasklist');
	});
});
