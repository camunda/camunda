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

// Ported from test/integration/tasklist/tasklist-filters.test.ts (Carbon). The Carbon suite also
// covers expanding/collapsing the sidebar filter panel and an `aria-current` link state — those
// don't apply here since the built-in filter picker is a Select, not a sidebar of nav links.

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
	test('should navigate to the completed filter with completion sorting', async ({page, shadcnTasklistIndexPage}) => {
		await shadcnTasklistIndexPage.goto();

		await shadcnTasklistIndexPage.filterSelect.click();
		await shadcnTasklistIndexPage.filterOption('Completed').click();

		const params = new URL(page.url()).searchParams;
		expect(params.get('filter')).toBe('completed');
		expect(params.get('sortBy')).toBe('completion');
		await expect(shadcnTasklistIndexPage.filterSelect).toHaveText('Completed');
	});
});

test.describe('Filter request bodies', () => {
	test('should request tasks assigned to the current user', async ({network, page, shadcnTasklistIndexPage}) => {
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

		await page.goto('/shadcn/tasklist?filter=assigned-to-me');

		await expect(shadcnTasklistIndexPage.filterSelect).toHaveText('Assigned to me');
		await expect(shadcnTasklistIndexPage.taskItem('Assigned task')).toBeVisible();
	});

	test('should request unassigned tasks', async ({network, page, shadcnTasklistIndexPage}) => {
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

		await page.goto('/shadcn/tasklist?filter=unassigned');

		await expect(shadcnTasklistIndexPage.filterSelect).toHaveText('Unassigned');
		await expect(shadcnTasklistIndexPage.taskItem('Unassigned task')).toBeVisible();
	});

	test('should request completed tasks sorted by completion date', async ({network, page, shadcnTasklistIndexPage}) => {
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

		await page.goto('/shadcn/tasklist?filter=completed&sortBy=completion');

		await expect(shadcnTasklistIndexPage.filterSelect).toHaveText('Completed');
		await expect(shadcnTasklistIndexPage.taskItem('Completed task')).toBeVisible();

		await test.step('offers completion-date sorting in the sort menu', async () => {
			await shadcnTasklistIndexPage.openSortMenu();
			await expect(shadcnTasklistIndexPage.sortOption('Completion date')).toBeVisible();
		});
	});
});

test.describe('Sorting', () => {
	test('should update the URL and send the correct sort field when a sort option is selected', async ({
		network,
		page,
		shadcnTasklistIndexPage,
	}) => {
		await shadcnTasklistIndexPage.goto();

		await expect(shadcnTasklistIndexPage.filterSelect).toHaveText('All open tasks');

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

		await shadcnTasklistIndexPage.openSortMenu();
		await shadcnTasklistIndexPage.sortOption('Due date').click();

		expect(new URL(page.url()).searchParams.get('sortBy')).toBe('due');
		await expect(shadcnTasklistIndexPage.taskItem('Sorted task')).toBeVisible();
	});

	test('should not offer completion-date sorting for non-completed filters', async ({shadcnTasklistIndexPage}) => {
		await shadcnTasklistIndexPage.goto();

		await shadcnTasklistIndexPage.openSortMenu();

		await expect(shadcnTasklistIndexPage.sortOption('Creation date')).toBeVisible();
		await expect(shadcnTasklistIndexPage.sortOption('Due date')).toBeVisible();
		await expect(shadcnTasklistIndexPage.sortOption('Follow-up date')).toBeVisible();
		await expect(shadcnTasklistIndexPage.sortOption('Priority')).toBeVisible();
		await expect(shadcnTasklistIndexPage.sortOption('Completion date')).not.toBeVisible();
	});

	test('should reset completion sorting when the filter is not completed', async ({
		network,
		page,
		shadcnTasklistIndexPage,
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

		await page.goto('/shadcn/tasklist?filter=all-open&sortBy=completion');

		await expect(shadcnTasklistIndexPage.filterSelect).toHaveText('All open tasks');
		expect(new URL(page.url()).searchParams.get('sortBy')).toBeNull();
		await expect(shadcnTasklistIndexPage.taskItem('Reset sort task')).toBeVisible();
	});
});

test.describe('Auto-select toggle', () => {
	test('should persist the auto-select preference locally', async ({shadcnTasklistIndexPage}) => {
		await shadcnTasklistIndexPage.goto();

		await expect(shadcnTasklistIndexPage.autoSelectToggle).not.toBeChecked();

		await shadcnTasklistIndexPage.autoSelectToggle.click();

		await expect(shadcnTasklistIndexPage.autoSelectToggle).toBeChecked();
	});
});
