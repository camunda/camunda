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
	mockQueryUserTasksEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createQueryUserTasksResponse, createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';

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

test('should match the tasklist index page snapshot', async ({shadcnTasklistIndexPage, page}) => {
	await shadcnTasklistIndexPage.goto();
	await expect(shadcnTasklistIndexPage.filterSelect).toHaveText('All open tasks');

	await expect(page).toHaveScreenshot();
});

test('should match the tasklist index page snapshot with available tasks', async ({
	network,
	shadcnTasklistIndexPage,
	page,
}) => {
	network.use(
		mockQueryUserTasksEndpoint({
			successResponse: HttpResponse.json(
				createQueryUserTasksResponse({
					items: [
						createUserTask({userTaskKey: '1', assignee: 'jane'}),
						createUserTask({userTaskKey: '2', assignee: 'demo', businessId: 'ORDER-2024-0042'}),
						createUserTask({userTaskKey: '3', businessId: 'ORDER-2024-0043'}),
					],
				}),
			),
		}),
	);

	await shadcnTasklistIndexPage.goto();
	await expect(page.getByText('ORDER-2024-0042')).toBeVisible();
	await expect(page.getByText('ORDER-2024-0043')).toBeVisible();

	await expect(page).toHaveScreenshot();
});
