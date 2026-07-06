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
	mockQueryUserTasksEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createQueryUserTasksResponse, createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';
import {createProblemDetails} from '#/shared-test-modules/api-mocks/shared';

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
	);
});

test.describe('Task completion', () => {
	test('should complete an assigned task', async ({network, taskDetailPage, page}) => {
		network.use(
			mockCompleteTaskEndpoint({
				successResponse: new HttpResponse(null, {status: 200}),
			}),
		);

		await taskDetailPage.goto('2251799813685281');
		await taskDetailPage.completeTaskButton.click();

		network.use(
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(completedTask),
			}),
		);

		await expect(taskDetailPage.header.notifications.getByNotificationTitle('Task completed')).toBeVisible();
		await expect(page).toHaveURL(/\/tasklist$/);
	});

	test('should preserve search params after completion', async ({network, taskDetailPage, page}) => {
		network.use(
			mockCompleteTaskEndpoint({
				successResponse: new HttpResponse(null, {status: 200}),
			}),
		);

		await taskDetailPage.goto('2251799813685281', '?filter=assigned-to-me&sortBy=priority');
		await taskDetailPage.completeTaskButton.click();

		network.use(
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(completedTask),
			}),
		);

		await expect(taskDetailPage.header.notifications.getByNotificationTitle('Task completed')).toBeVisible();
		await expect(page).toHaveURL(/\/tasklist\?/);
		expect(new URL(page.url()).searchParams.get('filter')).toBe('assigned-to-me');
		expect(new URL(page.url()).searchParams.get('sortBy')).toBe('priority');
	});

	test('should navigate to the next open task when auto-select is enabled', async ({network, taskDetailPage, page}) => {
		const assigningTask = createUserTask({
			userTaskKey: '2251799813685283',
			name: 'Assigning purchase request after auto-select',
			processName: 'Purchase process',
			assignee: currentUser.username,
			state: 'ASSIGNING',
		});
		const nextTask = createUserTask({
			userTaskKey: '2251799813685282',
			name: 'Review purchase request after auto-select',
			processName: 'Purchase process',
			assignee: currentUser.username,
			state: 'CREATED',
		});

		network.use(
			mockCompleteTaskEndpoint({
				successResponse: new HttpResponse(null, {status: 200}),
			}),
		);

		await taskDetailPage.goto('2251799813685281', '?filter=assigned-to-me&sortBy=priority');
		await expect(taskDetailPage.detailsInfo.getByText('Review invoice before auto-select')).toBeVisible();
		await taskDetailPage.autoSelectNextTaskSwitch.click({force: true});

		network.use(
			mockQueryUserTasksEndpoint({
				successResponse: HttpResponse.json(
					createQueryUserTasksResponse({
						items: [completedTask, assigningTask, nextTask],
					}),
				),
			}),
		);

		await taskDetailPage.completeTaskButton.click();

		network.use(
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(completedTask),
			}),
		);

		await expect(taskDetailPage.header.notifications.getByNotificationTitle('Task completed')).toBeVisible();

		network.use(
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(nextTask),
			}),
		);

		await expect(page).toHaveURL(/\/tasklist\/2251799813685282\?/);
		expect(new URL(page.url()).searchParams.get('filter')).toBe('assigned-to-me');
		expect(new URL(page.url()).searchParams.get('sortBy')).toBe('priority');
		await expect(taskDetailPage.detailsInfo.getByText('Review purchase request after auto-select')).toBeVisible();
	});

	test('should show a failed state when completion is forbidden', async ({network, taskDetailPage, page}) => {
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

		await taskDetailPage.goto('2251799813685281');
		await taskDetailPage.completeTaskButton.click();

		await expect(taskDetailPage.completionFailed).toBeVisible();
		await expect(
			taskDetailPage.header.notifications.getByNotificationTitle('Task could not be completed'),
		).toBeVisible();
		await expect(taskDetailPage.completeTaskButton).toBeVisible();
		await expect(page).toHaveURL(/\/tasklist\/2251799813685281$/);
	});

	test('should handle completion listeners', async ({network, taskDetailPage, page}) => {
		network.use(
			mockCompleteTaskEndpoint({
				successResponse: HttpResponse.json(
					createProblemDetails({title: 'DEADLINE_EXCEEDED', status: 504, detail: 'Request timed out'}),
					{status: 504},
				),
			}),
		);

		await taskDetailPage.goto('2251799813685281');
		await taskDetailPage.completeTaskButton.click();

		network.use(
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(completedTask),
			}),
		);

		await expect(taskDetailPage.header.notifications.getByNotificationTitle('Task completion delayed')).toBeVisible();
		await expect(taskDetailPage.header.notifications.getByNotificationTitle('Task completed')).toBeVisible();
		await expect(page).toHaveURL(/\/tasklist$/);
	});
});
