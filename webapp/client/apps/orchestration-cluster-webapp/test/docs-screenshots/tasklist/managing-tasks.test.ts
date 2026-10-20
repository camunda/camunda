/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Generates the screenshots for the "Managing tasks" docs page:
 * https://docs.camunda.io/docs/next/components/tasklist/userguide/managing-tasks/
 *
 * The "Don't miss new assignments" notifications banner (tasklist-notifications.png)
 * is not covered because it is not implemented in the unified webapp yet.
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
import {createQueryVariablesByUserTaskResponse, createVariable} from '#/shared-test-modules/api-mocks/variables';
import {getScreenshotPath} from '../screenshot-path';

const NOW = new Date('2024-09-05T13:32:00.000Z');
const CREATION_DATE = '2024-09-05T13:15:00.000Z';
const DUE_DATE = '2024-09-10T13:15:00.000Z';
const FORM_KEY = '2251799813685290';

const assignedPassengerTask = createUserTask({
	userTaskKey: '2251799813685301',
	name: 'Register the passenger',
	processName: 'Flight registration',
	assignee: 'demo',
	candidateGroups: ['group1', 'group2'],
	formKey: FORM_KEY,
	creationDate: CREATION_DATE,
	dueDate: DUE_DATE,
});
const unassignedPassengerTask = createUserTask({
	userTaskKey: '2251799813685302',
	name: 'Register the passenger',
	processName: 'Flight registration',
	assignee: null,
	candidateGroups: ['group1', 'group2'],
	formKey: FORM_KEY,
	creationDate: CREATION_DATE,
	dueDate: DUE_DATE,
});
const checkPaymentTask = createUserTask({
	userTaskKey: '2251799813685303',
	name: 'Check payment',
	processName: 'Order process',
	assignee: 'demo',
	creationDate: CREATION_DATE,
});
const registerCarTask = createUserTask({
	userTaskKey: '2251799813685304',
	name: 'Register car for rent',
	processName: 'Car rental',
	assignee: null,
	creationDate: CREATION_DATE,
});
const completedPassengerTask = createUserTask({
	...assignedPassengerTask,
	userTaskKey: '2251799813685305',
	state: 'COMPLETED',
	completionDate: '2024-09-05T13:31:00.000Z',
});
const completedCheckPaymentTask = createUserTask({
	...checkPaymentTask,
	userTaskKey: '2251799813685306',
	state: 'COMPLETED',
	completionDate: '2024-09-05T13:25:00.000Z',
});

const checkPaymentVariables = [
	createVariable({name: 'orderId', value: '"ORDER-2024-0042"', variableKey: '2251799813685311'}),
	createVariable({name: 'amount', value: '249.99', variableKey: '2251799813685312'}),
	createVariable({name: 'currency', value: '"EUR"', variableKey: '2251799813685313'}),
];

const passengerVariables = [
	createVariable({name: 'firstName', value: '"John"', variableKey: '2251799813685321'}),
	createVariable({name: 'lastName', value: '"Doe"', variableKey: '2251799813685322'}),
	createVariable({name: 'passportNumber', value: '"XY12345"', variableKey: '2251799813685323'}),
	createVariable({name: 'ticketNumber', value: '"AB1234"', variableKey: '2251799813685324'}),
	createVariable({name: 'registeredSuccessfully', value: 'true', variableKey: '2251799813685325'}),
];

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
		mockQueryVariablesByUserTaskEndpoint({
			successResponse: HttpResponse.json(createQueryVariablesByUserTaskResponse()),
		}),
		mockGetUserTaskFormEndpoint({
			successResponse: HttpResponse.json(createUserTaskFormResponse({formKey: FORM_KEY})),
		}),
	);

	await page.clock.setFixedTime(NOW);
	await taskDetailPage.seedHideNotificationBanner();
});

