/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Generates the Tasklist screenshots for the "Listen to user tasks" docs page:
 * https://docs.camunda.io/docs/next/components/concepts/listen-to-user-tasks/
 */

import {test, expect} from '#/pw-modules/test-extend';
import {HttpResponse} from 'msw';
import {
	mockCurrentUserEndpoint,
	mockGetUserTaskEndpoint,
	mockLicenseEndpoint,
	mockQueryUserTasksEndpoint,
	mockQueryVariablesByUserTaskEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createQueryUserTasksResponse, createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';
import {createQueryVariablesByUserTaskResponse} from '#/shared-test-modules/api-mocks/variables';
import {getScreenshotPath} from '../screenshot-path';

const NOW = new Date('2024-09-05T13:32:00.000Z');

const listenerAssignedTask = createUserTask({
	userTaskKey: '2251799813686101',
	name: 'Assigned by creating task listener',
	processName: 'Task Listener Tutorial',
	assignee: 'john.doe@camunda.com',
	priority: 50,
	creationDate: NOW.toISOString(),
});

test.beforeEach(async ({network, page, taskDetailPage}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(createCurrentUser({username: 'demo'})),
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
			successResponse: HttpResponse.json(listenerAssignedTask),
		}),
		mockQueryVariablesByUserTaskEndpoint({
			successResponse: HttpResponse.json(createQueryVariablesByUserTaskResponse()),
		}),
	);

	await page.clock.setFixedTime(NOW);
	await taskDetailPage.seedHideNotificationBanner();
});

test.describe('listen-to-user-tasks', () => {
	test('no tasks found', async ({tasklistIndexPage, page}) => {
		await tasklistIndexPage.goto();
		await expect(tasklistIndexPage.tasksPanelHeading('All open tasks')).toBeVisible();
		await expect(tasklistIndexPage.noTasksMessage).toBeVisible();
		await expect(tasklistIndexPage.welcomeHeading).toBeVisible();
		await expect(page.getByRole('link', {name: 'View tutorial'})).toBeVisible();

		await page.screenshot({
			path: getScreenshotPath({
				baseUrl: import.meta.url,
				folder: 'listen-to-user-tasks',
				fileName: '6.2-no-tasks-found.png',
			}),
		});
	});

	test('task assigned by listener', async ({network, tasklistIndexPage, page}) => {
		network.use(
			mockQueryUserTasksEndpoint({
				successResponse: HttpResponse.json(createQueryUserTasksResponse({items: [listenerAssignedTask]})),
			}),
		);

		await tasklistIndexPage.goto();
		await expect(tasklistIndexPage.tasksPanelHeading('All open tasks')).toBeVisible();
		await expect(tasklistIndexPage.taskItem('Assigned by creating task listener')).toBeVisible();
		await expect(tasklistIndexPage.tasksPanel.getByTitle('Task assigned to john.doe@camunda.com')).toBeVisible();
		await expect(tasklistIndexPage.welcomeHeading).toBeVisible();

		await page.screenshot({
			path: getScreenshotPath({
				baseUrl: import.meta.url,
				folder: 'listen-to-user-tasks',
				fileName: '8.2-verify-task-assigned.png',
			}),
		});
	});
});
