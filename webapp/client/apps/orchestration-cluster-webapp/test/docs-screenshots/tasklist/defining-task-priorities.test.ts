/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Generates the screenshots for the "Defining task priorities" docs page:
 * https://docs.camunda.io/docs/next/components/tasklist/userguide/defining-task-priorities/
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
const FORM_KEY = '2251799813685890';

const COMPANY_REGISTRATION_FORM_SCHEMA = JSON.stringify({
	components: [
		{
			type: 'text',
			text: '#### Company details',
		},
		{
			key: 'legalCompanyName',
			label: 'Legal company name',
			type: 'textfield',
		},
		{
			key: 'workPhone',
			label: 'Work phone',
			type: 'textfield',
			layout: {row: 'contact'},
		},
		{
			key: 'email',
			label: 'Email',
			type: 'textfield',
			layout: {row: 'contact'},
		},
		{
			key: 'companyAddress',
			label: 'Company address',
			type: 'textfield',
		},
		{
			key: 'city',
			label: 'City',
			type: 'textfield',
			layout: {row: 'location', columns: 12},
		},
		{
			key: 'zipCode',
			label: 'ZIP code',
			type: 'textfield',
			layout: {row: 'location', columns: 4},
		},
	],
	type: 'default',
	id: 'company-registration-form',
});

const kycReviewTask = createUserTask({
	userTaskKey: '2251799813685801',
	name: 'KYC review',
	processName: 'Customer support',
	assignee: null,
	priority: 90,
	creationDate: '2024-09-02T10:00:00.000Z',
	dueDate: '2024-09-20T17:00:00.000Z',
});
const companyRegistrationTask = createUserTask({
	userTaskKey: '2251799813685802',
	name: 'Company registration',
	processName: 'Credit request',
	assignee: null,
	priority: 70,
	formKey: FORM_KEY,
	creationDate: '2024-09-04T13:23:00.000Z',
	dueDate: '2024-09-04T17:00:00.000Z',
});
const loanApprovalTask = createUserTask({
	userTaskKey: '2251799813685803',
	name: 'Loan approval',
	processName: 'Credit request',
	assignee: 'demo',
	priority: 50,
	creationDate: '2024-09-02T09:00:00.000Z',
	dueDate: '2024-09-06T17:00:00.000Z',
});
const bankAccountTask = createUserTask({
	userTaskKey: '2251799813685804',
	name: 'Open a bank account',
	processName: 'Credit request',
	assignee: 'john.doe',
	priority: 40,
	creationDate: '2024-08-30T11:00:00.000Z',
	followUpDate: '2024-09-12T09:00:00.000Z',
	dueDate: '2024-09-15T17:00:00.000Z',
});
const lowPriorityBankAccountTask = createUserTask({
	userTaskKey: '2251799813685805',
	name: 'Open a bank account',
	processName: 'Credit request',
	assignee: 'demo',
	priority: 20,
	creationDate: '2024-08-28T15:00:00.000Z',
});

const TASKS_BY_CREATION_DATE = [
	companyRegistrationTask,
	kycReviewTask,
	loanApprovalTask,
	bankAccountTask,
	lowPriorityBankAccountTask,
];
const TASKS_BY_PRIORITY = [
	kycReviewTask,
	companyRegistrationTask,
	loanApprovalTask,
	bankAccountTask,
	lowPriorityBankAccountTask,
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
			successResponse: HttpResponse.json(createQueryUserTasksResponse({items: TASKS_BY_CREATION_DATE})),
		}),
		mockGetUserTaskEndpoint({
			successResponse: HttpResponse.json(companyRegistrationTask),
		}),
		mockGetUserTaskFormEndpoint({
			successResponse: HttpResponse.json(
				createUserTaskFormResponse({
					formKey: FORM_KEY,
					schema: COMPANY_REGISTRATION_FORM_SCHEMA,
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

test.describe('defining-task-priorities', () => {
	test('task priority labels', async ({tasklistIndexPage, taskDetailPage, page}) => {
		await taskDetailPage.goto(companyRegistrationTask.userTaskKey);
		await expect(taskDetailPage.taskName('Company registration')).toBeVisible();
		await expect(taskDetailPage.assignButton).toBeVisible();
		await expect(taskDetailPage.taskTabContent.getByLabel('Legal company name')).toBeVisible();
		await expect(tasklistIndexPage.tasksPanel.getByText('Priority: Critical', {exact: true})).toBeVisible();
		await expect(tasklistIndexPage.tasksPanel.getByText('Priority: High', {exact: true})).toBeVisible();
		await expect(tasklistIndexPage.tasksPanel.getByText('Priority: Medium', {exact: true})).toHaveCount(2);
		await expect(tasklistIndexPage.tasksPanel.getByText('Priority: Low', {exact: true})).toBeVisible();

		await page.screenshot({
			path: getScreenshotPath({
				baseUrl: import.meta.url,
				folder: 'defining-task-priorities',
				fileName: 'tasklist-tasks-with-priority.png',
			}),
		});
	});

	test('sort by priority', async ({network, tasklistIndexPage, taskDetailPage, page}) => {
		network.use(
			mockQueryUserTasksEndpoint({
				successResponse: HttpResponse.json(createQueryUserTasksResponse({items: TASKS_BY_PRIORITY})),
			}),
		);

		await taskDetailPage.goto(companyRegistrationTask.userTaskKey, '?sortBy=priority');
		await expect(taskDetailPage.taskName('Company registration')).toBeVisible();
		await expect(taskDetailPage.taskTabContent.getByLabel('Legal company name')).toBeVisible();
		await expect(tasklistIndexPage.tasksPanel.getByText('Priority: Critical', {exact: true})).toBeVisible();
		await tasklistIndexPage.openSortMenu();
		await expect(tasklistIndexPage.sortOption('Priority')).toBeChecked();
		await tasklistIndexPage.sortOption('Priority').hover();

		await page.screenshot({
			path: getScreenshotPath({
				baseUrl: import.meta.url,
				folder: 'defining-task-priorities',
				fileName: 'tasklist-tasks-with-priority-sorting.png',
			}),
		});
	});
});
