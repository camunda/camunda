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
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createQueryUserTasksResponse, createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';

const USER_TASK_KEY = '2251799813685281';
const task = createUserTask({
	userTaskKey: USER_TASK_KEY,
	name: 'Review purchase order',
	processName: 'Procurement process',
	assignee: 'demo',
	candidateUsers: ['alice', 'bob'],
	candidateGroups: ['managers'],
	priority: 60,
	businessId: 'ORDER-2024-0042',
	dueDate: '2024-06-15T17:00:00.000Z',
	creationDate: '2024-01-10T09:30:00.000Z',
});

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['tasklist']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockQueryUserTasksEndpoint({
			successResponse: HttpResponse.json(createQueryUserTasksResponse({items: [task]})),
		}),
		mockGetUserTaskEndpoint({successResponse: HttpResponse.json(task)}),
	);
});

test('should match the shadcn task details page snapshot', async ({shadcnTaskDetailPage, page}) => {
	await shadcnTaskDetailPage.goto(USER_TASK_KEY);
	await expect(shadcnTaskDetailPage.taskName('Review purchase order')).toBeVisible();
	await expect(shadcnTaskDetailPage.aside.getByText('ORDER-2024-0042', {exact: true})).toBeVisible();
	await expect(shadcnTaskDetailPage.taskTab).toHaveAttribute('aria-selected', 'true');
	await expect(shadcnTaskDetailPage.selectedTask('Review purchase order')).toHaveAttribute('aria-current', 'page');

	await expect(page).toHaveScreenshot();
});

test('should match the shadcn task details process route snapshot', async ({shadcnTaskDetailPage, page}) => {
	await shadcnTaskDetailPage.gotoProcess(USER_TASK_KEY);
	await expect(shadcnTaskDetailPage.taskName('Review purchase order')).toBeVisible();
	await expect(shadcnTaskDetailPage.aside.getByText('ORDER-2024-0042', {exact: true})).toBeVisible();
	await expect(shadcnTaskDetailPage.processTab).toHaveAttribute('aria-selected', 'true');
	await expect(shadcnTaskDetailPage.selectedTask('Review purchase order')).toHaveAttribute('aria-current', 'page');

	await expect(page).toHaveScreenshot();
});
