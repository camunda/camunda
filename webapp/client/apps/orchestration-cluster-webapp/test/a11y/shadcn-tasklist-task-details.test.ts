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

test('should have no accessibility violations on the shadcn task details page', async ({
	shadcnTaskDetailPage,
	makeAxeBuilder,
}) => {
	await shadcnTaskDetailPage.goto(USER_TASK_KEY);
	await expect(shadcnTaskDetailPage.taskTab).toHaveAttribute('aria-selected', 'true');
	await expect(shadcnTaskDetailPage.aside).toBeVisible();
	await expect(shadcnTaskDetailPage.selectedTask('Review purchase order')).toHaveAttribute('aria-current', 'page');

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations on the shadcn Process route', async ({
	shadcnTaskDetailPage,
	makeAxeBuilder,
}) => {
	await shadcnTaskDetailPage.gotoProcess(USER_TASK_KEY);
	await expect(shadcnTaskDetailPage.processTab).toHaveAttribute('aria-selected', 'true');
	await expect(shadcnTaskDetailPage.aside).toBeVisible();
	await expect(shadcnTaskDetailPage.selectedTask('Review purchase order')).toHaveAttribute('aria-current', 'page');

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});
