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
	mockCompleteTaskEndpoint,
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
import {createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {createQueryVariablesByUserTaskResponse} from '#/shared-test-modules/api-mocks/variables';

const currentUser = createCurrentUser({username: 'demo'});
const assignedTask = createUserTask({
	name: 'Review invoice before auto-select',
	processName: 'Invoice process',
	assignee: currentUser.username,
	state: 'CREATED',
});
const completedTask = createUserTask({
	name: 'Review invoice before auto-select',
	processName: 'Invoice process',
	assignee: currentUser.username,
	state: 'COMPLETED',
	completionDate: '2024-01-02T10:00:00.000Z',
});

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(currentUser),
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
		mockGetUserTaskEndpoint({
			successResponse: HttpResponse.json(assignedTask),
		}),
		mockQueryVariablesByUserTaskEndpoint({
			successResponse: HttpResponse.json(createQueryVariablesByUserTaskResponse()),
		}),
	);
});

test.describe('Task completion', () => {
	test('should complete an assigned task', async ({network, shadcnTaskDetailPage, page}) => {
		network.use(
			mockCompleteTaskEndpoint({
				successResponse: new HttpResponse(null, {status: 200}),
			}),
		);

		await shadcnTaskDetailPage.goto('2251799813685281');
		await shadcnTaskDetailPage.completeTaskButton.click();

		network.use(
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(completedTask),
			}),
		);

		await expect(shadcnTaskDetailPage.header.notifications.getByNotificationTitle('Task completed')).toBeVisible();
		await expect(page).toHaveURL(/\/shadcn\/tasklist$/);
	});

	test('should show a failed state when completion is forbidden', async ({network, shadcnTaskDetailPage, page}) => {
		network.use(
			mockCompleteTaskEndpoint({
				successResponse: HttpResponse.json(
					createProblemDetails({
						title: 'FORBIDDEN',
						status: 403,
						detail: "Unauthorized to perform operation 'UPDATE' on resource 'USER_TASK'",
						instance: '/v2/user-tasks/2251799813685281/completion',
					}),
					{status: 403},
				),
			}),
		);

		await shadcnTaskDetailPage.goto('2251799813685281');
		await shadcnTaskDetailPage.completeTaskButton.click();

		await expect(shadcnTaskDetailPage.completionFailed).toBeVisible();
		await expect(
			shadcnTaskDetailPage.header.notifications.getByNotificationTitle('Task could not be completed'),
		).toBeVisible();
		await expect(shadcnTaskDetailPage.completeTaskButton).toBeVisible();
		await expect(page).toHaveURL(/\/shadcn\/tasklist\/2251799813685281$/);
	});

	test('should handle completion listeners', async ({network, shadcnTaskDetailPage, page}) => {
		network.use(
			mockCompleteTaskEndpoint({
				successResponse: HttpResponse.json(
					createProblemDetails({title: 'DEADLINE_EXCEEDED', status: 504, detail: 'Request timed out'}),
					{status: 504},
				),
			}),
		);

		await shadcnTaskDetailPage.goto('2251799813685281');
		await shadcnTaskDetailPage.completeTaskButton.click();

		network.use(
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(completedTask),
			}),
		);

		await expect(
			shadcnTaskDetailPage.header.notifications.getByNotificationTitle('Task completion delayed'),
		).toBeVisible();
		await expect(shadcnTaskDetailPage.header.notifications.getByNotificationTitle('Task completed')).toBeVisible();
		await expect(page).toHaveURL(/\/shadcn\/tasklist$/);
	});
});
