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
import {createQueryUserTasksResponse, createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';

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
	);
});

test('should match the task details page snapshot', async ({network, taskDetailPage, page}) => {
	network.use(
		mockGetUserTaskEndpoint({
			successResponse: HttpResponse.json(
				createUserTask({
					state: 'CREATED',
					name: 'Review purchase order',
					processName: 'Procurement process',
					assignee: null,
					candidateUsers: ['alice', 'bob'],
					candidateGroups: ['managers'],
					priority: 60,
					dueDate: '2024-06-15T17:00:00.000Z',
					creationDate: '2024-01-10T09:30:00.000Z',
				}),
			),
		}),
	);

	await taskDetailPage.seedHideNotificationBanner();
	await taskDetailPage.goto('2251799813685281');
	await expect(taskDetailPage.detailsInfo).toBeVisible();
	await expect(taskDetailPage.taskName('Review purchase order')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the completed task details snapshot', async ({network, taskDetailPage, page}) => {
	network.use(
		mockGetUserTaskEndpoint({
			successResponse: HttpResponse.json(
				createUserTask({
					state: 'COMPLETED',
					name: 'Approve expense report',
					processName: 'Finance process',
					assignee: 'demo',
					completionDate: '2024-02-20T16:45:00.000Z',
					creationDate: '2024-02-18T10:00:00.000Z',
					priority: 50,
				}),
			),
		}),
	);

	await taskDetailPage.seedHideNotificationBanner();
	await taskDetailPage.goto('2251799813685281');
	await expect(taskDetailPage.completionLabel).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the task details snapshot with an active transition', async ({network, taskDetailPage, page}) => {
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

	await taskDetailPage.seedHideNotificationBanner();
	await taskDetailPage.goto('2251799813685281');
	await expect(taskDetailPage.detailsInfo).toBeVisible();
	await expect(taskDetailPage.taskName('Sign onboarding documents')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the notification permission banner snapshot', async ({network, taskDetailPage, page}) => {
	network.use(
		mockGetUserTaskEndpoint({
			successResponse: HttpResponse.json(
				createUserTask({
					state: 'CREATED',
					name: 'Review contract',
					processName: 'Legal process',
					assignee: null,
					creationDate: '2024-04-01T11:00:00.000Z',
				}),
			),
		}),
	);

	await taskDetailPage.seedShowNotificationBanner();
	await taskDetailPage.goto('2251799813685281');
	await expect(taskDetailPage.notificationBannerAction).toBeVisible();

	await expect(page).toHaveScreenshot();
});
