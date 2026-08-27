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
	mockQueryVariablesByUserTaskEndpoint,
	mockQueryUserTasksEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createQueryUserTasksResponse, createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';
import {createQueryVariablesByUserTaskResponse, createVariable} from '#/shared-test-modules/api-mocks/variables';

const USER_TASK_KEY = '2251799813685281';

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
			successResponse: HttpResponse.json(createQueryUserTasksResponse()),
		}),
		mockQueryVariablesByUserTaskEndpoint({
			successResponse: HttpResponse.json(createQueryVariablesByUserTaskResponse()),
		}),
	);
});

test('should match the task details page snapshot', async ({network, shadcnTaskDetailPage, page}) => {
	network.use(
		mockGetUserTaskEndpoint({
			successResponse: HttpResponse.json(
				createUserTask({
					userTaskKey: USER_TASK_KEY,
					state: 'CREATED',
					name: 'Review purchase order',
					processName: 'Procurement process',
					assignee: 'demo',
					candidateUsers: ['alice', 'bob'],
					candidateGroups: ['managers'],
					priority: 60,
					businessId: 'ORDER-2024-0042',
					dueDate: '2024-06-15T17:00:00.000Z',
					creationDate: '2024-01-10T09:30:00.000Z',
				}),
			),
		}),
		mockQueryVariablesByUserTaskEndpoint({
			successResponse: HttpResponse.json(
				createQueryVariablesByUserTaskResponse({
					items: [
						createVariable({name: 'orderTotal', value: '249.99'}),
						createVariable({name: 'currency', value: '"EUR"', variableKey: '2251799813685284'}),
					],
				}),
			),
		}),
	);

	await shadcnTaskDetailPage.seedHideNotificationBanner();
	await shadcnTaskDetailPage.goto(USER_TASK_KEY);
	await expect(shadcnTaskDetailPage.detailsInfo).toBeVisible();
	await expect(shadcnTaskDetailPage.taskName('Review purchase order')).toBeVisible();
	await expect(shadcnTaskDetailPage.aside.getByText('ORDER-2024-0042')).toBeVisible();
	await expect(shadcnTaskDetailPage.completeTaskButton).toBeEnabled();

	await expect(page).toHaveScreenshot();
});

test('should match the unassigned task details snapshot', async ({network, shadcnTaskDetailPage, page}) => {
	network.use(
		mockGetUserTaskEndpoint({
			successResponse: HttpResponse.json(
				createUserTask({
					state: 'CREATED',
					name: 'Review supplier onboarding',
					processName: 'Procurement process',
					assignee: null,
					candidateUsers: ['alice', 'bob'],
					candidateGroups: ['managers'],
					creationDate: '2024-01-12T09:30:00.000Z',
				}),
			),
		}),
	);

	await shadcnTaskDetailPage.seedHideNotificationBanner();
	await shadcnTaskDetailPage.goto('2251799813685281');
	await expect(shadcnTaskDetailPage.detailsInfo).toBeVisible();
	await expect(shadcnTaskDetailPage.taskName('Review supplier onboarding')).toBeVisible();
	await expect(shadcnTaskDetailPage.completeTaskButton).toBeDisabled();

	await expect(page).toHaveScreenshot();
});

test('should match the task details snapshot with an active transition', async ({
	network,
	shadcnTaskDetailPage,
	page,
}) => {
	network.use(
		mockGetUserTaskEndpoint({
			successResponse: HttpResponse.json(
				createUserTask({
					state: 'UPDATING',
					name: 'Sign onboarding documents',
					processName: 'HR process',
					assignee: 'demo',
					creationDate: '2024-03-05T08:00:00.000Z',
					priority: 25,
				}),
			),
		}),
	);

	await shadcnTaskDetailPage.seedHideNotificationBanner();
	await shadcnTaskDetailPage.goto('2251799813685281');
	await expect(shadcnTaskDetailPage.detailsInfo).toBeVisible();
	await expect(shadcnTaskDetailPage.taskName('Sign onboarding documents')).toBeVisible();

	await expect(page).toHaveScreenshot();
});
