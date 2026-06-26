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
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createQueryUserTasksResponse, createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';

const currentUser = createCurrentUser();

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
			successResponse: HttpResponse.json(
				createUserTask({
					name: 'Review invoice',
					processName: 'Invoice process',
					assignee: null,
					candidateUsers: ['alice'],
					candidateGroups: ['managers'],
					priority: 50,
				}),
			),
		}),
	);
});

test.describe('Task details page', () => {
	test('should render task name, process name, and details panel', async ({taskDetailPage, page}) => {
		await taskDetailPage.goto('2251799813685281');

		await expect(taskDetailPage.detailsInfo).toBeVisible();
		await expect(page.getByText('Review invoice')).toBeVisible();
		await expect(page.getByText('Invoice process')).toBeVisible();
		await expect(taskDetailPage.aside).toBeVisible();
		await expect(page.getByText('Creation date')).toBeVisible();
		await expect(page.getByText('alice')).toBeVisible();
		await expect(page.getByText('managers')).toBeVisible();
	});

	test('should render the tab navigation with Task, Process, and History tabs', async ({taskDetailPage}) => {
		await taskDetailPage.goto('2251799813685281');

		await expect(taskDetailPage.taskTab).toBeVisible();
		await expect(taskDetailPage.processTab).toBeVisible();
		await expect(taskDetailPage.historyTab).toBeVisible();
		await expect(taskDetailPage.taskTab).toHaveAttribute('aria-current', 'page');
	});

	test('should switch tabs and update the URL', async ({taskDetailPage, page}) => {
		await taskDetailPage.goto('2251799813685281');

		await taskDetailPage.processTab.click();
		await expect(page).toHaveURL(/\/tasklist\/2251799813685281\/process/);
		await expect(taskDetailPage.processTabContent).toBeAttached();

		await taskDetailPage.historyTab.click();
		await expect(page).toHaveURL(/\/tasklist\/2251799813685281\/history/);
		await expect(taskDetailPage.historyTabContent).toBeAttached();

		await taskDetailPage.taskTab.click();
		await expect(page).toHaveURL(/\/tasklist\/2251799813685281$/);
		await expect(taskDetailPage.taskTabContent).toBeAttached();
	});

	test('should show the 404 page for a non-existent task', async ({network, taskDetailPage, page}) => {
		network.use(
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json({}, {status: 404}),
			}),
		);

		await taskDetailPage.goto('nonexistent-key');

		await expect(page.getByRole('heading', {name: /not found/i})).toBeVisible();
	});
});
