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

const TASKS = [
	createUserTask({
		userTaskKey: '1',
		name: 'Review purchase contract',
		processName: 'Procurement',
		assignee: 'demo',
		priority: 50,
		creationDate: '2024-01-15T09:00:00.000Z',
	}),
	createUserTask({
		userTaskKey: '2',
		name: 'Approve expense report',
		processName: 'Finance',
		assignee: null,
		priority: 75,
		creationDate: '2024-02-20T14:30:00.000Z',
	}),
	createUserTask({
		userTaskKey: '3',
		name: 'Sign onboarding documents',
		processName: 'HR',
		assignee: 'jdoe',
		priority: 25,
		creationDate: '2024-03-10T11:00:00.000Z',
	}),
];

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(createCurrentUser({username: 'demo'})),
		}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['tasklist']}})),
		}),
		mockLicenseEndpoint({
			successResponse: HttpResponse.json(createLicense()),
		}),
		mockQueryUserTasksEndpoint({
			successResponse: HttpResponse.json(createQueryUserTasksResponse({items: TASKS})),
		}),
	);
});

test('should match snapshot with the filter panel collapsed', async ({tasklistIndexPage, page}) => {
	await tasklistIndexPage.goto();

	await expect(tasklistIndexPage.taskItem('Review purchase contract')).toBeVisible();
	await expect(tasklistIndexPage.expandFiltersButton).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match snapshot with the filter panel expanded', async ({tasklistIndexPage, page}) => {
	await tasklistIndexPage.goto();

	await expect(tasklistIndexPage.taskItem('Review purchase contract')).toBeVisible();
	await tasklistIndexPage.expandFilters();
	await expect(tasklistIndexPage.filterLink('All open tasks')).toBeVisible();

	await expect(page).toHaveScreenshot();
});