test.describe('managing-tasks', () => {
	test('tasks page', async ({network, tasklistIndexPage, page}) => {
		network.use(
			mockQueryUserTasksEndpoint({
				successResponse: HttpResponse.json(
					createQueryUserTasksResponse({
						items: [assignedPassengerTask, unassignedPassengerTask, checkPaymentTask, registerCarTask],
					}),
				),
			}),
		);

		await tasklistIndexPage.seedHasCompletedTask();
		await tasklistIndexPage.goto();
		await expect(tasklistIndexPage.tasksPanelHeading('All open tasks')).toBeVisible();
		await expect(tasklistIndexPage.taskItem('Check payment')).toBeVisible();
		await expect(tasklistIndexPage.taskItem('Register car for rent')).toBeVisible();
		await expect(page.getByRole('heading', {name: 'Pick a task to work on'})).toBeVisible();

		await page.screenshot({
			path: getScreenshotPath({
				baseUrl: import.meta.url,
				folder: 'managing-tasks',
				fileName: 'tasklist-start-screen_light.png',
			}),
		});
	});

	test('assign a task', async ({network, tasklistIndexPage, taskDetailPage, page}) => {
		network.use(
			mockQueryUserTasksEndpoint({
				successResponse: HttpResponse.json(
					createQueryUserTasksResponse({items: [unassignedPassengerTask, registerCarTask]}),
				),
			}),
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(unassignedPassengerTask),
			}),
		);

		await taskDetailPage.goto(unassignedPassengerTask.userTaskKey, '?filter=unassigned');
		await expect(tasklistIndexPage.tasksPanelHeading('Unassigned')).toBeVisible();
		await expect(taskDetailPage.taskName('Register the passenger')).toBeVisible();
		await expect(taskDetailPage.assignButton).toBeVisible();
		await expect(taskDetailPage.completeTaskButton).toBeDisabled();
		await taskDetailPage.assignButton.focus();

		await page.screenshot({
			path: getScreenshotPath({
				baseUrl: import.meta.url,
				folder: 'managing-tasks',
				fileName: 'tasklist-claim_light.png',
			}),
		});
	});

	test('tasks assigned to me', async ({network, tasklistIndexPage, taskDetailPage, page}) => {
		network.use(
			mockQueryUserTasksEndpoint({
				successResponse: HttpResponse.json(
					createQueryUserTasksResponse({items: [assignedPassengerTask, checkPaymentTask]}),
				),
			}),
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(assignedPassengerTask),
			}),
		);

		await taskDetailPage.goto(assignedPassengerTask.userTaskKey, '?filter=assigned-to-me');
		await expect(tasklistIndexPage.tasksPanelHeading('Assigned to me')).toBeVisible();
		await expect(taskDetailPage.selectedTask('Register the passenger')).toBeVisible();
		await expect(taskDetailPage.unassignButton).toBeVisible();
		await expect(taskDetailPage.taskTabContent.getByLabel(/^First name/)).toBeVisible();

		await page.screenshot({
			path: getScreenshotPath({
				baseUrl: import.meta.url,
				folder: 'managing-tasks',
				fileName: 'tasklist-claimed-by-me-list_light.png',
			}),
		});
	});

	test('complete a task with a form', async ({network, tasklistIndexPage, taskDetailPage, page}) => {
		network.use(
			mockQueryUserTasksEndpoint({
				successResponse: HttpResponse.json(
					createQueryUserTasksResponse({items: [assignedPassengerTask, checkPaymentTask]}),
				),
			}),
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(assignedPassengerTask),
			}),
		);

		await taskDetailPage.goto(assignedPassengerTask.userTaskKey, '?filter=assigned-to-me');
		await expect(tasklistIndexPage.tasksPanelHeading('Assigned to me')).toBeVisible();
		await expect(taskDetailPage.unassignButton).toBeVisible();

		const form = taskDetailPage.taskTabContent;
		await form.getByLabel(/^First name/).fill('John');
		await form.getByLabel(/^Last name/).fill('Doe');
		await form.getByLabel(/^Passport number/).fill('XY12345');
		await form.getByLabel(/^Ticket number/).fill('AB1234');
		await form.getByRole('checkbox', {name: 'Registered successfully'}).check();
		await expect(taskDetailPage.completeTaskButton).toBeEnabled();
		await taskDetailPage.completeTaskButton.focus();

		await page.screenshot({
			path: getScreenshotPath({
				baseUrl: import.meta.url,
				folder: 'managing-tasks',
				fileName: 'tasklist-completing-task_light.png',
			}),
		});
	});

	test('task with variables', async ({network, tasklistIndexPage, taskDetailPage, page}) => {
		network.use(
			mockQueryUserTasksEndpoint({
				successResponse: HttpResponse.json(
					createQueryUserTasksResponse({items: [checkPaymentTask, assignedPassengerTask]}),
				),
			}),
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(checkPaymentTask),
			}),
			mockQueryVariablesByUserTaskEndpoint({
				successResponse: HttpResponse.json(createQueryVariablesByUserTaskResponse({items: checkPaymentVariables})),
			}),
		);

		await taskDetailPage.goto(checkPaymentTask.userTaskKey, '?filter=assigned-to-me');
		await expect(tasklistIndexPage.tasksPanelHeading('Assigned to me')).toBeVisible();
		await expect(taskDetailPage.taskName('Check payment')).toBeVisible();
		await expect(taskDetailPage.variableValueInput('orderId')).toBeVisible();
		await expect(taskDetailPage.variableValueInput('currency')).toBeVisible();
		await expect(taskDetailPage.completeTaskButton).toBeEnabled();
		await taskDetailPage.assignmentButton.focus();

		await page.screenshot({
			path: getScreenshotPath({
				baseUrl: import.meta.url,
				folder: 'managing-tasks',
				fileName: 'tasklist-with-variables-claimed-by-me_light.png',
			}),
		});
	});

	test('add a new variable', async ({network, tasklistIndexPage, taskDetailPage, page}) => {
		network.use(
			mockQueryUserTasksEndpoint({
				successResponse: HttpResponse.json(
					createQueryUserTasksResponse({items: [checkPaymentTask, assignedPassengerTask]}),
				),
			}),
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(checkPaymentTask),
			}),
			mockQueryVariablesByUserTaskEndpoint({
				successResponse: HttpResponse.json(createQueryVariablesByUserTaskResponse({items: checkPaymentVariables})),
			}),
		);

		await taskDetailPage.goto(checkPaymentTask.userTaskKey, '?filter=assigned-to-me');
		await expect(tasklistIndexPage.tasksPanelHeading('Assigned to me')).toBeVisible();
		await expect(taskDetailPage.variableValueInput('orderId')).toBeVisible();
		await taskDetailPage.addVariableButton.click();
		await taskDetailPage.firstNewVariableNameInput.fill('paymentApproved');
		await taskDetailPage.firstNewVariableValueInput.fill('true');
		await expect(taskDetailPage.completeTaskButton).toBeEnabled();
		await taskDetailPage.assignmentButton.focus();

		await page.screenshot({
			path: getScreenshotPath({
				baseUrl: import.meta.url,
				folder: 'managing-tasks',
				fileName: 'tasklist-add-new-variable.png',
			}),
		});
	});

	test('completed tasks', async ({network, tasklistIndexPage, taskDetailPage, page}) => {
		network.use(
			mockQueryUserTasksEndpoint({
				successResponse: HttpResponse.json(
					createQueryUserTasksResponse({items: [completedPassengerTask, completedCheckPaymentTask]}),
				),
			}),
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(completedPassengerTask),
			}),
			mockQueryVariablesByUserTaskEndpoint({
				successResponse: HttpResponse.json(createQueryVariablesByUserTaskResponse({items: passengerVariables})),
			}),
		);

		await taskDetailPage.goto(completedPassengerTask.userTaskKey, '?filter=completed');
		await expect(tasklistIndexPage.tasksPanelHeading('Completed')).toBeVisible();
		await expect(taskDetailPage.taskName('Register the passenger')).toBeVisible();
		await expect(taskDetailPage.completionLabel).toBeVisible();
		await expect(taskDetailPage.completeTaskButton).not.toBeVisible();
		await expect(taskDetailPage.taskTabContent.getByLabel(/^First name/)).toHaveValue('John');

		await page.screenshot({
			path: getScreenshotPath({
				baseUrl: import.meta.url,
				folder: 'managing-tasks',
				fileName: 'tasklist-task-completed_light.png',
			}),
		});
	});
});
