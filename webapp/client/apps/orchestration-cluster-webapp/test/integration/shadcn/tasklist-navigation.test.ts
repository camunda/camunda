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
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createQueryUserTasksResponse, createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';

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
	);
});

test.describe('Tasklist index page', () => {
	test('should render Tasklist index page with navigation', async ({shadcnTasklistIndexPage}) => {
		await shadcnTasklistIndexPage.goto();

		await expect(shadcnTasklistIndexPage.filterSelect).toHaveText('All open tasks');
		await expect(shadcnTasklistIndexPage.header.branding).toBeVisible();
		await expect(shadcnTasklistIndexPage.header.tasksNavItem).toBeVisible();
		await expect(shadcnTasklistIndexPage.header.processesNavItem).toBeVisible();
	});

	test('should navigate from Tasks to Processes', async ({shadcnTasklistIndexPage, page}) => {
		await shadcnTasklistIndexPage.goto();

		await shadcnTasklistIndexPage.header.processesNavItem.click();

		await expect(page).toHaveURL('/shadcn/tasklist/processes');
	});
});

test.describe('Tasks panel', () => {
	test('should render tasks', async ({network, shadcnTasklistIndexPage}) => {
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

		await shadcnTasklistIndexPage.goto();

		await expect(shadcnTasklistIndexPage.taskItem('Approve purchase order')).toBeVisible();
		await expect(shadcnTasklistIndexPage.taskItem('Review contract')).toBeVisible();
	});

	test('should show the empty state', async ({shadcnTasklistIndexPage}) => {
		await shadcnTasklistIndexPage.goto();

		await expect(shadcnTasklistIndexPage.noTasksMessage).toBeVisible();
	});

	test('should navigate to the task details', async ({network, page, shadcnTasklistIndexPage}) => {
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

		await shadcnTasklistIndexPage.goto();
		await shadcnTasklistIndexPage.taskItem('Sign document').click();

		await expect(page).toHaveURL('/shadcn/tasklist/2251799813685281');
	});
});

test.describe('Tasklist processes page', () => {
	test('should render Tasklist Processes page with navigation', async ({shadcnTasklistProcessesPage}) => {
		await shadcnTasklistProcessesPage.goto();

		await expect(shadcnTasklistProcessesPage.heading).toBeVisible();
		await expect(shadcnTasklistProcessesPage.header.tasksNavItem).toBeVisible();
		await expect(shadcnTasklistProcessesPage.header.processesNavItem).toBeVisible();
	});

	test('should navigate from Processes to Tasks', async ({shadcnTasklistProcessesPage, page}) => {
		await shadcnTasklistProcessesPage.goto();

		await shadcnTasklistProcessesPage.header.tasksNavItem.click();

		await expect(page).toHaveURL('/shadcn/tasklist');
	});
});
