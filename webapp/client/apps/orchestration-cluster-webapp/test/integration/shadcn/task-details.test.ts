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
	test('should render task name, process name, and details panel', async ({shadcnTaskDetailPage, page}) => {
		await shadcnTaskDetailPage.goto('2251799813685281');

		await expect(shadcnTaskDetailPage.detailsInfo).toBeVisible();
		await expect(page.getByText('Review invoice')).toBeVisible();
		await expect(page.getByText('Invoice process')).toBeVisible();
		await expect(shadcnTaskDetailPage.aside).toBeVisible();
		await expect(page.getByText('Creation date')).toBeVisible();
		await expect(page.getByText('alice')).toBeVisible();
		await expect(page.getByText('managers')).toBeVisible();
	});

	test('should render the tab navigation with Task, Process, and History tabs', async ({shadcnTaskDetailPage}) => {
		await shadcnTaskDetailPage.goto('2251799813685281');

		await expect(shadcnTaskDetailPage.taskTab).toBeVisible();
		await expect(shadcnTaskDetailPage.processTab).toBeVisible();
		await expect(shadcnTaskDetailPage.historyTab).toBeVisible();
		await expect(shadcnTaskDetailPage.taskTab).toHaveAttribute('aria-selected', 'true');
	});

	test('should switch tabs and update the URL', async ({shadcnTaskDetailPage, page}) => {
		await shadcnTaskDetailPage.goto('2251799813685281');

		await shadcnTaskDetailPage.processTab.click();
		await expect(page).toHaveURL(/\/shadcn\/tasklist\/2251799813685281\/process/);
		await expect(shadcnTaskDetailPage.processTab).toHaveAttribute('aria-selected', 'true');
		await expect(shadcnTaskDetailPage.detailsInfo).toBeVisible();
		await expect(shadcnTaskDetailPage.aside).toBeVisible();

		await shadcnTaskDetailPage.historyTab.click();
		await expect(page).toHaveURL(/\/shadcn\/tasklist\/2251799813685281\/history/);
		await expect(shadcnTaskDetailPage.historyTab).toHaveAttribute('aria-selected', 'true');

		await shadcnTaskDetailPage.taskTab.click();
		await expect(page).toHaveURL(/\/shadcn\/tasklist\/2251799813685281$/);
		await expect(shadcnTaskDetailPage.taskTab).toHaveAttribute('aria-selected', 'true');
	});
});
