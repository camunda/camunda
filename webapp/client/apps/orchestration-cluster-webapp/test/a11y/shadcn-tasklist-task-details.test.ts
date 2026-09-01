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
	mockQueryVariablesByUserTaskEndpoint,
	mockQueryUserTasksEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createQueryUserTasksResponse, createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';
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
		mockGetUserTaskEndpoint({
			successResponse: HttpResponse.json(
				createUserTask({
					userTaskKey: USER_TASK_KEY,
					state: 'CREATED',
					name: 'Review purchase order',
					processName: 'Procurement process',
					elementId: 'task-1',
					assignee: 'demo',
				}),
			),
		}),
		mockQueryVariablesByUserTaskEndpoint({
			successResponse: HttpResponse.json(createQueryVariablesByUserTaskResponse()),
		}),
	);
});

test('should have no accessibility violations on the task tab with editable variables', async ({
	network,
	shadcnTaskDetailPage: taskDetailPage,
	makeAxeBuilder,
}) => {
	network.use(
		mockQueryVariablesByUserTaskEndpoint({
			successResponse: HttpResponse.json(
				createQueryVariablesByUserTaskResponse({
					items: [
						createVariable({name: 'invoiceAmount', value: '249.99', variableKey: '2251799813685301'}),
						createVariable({name: 'invoiceCurrency', value: '"EUR"', variableKey: '2251799813685302'}),
					],
				}),
			),
		}),
	);

	await taskDetailPage.goto(USER_TASK_KEY);
	await expect(taskDetailPage.variableValueInput('invoiceAmount')).toBeVisible();
	await expect(taskDetailPage.variableValueInput('invoiceCurrency')).toBeVisible();
	await taskDetailPage.addVariableButton.click();
	await expect(taskDetailPage.firstNewVariableNameInput).toBeVisible();
	await expect(taskDetailPage.firstNewVariableValueInput).toBeVisible();
	await expect(taskDetailPage.firstNewVariableRemoveButton).toBeVisible();
	await expect(taskDetailPage.fillAllVariableFieldsWarning).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations on the task tab with read-only variables', async ({
	network,
	shadcnTaskDetailPage: taskDetailPage,
	makeAxeBuilder,
}) => {
	network.use(
		mockGetUserTaskEndpoint({
			successResponse: HttpResponse.json(
				createUserTask({
					userTaskKey: USER_TASK_KEY,
					state: 'CREATED',
					name: 'Approve expense report',
					processName: 'Finance process',
					assignee: 'another-user',
				}),
			),
		}),
		mockQueryVariablesByUserTaskEndpoint({
			successResponse: HttpResponse.json(
				createQueryVariablesByUserTaskResponse({
					items: [
						createVariable({name: 'expenseAmount', value: '175.5', variableKey: '2251799813685303'}),
						createVariable({name: 'expenseStatus', value: '"approved"', variableKey: '2251799813685304'}),
					],
				}),
			),
		}),
	);

	await taskDetailPage.goto(USER_TASK_KEY);
	await expect(taskDetailPage.variablesTable.getByText('expenseAmount', {exact: true})).toBeVisible();
	await expect(taskDetailPage.variablesTable.getByText('expenseStatus', {exact: true})).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});
