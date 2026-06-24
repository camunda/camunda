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

const currentUser = createCurrentUser({username: 'demo'});

const allOpenFilter = z.object({
	state: z.object({
		$in: z.tuple([
			z.literal('CREATED'),
			z.literal('ASSIGNING'),
			z.literal('UPDATING'),
			z.literal('COMPLETING'),
			z.literal('CANCELING'),
		]),
	}),
});

function createUserTasksRequestSchema(options: {
	filter: z.ZodTypeAny;
	sortField: 'creationDate' | 'dueDate' | 'followUpDate' | 'completionDate' | 'priority';
}) {
	return z.object({
		filter: options.filter,
		sort: z.tuple([z.object({field: z.literal(options.sortField), order: z.literal('desc')})]),
		page: z.object({limit: z.literal(50), from: z.literal(0)}),
	});
}

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(currentUser)}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['tasklist']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockQueryUserTasksEndpoint({successResponse: HttpResponse.json(createQueryUserTasksResponse())}),
	);
});

test.describe('Filter panel', () => {
	test('should expand and collapse the filter panel', async ({tasklistIndexPage}) => {
		await tasklistIndexPage.goto();

		await expect(tasklistIndexPage.expandFiltersButton).toBeVisible();
		await expect(tasklistIndexPage.filterTasksButton).toBeVisible();

		await tasklistIndexPage.expandFilters();

		await expect(tasklistIndexPage.filterLink('All open tasks')).toBeVisible();
		await expect(tasklistIndexPage.filterLink('Assigned to me')).toBeVisible();
		await expect(tasklistIndexPage.filterLink('Unassigned')).toBeVisible();
		await expect(tasklistIndexPage.filterLink('Completed')).toBeVisible();
		await expect(tasklistIndexPage.newFilterButton).toBeVisible();
		await expect(tasklistIndexPage.collapseFiltersButton).toBeVisible();

		await tasklistIndexPage.collapseFiltersButton.click();

		await expect(tasklistIndexPage.expandFiltersButton).toBeVisible();
		await expect(tasklistIndexPage.filterLink('Assigned to me')).not.toBeVisible();
	});

	test('should navigate to the completed filter with completion sorting', async ({page, tasklistIndexPage}) => {
		await tasklistIndexPage.goto();
		await tasklistIndexPage.expandFilters();

		await tasklistIndexPage.filterLink('Completed').click();

		const params = new URL(page.url()).searchParams;
		expect(params.get('filter')).toBe('completed');
		expect(params.get('sortBy')).toBe('completion');
		await expect(tasklistIndexPage.tasksPanelHeading('Completed')).toBeVisible();
	});

	test('should mark the active filter with aria-current', async ({page, tasklistIndexPage}) => {
		await page.goto('/tasklist?filter=unassigned');
		await tasklistIndexPage.expandFilters();

		await expect(tasklistIndexPage.filterLink('Unassigned')).toHaveAttribute('aria-current', 'page');
		await expect(tasklistIndexPage.filterLink('All open tasks')).not.toHaveAttribute('aria-current', 'page');
		await expect(tasklistIndexPage.filterLink('Assigned to me')).not.toHaveAttribute('aria-current', 'page');
		await expect(tasklistIndexPage.filterLink('Completed')).not.toHaveAttribute('aria-current', 'page');
	});
});

