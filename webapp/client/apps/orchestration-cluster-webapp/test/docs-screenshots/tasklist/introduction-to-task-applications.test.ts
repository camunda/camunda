/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Generates the screenshots for the "Introduction to task applications" docs page:
 * https://docs.camunda.io/docs/next/apis-tools/frontend-development/task-applications/introduction-to-task-applications/
 */

import {test, expect} from '#/pw-modules/test-extend';
import {HttpResponse} from 'msw';
import {
	mockCurrentUserEndpoint,
	mockGetUserTaskEndpoint,
	mockGetUserTaskFormEndpoint,
	mockLicenseEndpoint,
	mockQueryUserTasksEndpoint,
	mockQueryVariablesByUserTaskEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createQueryUserTasksResponse, createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';
import {createUserTaskFormResponse} from '#/shared-test-modules/api-mocks/forms';
import {createQueryVariablesByUserTaskResponse} from '#/shared-test-modules/api-mocks/variables';
import {addCallouts, frameApp} from '../callouts';
import {getScreenshotPath} from '../screenshot-path';

const NOW = new Date('2024-09-05T13:32:00.000Z');
const CREATION_DATE = '2024-09-05T13:15:00.000Z';
const FORM_KEY = '2251799813685290';

const flightRegistrationTask = {
	name: 'Register the passenger',
	processName: 'Flight registration',
	candidateGroups: ['group1', 'group2'],
	formKey: FORM_KEY,
	priority: 60,
	creationDate: CREATION_DATE,
	dueDate: '2024-09-10T13:15:00.000Z',
	followUpDate: '2024-09-09T13:15:00.000Z',
};

const unassignedPassengerTask = createUserTask({
	...flightRegistrationTask,
	userTaskKey: '2251799813686001',
	assignee: null,
});

const TASKS = [
	unassignedPassengerTask,
	createUserTask({
		userTaskKey: '2251799813686002',
		name: 'Check payment',
		processName: 'Order process',
		assignee: 'demo',
		priority: 60,
		creationDate: CREATION_DATE,
		dueDate: '2024-09-06T13:15:00.000Z',
	}),
	createUserTask({
		userTaskKey: '2251799813686003',
		name: 'Approve loan',
		processName: 'Credit request',
		assignee: 'john.doe',
		priority: 50,
		creationDate: '2024-09-02T09:30:00.000Z',
		followUpDate: '2024-09-08T09:00:00.000Z',
		dueDate: '2024-09-12T17:00:00.000Z',
	}),
	createUserTask({
		userTaskKey: '2251799813686004',
		name: 'Register car for rent',
		processName: 'Car rental',
		assignee: null,
		priority: 90,
		creationDate: '2024-09-04T15:00:00.000Z',
		dueDate: '2024-09-07T12:00:00.000Z',
	}),
	createUserTask({
		...flightRegistrationTask,
		userTaskKey: '2251799813686005',
		assignee: 'demo',
	}),
	createUserTask({
		userTaskKey: '2251799813686006',
		name: 'Review order',
		processName: 'Order process',
		assignee: 'demo',
		priority: 10,
		creationDate: '2024-09-03T11:00:00.000Z',
	}),
];

test.beforeEach(async ({network, page, tasklistIndexPage, taskDetailPage}) => {
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
			successResponse: HttpResponse.json(createQueryUserTasksResponse({items: TASKS})),
		}),
		mockGetUserTaskEndpoint({
			successResponse: HttpResponse.json(unassignedPassengerTask),
		}),
		mockQueryVariablesByUserTaskEndpoint({
			successResponse: HttpResponse.json(createQueryVariablesByUserTaskResponse()),
		}),
		mockGetUserTaskFormEndpoint({
			successResponse: HttpResponse.json(createUserTaskFormResponse({formKey: FORM_KEY})),
		}),
	);

	await page.clock.setFixedTime(NOW);
	await taskDetailPage.seedHideNotificationBanner();
	await tasklistIndexPage.seedHasCompletedTask();
});

test.describe('introduction-to-task-applications', () => {
	test('detailed tasks page layout', async ({tasklistIndexPage, taskDetailPage, page}) => {
		await taskDetailPage.goto(unassignedPassengerTask.userTaskKey);
		await expect(taskDetailPage.detailsInfo).toBeVisible();
		await expect(taskDetailPage.taskName('Register the passenger')).toBeVisible();
		await expect(taskDetailPage.assignButton).toBeVisible();
		await expect(taskDetailPage.taskTabContent.getByLabel(/^First name/)).toBeVisible();
		await expect(tasklistIndexPage.taskItem('Review order')).toBeVisible();

		await frameApp(page, {scale: 0.84});
		await addCallouts(page, [
			{label: 'Filters', target: tasklistIndexPage.filterSelect, side: 'left', distance: 96, outline: true},
			{label: 'Tasks queue', target: tasklistIndexPage.tasksPanel, side: 'top', distance: 24, outline: true},
			{
				label: 'Task card',
				target: tasklistIndexPage.tasksPanel.getByRole('article').nth(1),
				side: 'left',
				distance: 64,
				outline: true,
			},
			{label: 'Selected task details', target: taskDetailPage.detailsInfo, side: 'top', distance: 24, outline: true},
			{
				label: 'Task title',
				target: taskDetailPage.taskName('Register the passenger'),
				side: 'right',
				distance: 48,
				fitText: true,
			},
			{label: 'Assign action', target: taskDetailPage.assignButton, side: 'bottom', distance: 32, offset: -60},
			{
				label: 'Form',
				target: taskDetailPage.taskTabContent.locator('.fjs-form'),
				side: 'bottom',
				distance: 32,
				outline: true,
			},
			{
				label: 'Task summary',
				target: taskDetailPage.aside.getByText('Follow up date', {exact: true}).locator('..'),
				side: 'bottom',
				distance: 40,
			},
		]);

		await page.screenshot({
			path: getScreenshotPath({
				baseUrl: import.meta.url,
				folder: 'introduction-to-task-applications',
				fileName: 'tasklist-page-specifications-detailed.png',
			}),
		});
	});
});
