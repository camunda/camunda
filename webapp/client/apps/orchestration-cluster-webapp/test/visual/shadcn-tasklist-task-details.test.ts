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
	mockGetProcessDefinitionXmlEndpoint,
	mockGetUserTaskEndpoint,
	mockLicenseEndpoint,
	mockQueryVariablesByUserTaskEndpoint,
	mockQueryUserTasksEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createQueryUserTasksResponse, createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';
import {BPMN_XML} from '#/shared-test-modules/api-mocks/process-definition-xmls';
import {createQueryVariablesByUserTaskResponse, createVariable} from '#/shared-test-modules/api-mocks/variables';

const USER_TASK_KEY = '2251799813685281';

test.beforeEach(({network}) => {
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
	);
});

test('should match the task details page snapshot', async ({network, shadcnTaskDetailPage, page}) => {
	network.use(
		mockGetUserTaskEndpoint({
			successResponse: HttpResponse.json(
				createUserTask({
					userTaskKey: USER_TASK_KEY,
					state: 'CREATED',
					name: 'Review purchase order',
					processName: 'Procurement process',
					assignee: 'demo',
					candidateUsers: ['alice', 'bob'],
					candidateGroups: ['managers'],
					priority: 60,
					businessId: 'ORDER-2024-0042',
					dueDate: '2024-06-15T17:00:00.000Z',
					creationDate: '2024-01-10T09:30:00.000Z',
				}),
			),
		}),
		mockQueryVariablesByUserTaskEndpoint({
			successResponse: HttpResponse.json(
				createQueryVariablesByUserTaskResponse({
					items: [
						createVariable({name: 'orderTotal', value: '249.99'}),
						createVariable({name: 'currency', value: '"EUR"', variableKey: '2251799813685284'}),
					],
				}),
			),
		}),
	);

	await shadcnTaskDetailPage.seedHideNotificationBanner();
	await shadcnTaskDetailPage.goto(USER_TASK_KEY);
	await expect(shadcnTaskDetailPage.detailsInfo).toBeVisible();
	await expect(shadcnTaskDetailPage.taskName('Review purchase order')).toBeVisible();
	await expect(shadcnTaskDetailPage.aside.getByText('ORDER-2024-0042')).toBeVisible();
	await expect(shadcnTaskDetailPage.completeTaskButton).toBeEnabled();

	await expect(page).toHaveScreenshot();
});

test('should match the new variable row snapshot', async ({network, shadcnTaskDetailPage: taskDetailPage, page}) => {
	network.use(
		mockGetUserTaskEndpoint({
			successResponse: HttpResponse.json(
				createUserTask({
					userTaskKey: USER_TASK_KEY,
					state: 'CREATED',
					name: 'Review purchase order',
					processName: 'Procurement process',
					assignee: 'demo',
				}),
			),
		}),
	);

	await taskDetailPage.seedHideNotificationBanner();
	await taskDetailPage.goto(USER_TASK_KEY);
	await taskDetailPage.addVariableButton.click();
	await expect(taskDetailPage.firstNewVariableNameInput).toBeFocused();
	await expect(taskDetailPage.firstNewVariableValueInput).toBeVisible();
	await taskDetailPage.variablesHeading.click();

	await expect(page).toHaveScreenshot();
});

