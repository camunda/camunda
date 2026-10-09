/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Generates the screenshots for the "Using filters" docs page:
 * https://docs.camunda.io/docs/next/components/tasklist/userguide/using-filters/
 */

import {test, expect} from '#/pw-modules/test-extend';
import {HttpResponse} from 'msw';
import {
	mockCurrentUserEndpoint,
	mockGetUserTaskEndpoint,
	mockLicenseEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockQueryUserTasksEndpoint,
	mockQueryVariablesByUserTaskEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createQueryVariablesByUserTaskResponse} from '#/shared-test-modules/api-mocks/variables';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createQueryUserTasksResponse, createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {getScreenshotPath} from '../screenshot-path';

const NOW = new Date('2024-09-05T13:32:00.000Z');
const CREATION_DATE = '2024-09-05T13:15:00.000Z';
const DUE_DATE = '2024-09-10T13:15:00.000Z';
const FLIGHT_REGISTRATION_KEY = '2251799813685401';
const SAVED_FILTER_NAME = 'Flight register tasks';

const flightRegistrationProcess = createProcessDefinition({
	name: 'Flight registration',
	processDefinitionId: 'flight-registration',
	processDefinitionKey: FLIGHT_REGISTRATION_KEY,
	version: 1,
});
const orderProcess = createProcessDefinition({
	name: 'Order process',
	processDefinitionId: 'order-process',
	processDefinitionKey: '2251799813685402',
	version: 2,
});
const carRentalProcess = createProcessDefinition({
	name: 'Car rental',
	processDefinitionId: 'car-rental',
	processDefinitionKey: '2251799813685403',
	version: 1,
});

const assignedPassengerTasks = ['2251799813685501', '2251799813685502', '2251799813685503'].map((userTaskKey) =>
	createUserTask({
		userTaskKey,
		name: 'Register the passenger',
		processName: 'Flight registration',
		processDefinitionKey: FLIGHT_REGISTRATION_KEY,
		assignee: 'demo',
		candidateGroups: ['group1', 'group2'],
		creationDate: CREATION_DATE,
		dueDate: DUE_DATE,
	}),
);
const unassignedPassengerTask = createUserTask({
	userTaskKey: '2251799813685504',
	name: 'Register the passenger',
	processName: 'Flight registration',
	processDefinitionKey: FLIGHT_REGISTRATION_KEY,
	assignee: null,
	candidateGroups: ['group1', 'group2'],
	creationDate: CREATION_DATE,
	dueDate: DUE_DATE,
});
const checkPaymentTask = createUserTask({
	userTaskKey: '2251799813685505',
	name: 'Check payment',
	processName: 'Order process',
	processDefinitionKey: orderProcess.processDefinitionKey,
	assignee: 'demo',
	creationDate: CREATION_DATE,
});
const registerCarTask = createUserTask({
	userTaskKey: '2251799813685506',
	name: 'Register car for rent',
	processName: 'Car rental',
	processDefinitionKey: carRentalProcess.processDefinitionKey,
	assignee: null,
	creationDate: CREATION_DATE,
});

const ALL_OPEN_TASKS = [...assignedPassengerTasks, unassignedPassengerTask, checkPaymentTask, registerCarTask];

const SAVED_FILTERS = {
	'filter-1': {
		name: SAVED_FILTER_NAME,
		assignee: 'me',
		status: 'open',
		bpmnProcess: FLIGHT_REGISTRATION_KEY,
	},
};

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
			successResponse: HttpResponse.json(createQueryUserTasksResponse({items: ALL_OPEN_TASKS})),
		}),
		mockQueryProcessDefinitionsEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessDefinitionsResponse({
					items: [flightRegistrationProcess, orderProcess, carRentalProcess],
				}),
			),
		}),
		// The router preloads task details when the pointer hovers a task card (defaultPreload: 'intent')
		mockGetUserTaskEndpoint({
			successResponse: HttpResponse.json(assignedPassengerTasks[0]),
		}),
		mockQueryVariablesByUserTaskEndpoint({
			successResponse: HttpResponse.json(createQueryVariablesByUserTaskResponse()),
		}),
	);

	await page.clock.setFixedTime(NOW);
	await taskDetailPage.seedHideNotificationBanner();
	await tasklistIndexPage.seedHasCompletedTask();
});