test.describe('Filter request bodies', () => {
	test('should request tasks assigned to the current user', async ({network, page, tasklistIndexPage}) => {
		network.use(
			mockQueryUserTasksEndpoint({
				schema: createUserTasksRequestSchema({
					filter: z.object({
						assignee: z.literal('demo'),
						state: z.literal('CREATED'),
					}),
					sortField: 'creationDate',
				}),
				successResponse: HttpResponse.json(
					createQueryUserTasksResponse({
						items: [createUserTask({userTaskKey: '1', name: 'Assigned task'})],
					}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		await page.goto('/tasklist?filter=assigned-to-me');

		await expect(tasklistIndexPage.tasksPanelHeading('Assigned to me')).toBeVisible();
		await expect(tasklistIndexPage.taskItem('Assigned task')).toBeVisible();
	});

	test('should request unassigned tasks', async ({network, page, tasklistIndexPage}) => {
		network.use(
			mockQueryUserTasksEndpoint({
				schema: createUserTasksRequestSchema({
					filter: z.object({
						state: z.literal('CREATED'),
						assignee: z.object({$exists: z.literal(false)}),
					}),
					sortField: 'creationDate',
				}),
				successResponse: HttpResponse.json(
					createQueryUserTasksResponse({
						items: [createUserTask({userTaskKey: '1', name: 'Unassigned task'})],
					}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		await page.goto('/tasklist?filter=unassigned');

		await expect(tasklistIndexPage.tasksPanelHeading('Unassigned')).toBeVisible();
		await expect(tasklistIndexPage.taskItem('Unassigned task')).toBeVisible();
	});

	test('should request completed tasks sorted by completion date', async ({network, page, tasklistIndexPage}) => {
		network.use(
			mockQueryUserTasksEndpoint({
				schema: createUserTasksRequestSchema({
					filter: z.object({
						state: z.literal('COMPLETED'),
					}),
					sortField: 'completionDate',
				}),
				successResponse: HttpResponse.json(
					createQueryUserTasksResponse({
						items: [createUserTask({userTaskKey: '1', name: 'Completed task'})],
					}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		await page.goto('/tasklist?filter=completed&sortBy=completion');

		await expect(tasklistIndexPage.tasksPanelHeading('Completed')).toBeVisible();
		await expect(tasklistIndexPage.taskItem('Completed task')).toBeVisible();

		await test.step('offers completion-date sorting in the sort menu', async () => {
			await tasklistIndexPage.openSortMenu();
			await expect(tasklistIndexPage.sortOption('Completion date')).toBeVisible();
		});
	});
});

test.describe('Sorting', () => {
	test('should update the URL and send the correct sort field when a sort option is selected', async ({
		network,
		page,
		tasklistIndexPage,
	}) => {
		await tasklistIndexPage.goto();

		await expect(tasklistIndexPage.tasksPanelHeading('All open tasks')).toBeVisible();

		network.use(
			mockQueryUserTasksEndpoint({
				schema: createUserTasksRequestSchema({filter: allOpenFilter, sortField: 'dueDate'}),
				successResponse: HttpResponse.json(
					createQueryUserTasksResponse({
						items: [createUserTask({userTaskKey: '1', name: 'Sorted task'})],
					}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		await tasklistIndexPage.openSortMenu();
		await tasklistIndexPage.sortOption('Due date').click();

		expect(new URL(page.url()).searchParams.get('sortBy')).toBe('due');
		await expect(tasklistIndexPage.taskItem('Sorted task')).toBeVisible();
	});

	test('should not offer completion-date sorting for non-completed filters', async ({tasklistIndexPage}) => {
		await tasklistIndexPage.goto();

		await tasklistIndexPage.openSortMenu();

		await expect(tasklistIndexPage.sortOption('Creation date')).toBeVisible();
		await expect(tasklistIndexPage.sortOption('Due date')).toBeVisible();
		await expect(tasklistIndexPage.sortOption('Follow-up date')).toBeVisible();
		await expect(tasklistIndexPage.sortOption('Priority')).toBeVisible();
		await expect(tasklistIndexPage.sortOption('Completion date')).not.toBeVisible();
	});

	test('should reset completion sorting when the filter is not completed', async ({
		network,
		page,
		tasklistIndexPage,
	}) => {
		network.use(
			mockQueryUserTasksEndpoint({
				schema: createUserTasksRequestSchema({filter: allOpenFilter, sortField: 'creationDate'}),
				successResponse: HttpResponse.json(
					createQueryUserTasksResponse({
						items: [createUserTask({userTaskKey: '1', name: 'Reset sort task'})],
					}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		await page.goto('/tasklist?filter=all-open&sortBy=completion');

		await expect(tasklistIndexPage.tasksPanelHeading('All open tasks')).toBeVisible();
		expect(new URL(page.url()).searchParams.get('sortBy')).toBeNull();
		await expect(tasklistIndexPage.taskItem('Reset sort task')).toBeVisible();
	});
});
