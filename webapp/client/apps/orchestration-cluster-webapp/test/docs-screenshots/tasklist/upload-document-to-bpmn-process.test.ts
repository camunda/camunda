/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Generates the Tasklist screenshot for the "Upload a document to a BPMN process" docs page:
 * https://docs.camunda.io/docs/next/components/document-handling/upload-document-to-bpmn-process/
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
const FORM_KEY = '2251799813686290';
const FILE_PICKER_LABEL = 'Upload an ID document (passport or driving licence)';

const PERSONAL_DETAILS_FORM_SCHEMA = JSON.stringify({
	components: [
		{key: 'firstName', label: 'First name', type: 'textfield', layout: {row: 'name'}},
		{key: 'lastName', label: 'Last name', type: 'textfield', layout: {row: 'name'}},
		{key: 'workPhone', label: 'Work phone', type: 'textfield', layout: {row: 'contact'}},
		{key: 'email', label: 'E-mail', type: 'textfield', layout: {row: 'contact'}},
		{key: 'address', label: 'Address', type: 'textfield'},
		{key: 'city', label: 'City', type: 'textfield', layout: {row: 'location', columns: 11}},
		{key: 'zipCode', label: 'ZIP code', type: 'textfield', layout: {row: 'location', columns: 5}},
		{key: 'idDocument', label: FILE_PICKER_LABEL, type: 'filepicker'},
	],
	type: 'default',
	id: 'provide-personal-details-form',
});

const personalDetailsTask = createUserTask({
	userTaskKey: '2251799813686201',
	name: 'Provide personal details',
	processName: 'Process name',
	assignee: null,
	priority: 50,
	formKey: FORM_KEY,
	creationDate: '2024-09-04T13:23:00.000Z',
	dueDate: '2024-09-12T17:00:00.000Z',
});

const TASKS = [
	personalDetailsTask,
	createUserTask({
		userTaskKey: '2251799813686202',
		name: 'Loan approval',
		processName: 'Camundia Credit Request',
		assignee: 'demo',
		priority: 50,
		creationDate: '2024-09-02T09:46:00.000Z',
		dueDate: '2024-09-06T17:00:00.000Z',
	}),
	createUserTask({
		userTaskKey: '2251799813686203',
		name: 'Loan approval',
		processName: 'Camundia Credit Request',
		assignee: 'John Doe',
		priority: 50,
		creationDate: '2024-08-11T12:34:00.000Z',
		followUpDate: '2024-09-15T09:00:00.000Z',
		dueDate: '2024-09-20T17:00:00.000Z',
	}),
	createUserTask({
		userTaskKey: '2251799813686204',
		name: 'Loan approval',
		processName: 'Camundia Credit Request',
		assignee: null,
		priority: 50,
		creationDate: '2024-08-12T13:23:00.000Z',
		dueDate: '2024-09-21T17:00:00.000Z',
	}),
	createUserTask({
		userTaskKey: '2251799813686205',
		name: 'Loan approval',
		processName: 'Camundia Credit Request',
		assignee: 'demo',
		priority: 50,
		creationDate: '2024-08-02T13:23:00.000Z',
		dueDate: '2024-09-17T17:00:00.000Z',
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
			successResponse: HttpResponse.json(personalDetailsTask),
		}),
		mockGetUserTaskFormEndpoint({
			successResponse: HttpResponse.json(
				createUserTaskFormResponse({
					formKey: FORM_KEY,
					schema: PERSONAL_DETAILS_FORM_SCHEMA,
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

test.describe('upload-document-to-bpmn-process', () => {
	test('file picker in a task form', async ({taskDetailPage, page}) => {
		await taskDetailPage.goto(personalDetailsTask.userTaskKey);
		await expect(taskDetailPage.taskName('Provide personal details')).toBeVisible();
		await expect(taskDetailPage.assignButton).toBeVisible();
		await expect(taskDetailPage.taskTabContent.getByLabel('First name')).toBeVisible();
		await expect(taskDetailPage.taskTabContent.getByText(FILE_PICKER_LABEL)).toBeVisible();
		await expect(taskDetailPage.taskTabContent.getByRole('button', {name: 'Browse'})).toBeVisible();
		await expect(taskDetailPage.taskTabContent.getByText('No files selected')).toBeVisible();

		await page.screenshot({
			path: getScreenshotPath({
				baseUrl: import.meta.url,
				folder: 'upload-document-to-bpmn-process',
				fileName: 'task-with-file-picker-tasklist.png',
			}),
		});
	});
});
