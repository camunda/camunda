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

const currentUser = createCurrentUser();

function createTasksPageRequestSchema(from: number) {
	return z.object({
		filter: z.object({
			state: z.object({
				$in: z.tuple([
					z.literal('CREATED'),
					z.literal('ASSIGNING'),
					z.literal('UPDATING'),
					z.literal('COMPLETING'),
					z.literal('CANCELING'),
				]),
			}),
		}),
		sort: z.tuple([
			z.object({
				field: z.literal('creationDate'),
				order: z.literal('desc'),
			}),
		]),
		page: z.object({
			limit: z.literal(50),
			from: z.literal(from),
		}),
	});
}

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

test.describe('Tasklist index page', () => {
	test('should render Tasklist index page with navigation', async ({tasklistIndexPage}) => {
		await tasklistIndexPage.goto();

		await expect(tasklistIndexPage.tasksPanelHeading('All open tasks')).toBeVisible();
		await expect(tasklistIndexPage.header.branding).toBeVisible();
		await expect(tasklistIndexPage.tasksNavItem).toBeVisible();
		await expect(tasklistIndexPage.processesNavItem).toBeVisible();
	});

	test('should navigate from Tasks to Processes', async ({tasklistIndexPage, page}) => {
		await tasklistIndexPage.goto();

		await tasklistIndexPage.processesNavItem.click();

		await expect(page).toHaveURL('/tasklist/processes');
	});
});

test.describe('Tasks panel', () => {
	test('should render tasks', async ({network, tasklistIndexPage}) => {
		const firstPageTasks = [
			createUserTask({userTaskKey: '1', name: 'Approve purchase order'}),
			createUserTask({userTaskKey: '2', name: 'Review contract'}),
		];

		network.use(
			mockQueryUserTasksEndpoint({
				schema: createTasksPageRequestSchema(0),
				successResponse: HttpResponse.json(createQueryUserTasksResponse({items: firstPageTasks})),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		await tasklistIndexPage.goto();

		await expect(tasklistIndexPage.taskItem('Approve purchase order')).toBeVisible();
		await expect(tasklistIndexPage.taskItem('Review contract')).toBeVisible();
	});

	test('should show the empty state', async ({network, tasklistIndexPage}) => {
		network.use(
			mockQueryUserTasksEndpoint({
				successResponse: HttpResponse.json(createQueryUserTasksResponse({items: []})),
			}),
		);

		await tasklistIndexPage.goto();

		await expect(tasklistIndexPage.noTasksMessage).toBeVisible();
	});

	test('should navigate to the task details', async ({network, page, tasklistIndexPage}) => {
		network.use(
			mockQueryUserTasksEndpoint({
				successResponse: HttpResponse.json(
					createQueryUserTasksResponse({
						items: [createUserTask({userTaskKey: '2251799813685281', name: 'Sign document'})],
					}),
				),
			}),
		);

		await tasklistIndexPage.goto();
		await tasklistIndexPage.taskItem('Sign document').click();

		await expect(page).toHaveURL('/tasklist/2251799813685281');
	});

	test('should paginate when scrolling to the bottom', async ({network, tasklistIndexPage}) => {
		const firstPageTasks = Array.from({length: 50}, (_, i) =>
			createUserTask({userTaskKey: String(i + 1), name: `Task ${i + 1}`}),
		);
		const secondPageTasks = [createUserTask({userTaskKey: '51', name: 'Task from second page'})];

		network.use(
			mockQueryUserTasksEndpoint({
				schema: createTasksPageRequestSchema(0),
				successResponse: HttpResponse.json(
					createQueryUserTasksResponse({items: firstPageTasks, page: {totalItems: 51}}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		await tasklistIndexPage.goto();

		await expect(tasklistIndexPage.taskItem('Task 50')).toBeVisible();

		network.use(
			mockQueryUserTasksEndpoint({
				schema: createTasksPageRequestSchema(50),
				successResponse: HttpResponse.json(
					createQueryUserTasksResponse({
						items: secondPageTasks,
						page: {totalItems: 51},
					}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		await tasklistIndexPage.taskItem('Task 50').scrollIntoViewIfNeeded();

		await expect(tasklistIndexPage.taskItem('Task from second page')).toBeVisible();
	});
});
