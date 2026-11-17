/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Generates the screenshots for the Tasklist "Localization" docs page:
 * https://docs.camunda.io/docs/next/components/tasklist/userguide/tasklist-localization/
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
const FORM_KEY = '2251799813685990';

const assignedPassengerTask = createUserTask({
	userTaskKey: '2251799813685901',
	name: 'Register the passenger',
	processName: 'Flight registration',
	assignee: 'demo',
	candidateGroups: ['group1', 'group2'],
	formKey: FORM_KEY,
	priority: 70,
	creationDate: '2024-09-05T13:15:00.000Z',
	dueDate: '2024-09-10T13:15:00.000Z',
});

const TASKS = [
	assignedPassengerTask,
	createUserTask({
		userTaskKey: '2251799813685902',
		name: 'Check payment',
		processName: 'Order process',
		assignee: 'demo',
		priority: 50,
		creationDate: '2024-09-04T10:00:00.000Z',
		dueDate: '2024-09-06T17:00:00.000Z',
	}),
	createUserTask({
		userTaskKey: '2251799813685903',
		name: 'Approve loan',
		processName: 'Credit request',
		assignee: 'john.doe',
		priority: 50,
		creationDate: '2024-09-02T09:30:00.000Z',
		followUpDate: '2024-09-08T09:00:00.000Z',
		dueDate: '2024-09-12T17:00:00.000Z',
	}),
	createUserTask({
		userTaskKey: '2251799813685904',
		name: 'KYC review',
		processName: 'Customer support',
		assignee: null,
		priority: 90,
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
			successResponse: HttpResponse.json(assignedPassengerTask),
		}),
		mockGetUserTaskFormEndpoint({
			successResponse: HttpResponse.json(createUserTaskFormResponse({formKey: FORM_KEY})),
		}),
		mockQueryVariablesByUserTaskEndpoint({
			successResponse: HttpResponse.json(createQueryVariablesByUserTaskResponse()),
		}),
	);

	await page.clock.setFixedTime(NOW);
	await taskDetailPage.seedHideNotificationBanner();
	await tasklistIndexPage.seedHasCompletedTask();
});

test.describe('tasklist-localization', () => {
	test('language settings', async ({tasklistIndexPage, taskDetailPage, page}) => {
		const {header} = tasklistIndexPage;

		await taskDetailPage.goto(assignedPassengerTask.userTaskKey);
		await expect(taskDetailPage.taskName('Register the passenger')).toBeVisible();
		await expect(taskDetailPage.taskTabContent.getByLabel(/^First name/)).toBeVisible();
		await header.openUserSidebar();
		await expect(header.languageSelector).toBeVisible();
		await expect(header.getLanguageOption('English')).toBeChecked();
		await expect(header.getLanguageOption('Deutsch')).toBeVisible();
		await expect(header.getLanguageOption('Français')).toBeVisible();
		await expect(header.getLanguageOption('Español')).toBeVisible();

		await page.screenshot({
			path: getScreenshotPath({
				baseUrl: import.meta.url,
				folder: 'localization',
				fileName: 'tasklist-language-settings.png',
			}),
		});
	});
});
