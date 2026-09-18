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
	mockGetUserTaskEndpoint,
	mockLicenseEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockQueryUserTasksEndpoint,
	mockQueryVariablesByUserTaskEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createQueryUserTasksResponse, createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';
import {createQueryVariablesByUserTaskResponse} from '#/shared-test-modules/api-mocks/variables';

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
import {createQueryProcessDefinitionsResponse} from '#/shared-test-modules/api-mocks/process-definitions';

test.beforeEach(async ({network, page}) => {
	await page.addInitScript(() => {
		localStorage.setItem('tasklist.hasConsentedToStartProcess', JSON.stringify(true));
	});
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
			successResponse: HttpResponse.json(createQueryUserTasksResponse({items: []})),
		}),
		mockQueryProcessDefinitionsEndpoint({
			successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
		}),
		mockQueryVariablesByUserTaskEndpoint({
			successResponse: HttpResponse.json(createQueryVariablesByUserTaskResponse()),
		}),
	);
});

test.describe('Tasklist index page', () => {
	test('should render Tasklist index page with navigation', async ({tasklistIndexPage}) => {
		await tasklistIndexPage.goto();

		await expect(tasklistIndexPage.filterSelect).toHaveText('All open tasks');
		await expect(tasklistIndexPage.header.branding).toBeVisible();
		await expect(tasklistIndexPage.header.tasksNavItem).toBeVisible();
		await expect(tasklistIndexPage.header.processesNavItem).toBeVisible();
	});

	test('should navigate from Tasks to Processes', async ({tasklistIndexPage, page}) => {
		await tasklistIndexPage.goto();

		await tasklistIndexPage.header.processesNavItem.click();

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

	test('should not auto-select a task on direct navigation when auto-select is enabled', async ({
		network,
		page,
		tasklistIndexPage,
	}) => {
		await page.addInitScript(`localStorage.setItem('tasklist.autoSelectNextTask', JSON.stringify(true))`);
		network.use(
			mockQueryUserTasksEndpoint({
				successResponse: HttpResponse.json(
					createQueryUserTasksResponse({
						items: [createUserTask({userTaskKey: '2251799813685281', name: 'Approve purchase order'})],
					}),
				),
			}),
		);

		await tasklistIndexPage.goto();

		await expect(page).toHaveURL('/tasklist');
		await expect(tasklistIndexPage.taskItem('Approve purchase order')).toBeVisible();
	});

	test('should show the empty state', async ({tasklistIndexPage}) => {
		await tasklistIndexPage.goto();

		await expect(tasklistIndexPage.noTasksMessage).toBeVisible();
	});

	test('should navigate to the task details', async ({network, page, tasklistIndexPage}) => {
		const task = createUserTask({userTaskKey: '2251799813685281', name: 'Sign document'});

		network.use(
			mockQueryUserTasksEndpoint({
				successResponse: HttpResponse.json(
					createQueryUserTasksResponse({
						items: [task],
					}),
				),
			}),
			mockGetUserTaskEndpoint({successResponse: HttpResponse.json(task)}),
		);

		await tasklistIndexPage.goto();
		await tasklistIndexPage.taskItem('Sign document').click();

		await expect(page).toHaveURL('/tasklist/2251799813685281');
	});
});

test.describe('Tasklist processes page', () => {
	test('should render Tasklist Processes page with navigation', async ({tasklistProcessesPage}) => {
		await tasklistProcessesPage.goto();

		await expect(tasklistProcessesPage.heading).toBeVisible();
		await expect(tasklistProcessesPage.header.tasksNavItem).toBeVisible();
		await expect(tasklistProcessesPage.header.processesNavItem).toBeVisible();
	});

	test('should navigate from Processes to Tasks', async ({tasklistProcessesPage, page}) => {
		await tasklistProcessesPage.goto();

		await tasklistProcessesPage.header.tasksNavItem.click();

		await expect(page).toHaveURL('/tasklist');
	});
});