test('should match the variable validation error snapshot', async ({
	network,
	shadcnTaskDetailPage: taskDetailPage,
	page,
}) => {
	network.use(
		mockGetUserTaskEndpoint({
			successResponse: HttpResponse.json(
				createUserTask({
					userTaskKey: USER_TASK_KEY,
					state: 'CREATED',
					name: 'Review purchase order',
					processName: 'Procurement process',
					assignee: 'demo',
				}),
			),
		}),
		mockQueryVariablesByUserTaskEndpoint({
			successResponse: HttpResponse.json(
				createQueryVariablesByUserTaskResponse({
					items: [createVariable({name: 'validationAmount', value: '249.99', variableKey: '2251799813685291'})],
				}),
			),
		}),
	);

	await taskDetailPage.seedHideNotificationBanner();
	await taskDetailPage.goto(USER_TASK_KEY);
	await taskDetailPage.variableValueInput('validationAmount').fill('{invalid');
	await taskDetailPage.variablesHeading.click();
	await expect(taskDetailPage.invalidVariableValueError).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the JSON editor modal snapshot', async ({network, shadcnTaskDetailPage: taskDetailPage, page}) => {
	network.use(
		mockGetUserTaskEndpoint({
			successResponse: HttpResponse.json(
				createUserTask({
					userTaskKey: USER_TASK_KEY,
					state: 'CREATED',
					name: 'Review purchase order',
					processName: 'Procurement process',
					assignee: 'demo',
				}),
			),
		}),
		mockQueryVariablesByUserTaskEndpoint({
			successResponse: HttpResponse.json(
				createQueryVariablesByUserTaskResponse({
					items: [
						createVariable({
							name: 'purchaseOrder',
							value: '{"orderId":"ORDER-2024-0042","approved":true}',
							variableKey: '2251799813685292',
						}),
					],
				}),
			),
		}),
	);

	await taskDetailPage.seedHideNotificationBanner();
	await taskDetailPage.goto(USER_TASK_KEY);
	await taskDetailPage.openJsonEditorButtons.first().click();
	await expect(taskDetailPage.jsonEditorDialog('Edit Variable')).toBeVisible();
	await expect(taskDetailPage.applyJsonEditorButton).toBeVisible();
	await expect(taskDetailPage.jsonEditorContent('Edit Variable', 'orderId')).toBeVisible();
	await taskDetailPage.jsonEditorInput('Edit Variable').evaluate((element) => element.blur());

	await expect(page).toHaveScreenshot({caret: 'hide'});
});

test('should match the task details process tab snapshot', async ({
	network,
	shadcnTaskDetailPage: taskDetailPage,
	page,
}) => {
	network.use(
		mockGetUserTaskEndpoint({
			successResponse: HttpResponse.json(
				createUserTask({
					state: 'CREATED',
					name: 'Review purchase order',
					processName: 'Procurement process',
					processDefinitionVersion: 3,
					elementId: 'task-1',
					assignee: 'demo',
					candidateUsers: ['alice', 'bob'],
					candidateGroups: ['managers'],
					priority: 60,
					businessId: 'ORDER-2024-0042',
					creationDate: '2024-01-10T09:30:00.000Z',
				}),
			),
		}),
		mockGetProcessDefinitionXmlEndpoint({
			successResponse: new HttpResponse(BPMN_XML, {headers: {'Content-Type': 'text/xml'}}),
		}),
	);

	await taskDetailPage.seedHideNotificationBanner();
	await taskDetailPage.gotoProcess('2251799813685281');
	await expect(taskDetailPage.processName('Procurement process')).toBeVisible();
	await expect(taskDetailPage.processVersion(3)).toBeVisible();
	await expect(taskDetailPage.processDiagramZoomReset).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the task details process forbidden snapshot', async ({
	network,
	shadcnTaskDetailPage: taskDetailPage,
	page,
}) => {
	network.use(
		mockGetUserTaskEndpoint({
			successResponse: HttpResponse.json(
				createUserTask({
					state: 'CREATED',
					name: 'Review purchase order',
					processName: 'Procurement process',
					assignee: 'demo',
					candidateUsers: ['alice', 'bob'],
					candidateGroups: ['managers'],
					priority: 60,
					businessId: 'ORDER-2024-0042',
					creationDate: '2024-01-10T09:30:00.000Z',
				}),
			),
		}),
		mockGetProcessDefinitionXmlEndpoint({
			successResponse: new HttpResponse(null, {status: 403}),
		}),
	);

	await taskDetailPage.seedHideNotificationBanner();
	await taskDetailPage.gotoProcess('2251799813685281');
	await expect(taskDetailPage.processForbiddenError).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the unassigned task details snapshot', async ({network, shadcnTaskDetailPage, page}) => {
	network.use(
		mockGetUserTaskEndpoint({
			successResponse: HttpResponse.json(
				createUserTask({
					state: 'CREATED',
					name: 'Review supplier onboarding',
					processName: 'Procurement process',
					assignee: null,
					candidateUsers: ['alice', 'bob'],
					candidateGroups: ['managers'],
					creationDate: '2024-01-12T09:30:00.000Z',
				}),
			),
		}),
	);

	await shadcnTaskDetailPage.seedHideNotificationBanner();
	await shadcnTaskDetailPage.goto('2251799813685281');
	await expect(shadcnTaskDetailPage.detailsInfo).toBeVisible();
	await expect(shadcnTaskDetailPage.taskName('Review supplier onboarding')).toBeVisible();
	await expect(shadcnTaskDetailPage.completeTaskButton).toBeDisabled();

	await expect(page).toHaveScreenshot();
});

