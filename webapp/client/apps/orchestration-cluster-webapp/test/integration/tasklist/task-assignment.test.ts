/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import {HttpResponse} from 'msw';
import {z} from 'zod';
import {assignTaskRequestBodySchema} from '@camunda/camunda-api-zod-schemas/8.10';
import {
	mockCurrentUserEndpoint,
	mockGetUserTaskEndpoint,
	mockLicenseEndpoint,
	mockQueryUserTasksEndpoint,
	mockSystemConfigurationEndpoint,
	mockAssignTaskEndpoint,
	mockUnassignTaskEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createQueryUserTasksResponse, createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';

const currentUser = createCurrentUser({username: 'demo'});
const unassignedTask = createUserTask({
	name: 'Review invoice',
	processName: 'Invoice process',
	assignee: null,
	state: 'CREATED',
});
const assignedTask = createUserTask({
	name: 'Review invoice',
	processName: 'Invoice process',
	assignee: 'demo',
	state: 'CREATED',
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
	);
});

test.describe('Task assignment', () => {
	test('should assign an unassigned task to the current user', async ({network, taskDetailPage}) => {
		network.use(
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(unassignedTask),
			}),
			mockAssignTaskEndpoint({
				schema: assignTaskRequestBodySchema.extend({
					assignee: z.literal(currentUser.username),
					allowOverride: z.literal(false),
				}),
				failureResponse: HttpResponse.json({error: 'bad request'}, {status: 400}),
				successResponse: new HttpResponse(null, {status: 200}),
			}),
		);

		await taskDetailPage.goto('2251799813685281');
		await expect(taskDetailPage.assignButton).toBeVisible();

		network.use(
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(assignedTask),
			}),
		);

		await taskDetailPage.assignButton.click();

		await expect(taskDetailPage.assignmentSuccessful).toBeVisible();
		await expect(taskDetailPage.unassignButton).toBeVisible();
	});

	test('should unassign a task from the current user', async ({network, taskDetailPage}) => {
		network.use(
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(assignedTask),
			}),
			mockUnassignTaskEndpoint({
				successResponse: new HttpResponse(null, {status: 200}),
			}),
		);

		await taskDetailPage.goto('2251799813685281');
		await expect(taskDetailPage.unassignButton).toBeVisible();

		network.use(
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(unassignedTask),
			}),
		);

		await taskDetailPage.unassignButton.click();

		await expect(taskDetailPage.unassignmentSuccessful).toBeVisible();
		await expect(taskDetailPage.assignButton).toBeVisible();
	});

	test('should show error notification when assignment fails with 403', async ({network, taskDetailPage}) => {
		network.use(
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(unassignedTask),
			}),
			mockAssignTaskEndpoint({
				successResponse: HttpResponse.json(
					{
						type: 'about:blank',
						title: 'FORBIDDEN',
						status: 403,
						detail: "Unauthorized to perform operation 'UPDATE' on resource 'USER_TASK'",
						instance: '/v2/user-tasks/2251799813685281/assignment',
					},
					{status: 403},
				),
			}),
		);

		await taskDetailPage.goto('2251799813685281');
		await taskDetailPage.assignButton.click();

		await expect(
			taskDetailPage.header.notifications.getByNotificationTitle('Task could not be assigned'),
		).toBeVisible();
		await expect(taskDetailPage.assignButton).toBeVisible();
	});

	// Temporarily skipped due to incident INC-6420: https://app.incident.io/camunda/incidents/6420
	test.skip('should handle tasks with task listeners', async ({network, taskDetailPage}) => {
		network.use(
			mockQueryUserTasksEndpoint({
				successResponse: HttpResponse.json(createQueryUserTasksResponse()),
			}),
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(unassignedTask),
			}),
			mockAssignTaskEndpoint({
				successResponse: HttpResponse.json(
					{
						type: 'about:blank',
						title: 'DEADLINE_EXCEEDED',
						status: 504,
						detail: 'Request timed out',
						instance: '/v2/user-tasks/2251799813685281/assignment',
					},
					{status: 504},
				),
			}),
		);

		await taskDetailPage.goto('2251799813685281');
		await taskDetailPage.assignButton.click();

		await expect(taskDetailPage.header.notifications.getByNotificationTitle('Task assignment delayed')).toBeVisible();

		network.use(
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(assignedTask),
			}),
		);

		await expect(taskDetailPage.assignButton).toBeVisible();
	});

	test('should not show assign button for completed task', async ({network, taskDetailPage}) => {
		const completedTask = createUserTask({
			name: 'Review invoice',
			processName: 'Invoice process',
			assignee: 'demo',
			state: 'COMPLETED',
			completionDate: '2024-01-02T10:00:00.000Z',
		});

		network.use(
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(completedTask),
			}),
		);

		await taskDetailPage.goto('2251799813685281');

		await expect(taskDetailPage.assignButton).not.toBeVisible();
		await expect(taskDetailPage.unassignButton).not.toBeVisible();
	});
});