test.describe('using-filters', () => {
	test('all open tasks', async ({tasklistIndexPage, page}) => {
		await tasklistIndexPage.goto();
		await expect(tasklistIndexPage.tasksPanelHeading('All open tasks')).toBeVisible();
		await expect(tasklistIndexPage.taskItem('Check payment')).toBeVisible();
		await expect(tasklistIndexPage.taskItem('Register car for rent')).toBeVisible();
		await expect(page.getByRole('heading', {name: 'Pick a task to work on'})).toBeVisible();

		await page.screenshot({
			path: getScreenshotPath({baseUrl: import.meta.url, folder: 'using-filters', fileName: 'tasklist-all-tasks.png'}),
		});
	});

	test('default filters', async ({tasklistIndexPage, page}) => {
		await tasklistIndexPage.goto();
		await expect(tasklistIndexPage.taskItem('Check payment')).toBeVisible();
		await tasklistIndexPage.expandFilters();
		await expect(tasklistIndexPage.filterOption('All open tasks')).toBeVisible();
		await expect(tasklistIndexPage.filterOption('Assigned to me')).toBeVisible();
		await expect(tasklistIndexPage.filterOption('Unassigned')).toBeVisible();
		await expect(tasklistIndexPage.filterOption('Completed')).toBeVisible();
		await expect(tasklistIndexPage.newFilterButton).toBeVisible();

		await page.screenshot({
			path: getScreenshotPath({
				baseUrl: import.meta.url,
				folder: 'using-filters',
				fileName: 'tasklist-default-filters.png',
			}),
		});
	});

	test('filter dialog with advanced options', async ({tasklistIndexPage, page}) => {
		const {customFiltersModal} = tasklistIndexPage;

		await tasklistIndexPage.goto();
		await expect(tasklistIndexPage.taskItem('Check payment')).toBeVisible();
		await tasklistIndexPage.filterTasksButton.click();
		await expect(customFiltersModal.heading).toBeVisible();
		await expect(customFiltersModal.processSelect).toBeVisible();
		await customFiltersModal.advancedFiltersToggle.click();
		await expect(customFiltersModal.advancedFiltersToggle).toBeChecked();
		await expect(customFiltersModal.businessIdField).toBeVisible();
		await expect(customFiltersModal.addVariableButton).toBeVisible();

		await page.screenshot({
			path: getScreenshotPath({
				baseUrl: import.meta.url,
				folder: 'using-filters',
				fileName: 'tasklist-filter-dialog-with-advanced-options.png',
			}),
		});
	});

	test('save filter dialog', async ({tasklistIndexPage, page}) => {
		const {customFiltersModal, filterNameModal} = tasklistIndexPage;

		await tasklistIndexPage.goto();
		await expect(tasklistIndexPage.taskItem('Check payment')).toBeVisible();
		await tasklistIndexPage.filterTasksButton.click();
		await expect(customFiltersModal.heading).toBeVisible();
		await customFiltersModal.assigneeOption('Me').click();
		await customFiltersModal.statusOption('Open').click();
		await customFiltersModal.processSelect.click();
		await customFiltersModal.processOption('Flight registration - v1').click();
		await expect(customFiltersModal.processSelect).toHaveText(/Flight registration/);
		await customFiltersModal.saveButton.click();
		await expect(filterNameModal.dialog).toBeVisible();
		await filterNameModal.nameInput.fill(SAVED_FILTER_NAME);
		await expect(filterNameModal.saveAndApplyButton).toBeEnabled();

		await page.screenshot({
			path: getScreenshotPath({
				baseUrl: import.meta.url,
				folder: 'using-filters',
				fileName: 'tasklist-save-filter-dialog.png',
			}),
		});
	});

	test('applied saved filter', async ({network, tasklistIndexPage, page}) => {
		await tasklistIndexPage.seedCustomFilters(SAVED_FILTERS);
		await tasklistIndexPage.goto();
		await expect(tasklistIndexPage.taskItem('Check payment')).toBeVisible();

		network.use(
			mockQueryUserTasksEndpoint({
				successResponse: HttpResponse.json(createQueryUserTasksResponse({items: assignedPassengerTasks})),
			}),
		);

		await tasklistIndexPage.expandFilters();
		await tasklistIndexPage.customFilterLink(SAVED_FILTER_NAME).click();
		await expect(tasklistIndexPage.tasksPanelHeading(SAVED_FILTER_NAME)).toBeVisible();
		await expect(tasklistIndexPage.taskItem('Check payment')).not.toBeVisible();
		await expect(tasklistIndexPage.taskItem('Register the passenger')).toHaveCount(assignedPassengerTasks.length);

		await page.screenshot({
			path: getScreenshotPath({
				baseUrl: import.meta.url,
				folder: 'using-filters',
				fileName: 'tasklist-applied-filter-tasks.png',
			}),
		});
	});

	test('saved filter options', async ({tasklistIndexPage, page}) => {
		await tasklistIndexPage.seedCustomFilters(SAVED_FILTERS);
		await tasklistIndexPage.goto();
		await expect(tasklistIndexPage.taskItem('Check payment')).toBeVisible();
		await tasklistIndexPage.expandFilters();
		await tasklistIndexPage.customFilterActionsButton(SAVED_FILTER_NAME).click();
		await expect(tasklistIndexPage.customFilterOverflowItem('Edit')).toBeVisible();
		await expect(tasklistIndexPage.customFilterOverflowItem('Delete')).toBeVisible();

		await page.screenshot({
			path: getScreenshotPath({
				baseUrl: import.meta.url,
				folder: 'using-filters',
				fileName: 'tasklist-saved-filter-options.png',
			}),
		});
	});

	test('edit filter dialog', async ({tasklistIndexPage, page}) => {
		const {customFiltersModal} = tasklistIndexPage;

		await tasklistIndexPage.seedCustomFilters(SAVED_FILTERS);
		await tasklistIndexPage.goto();
		await expect(tasklistIndexPage.taskItem('Check payment')).toBeVisible();
		await tasklistIndexPage.expandFilters();
		await tasklistIndexPage.customFilterActionsButton(SAVED_FILTER_NAME).click();
		await tasklistIndexPage.customFilterOverflowItem('Edit').click();
		await expect(customFiltersModal.dialog).toBeVisible();
		await expect(customFiltersModal.assigneeOption('Me')).toBeChecked();
		await expect(customFiltersModal.statusOption('Open')).toBeChecked();
		await expect(customFiltersModal.processSelect).toHaveText(/Flight registration/);
		await expect(customFiltersModal.saveAndApplyButton).toBeVisible();

		await page.screenshot({
			path: getScreenshotPath({
				baseUrl: import.meta.url,
				folder: 'using-filters',
				fileName: 'tasklist-edit-filter-dialog.png',
			}),
		});
	});

	test('delete filter dialog', async ({tasklistIndexPage, page}) => {
		await tasklistIndexPage.seedCustomFilters(SAVED_FILTERS);
		await tasklistIndexPage.goto();
		await expect(tasklistIndexPage.taskItem('Check payment')).toBeVisible();
		await tasklistIndexPage.expandFilters();
		await tasklistIndexPage.customFilterActionsButton(SAVED_FILTER_NAME).click();
		await tasklistIndexPage.customFilterOverflowItem('Delete').click();
		await expect(tasklistIndexPage.deleteFilterModal.dialog).toBeVisible();
		await expect(tasklistIndexPage.deleteFilterModal.confirmButton).toBeVisible();

		await page.screenshot({
			path: getScreenshotPath({
				baseUrl: import.meta.url,
				folder: 'using-filters',
				fileName: 'tasklist-delete-filter-dialog.png',
			}),
		});
	});
});
