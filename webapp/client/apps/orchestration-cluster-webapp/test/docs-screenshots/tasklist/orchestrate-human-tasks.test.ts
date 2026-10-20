/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Generates the Tasklist screenshot for the "Orchestrate human tasks" guide:
 * https://docs.camunda.io/docs/next/guides/orchestrate-human-tasks/
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
import {getScreenshotPath} from '../screenshot-path';

const NOW = new Date('2024-09-05T13:32:00.000Z');
const FORM_KEY = '2251799813686390';

const DINNER_FORM_SCHEMA = JSON.stringify({
	components: [
		{
			type: 'text',
			text: "# What's for dinner?",
		},
		{
			key: 'meal',
			label: 'Meal',
			type: 'radio',
			validate: {required: true},
			values: [
				{label: 'Chicken', value: 'Chicken'},
				{label: 'Salad', value: 'Salad'},
			],
		},
	],
	type: 'default',
	id: 'whats-for-dinner-form',
});

const dinnerTask = createUserTask({
	userTaskKey: '2251799813686301',
	name: "Decide what's for dinner",
	processName: 'Preparing dinner',
	assignee: 'demo',
	priority: 50,
	formKey: FORM_KEY,
	creationDate: '2024-09-05T13:20:00.000Z',
});

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
			successResponse: HttpResponse.json(createQueryUserTasksResponse({items: [dinnerTask]})),
		}),
		mockGetUserTaskEndpoint({
			successResponse: HttpResponse.json(dinnerTask),
		}),
		mockGetUserTaskFormEndpoint({
			successResponse: HttpResponse.json(
				createUserTaskFormResponse({
					formKey: FORM_KEY,
					schema: DINNER_FORM_SCHEMA,
				}),
			),
		}),
		mockQueryVariablesByUserTaskEndpoint({
			successResponse: HttpResponse.json(createQueryVariablesByUserTaskResponse()),
		}),
	);

	await page.clock.setFixedTime(NOW);
	await taskDetailPage.seedHideNotificationBanner();
	await tasklistIndexPage.seedHasCompletedTask();
});

test.describe('orchestrate-human-tasks', () => {
	test('complete a human task', async ({taskDetailPage, page}) => {
		await taskDetailPage.goto(dinnerTask.userTaskKey);
		await expect(taskDetailPage.taskName("Decide what's for dinner")).toBeVisible();
		await expect(taskDetailPage.unassignButton).toBeVisible();

		const salad = taskDetailPage.taskTabContent.getByRole('radio', {name: 'Salad'});
		await expect(taskDetailPage.taskTabContent.getByRole('radio', {name: 'Chicken'})).toBeVisible();
		await salad.check();
		await expect(salad).toBeChecked();
		await expect(taskDetailPage.completeTaskButton).toBeEnabled();
		await taskDetailPage.completeTaskButton.focus();

		await page.screenshot({
			path: getScreenshotPath({
				baseUrl: import.meta.url,
				folder: 'orchestrate-human-tasks',
				fileName: 'user-task-tasklist.png',
			}),
		});
	});
});