test('should match the completed task details snapshot', async ({
	network,
	shadcnTaskDetailPage: taskDetailPage,
	page,
}) => {
	network.use(
		mockGetUserTaskEndpoint({
			successResponse: HttpResponse.json(
				createUserTask({
					userTaskKey: USER_TASK_KEY,
					state: 'COMPLETED',
					name: 'Approve expense report',
					processName: 'Finance process',
					assignee: 'demo',
					completionDate: '2024-02-20T16:45:00.000Z',
					creationDate: '2024-02-18T10:00:00.000Z',
					priority: 50,
				}),
			),
		}),
		mockQueryVariablesByUserTaskEndpoint({
			successResponse: HttpResponse.json(
				createQueryVariablesByUserTaskResponse({
					items: [
						createVariable({name: 'approvedAmount', value: '175.5', variableKey: '2251799813685293'}),
						createVariable({name: 'approvalStatus', value: '"approved"', variableKey: '2251799813685294'}),
					],
				}),
			),
		}),
	);

	await taskDetailPage.seedHideNotificationBanner();
	await taskDetailPage.goto(USER_TASK_KEY);
	await expect(taskDetailPage.completionLabel).toBeVisible();
	await expect(taskDetailPage.completeTaskButton).not.toBeVisible();
	await expect(taskDetailPage.variablesTable.getByText('approvedAmount', {exact: true})).toBeVisible();
	await expect(taskDetailPage.variablesTable.getByText('approvalStatus', {exact: true})).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the task details snapshot with an active transition', async ({
	network,
	shadcnTaskDetailPage,
	page,
}) => {
	network.use(
		mockGetUserTaskEndpoint({
			successResponse: HttpResponse.json(
				createUserTask({
					state: 'UPDATING',
					name: 'Sign onboarding documents',
					processName: 'HR process',
					assignee: 'demo',
					creationDate: '2024-03-05T08:00:00.000Z',
					priority: 25,
				}),
			),
		}),
	);

	await shadcnTaskDetailPage.seedHideNotificationBanner();
	await shadcnTaskDetailPage.goto('2251799813685281');
	await expect(shadcnTaskDetailPage.detailsInfo).toBeVisible();
	await expect(shadcnTaskDetailPage.taskName('Sign onboarding documents')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the task details 404 page snapshot', async ({network, shadcnTaskDetailPage, notFoundPage, page}) => {
	network.use(
		mockGetUserTaskEndpoint({
			successResponse: new HttpResponse(null, {status: 404}),
		}),
	);

	await shadcnTaskDetailPage.goto(USER_TASK_KEY);
	await expect(notFoundPage.heading).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the task details forbidden page snapshot', async ({
	network,
	shadcnTaskDetailPage,
	forbiddenPage,
	page,
}) => {
	network.use(
		mockGetUserTaskEndpoint({
			successResponse: new HttpResponse(null, {status: 403}),
		}),
	);

	await shadcnTaskDetailPage.goto(USER_TASK_KEY);
	await expect(forbiddenPage.heading).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the task details generic error page snapshot', async ({network, shadcnTaskDetailPage, page}) => {
	network.use(
		mockGetUserTaskEndpoint({
			successResponse: new HttpResponse(null, {status: 500}),
		}),
	);

	await shadcnTaskDetailPage.goto(USER_TASK_KEY);
	await expect(page.getByRole('heading', {name: 'Something went wrong'})).toBeVisible();

	await expect(page).toHaveScreenshot();
});
