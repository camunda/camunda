/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {completeTaskRequestBodySchema} from '@camunda/camunda-api-zod-schemas/8.10';
import {HttpResponse} from 'msw';
import {z} from 'zod';
import {test, expect} from '#/pw-modules/test-extend';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createQueryUserTasksResponse, createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';
import {createQueryVariablesByUserTaskResponse, createVariable} from '#/shared-test-modules/api-mocks/variables';
import {
	mockCompleteTaskEndpoint,
	mockCurrentUserEndpoint,
	mockGetUserTaskEndpoint,
	mockGetVariableEndpoint,
	mockLicenseEndpoint,
	mockQueryUserTasksEndpoint,
	mockQueryVariablesByUserTaskEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';

const USER_TASK_KEY = '2251799813685281';
const currentUser = createCurrentUser({username: 'demo'});
const assignedTask = createUserTask({userTaskKey: USER_TASK_KEY, assignee: currentUser.username, state: 'CREATED'});
const completedTask = createUserTask({
	userTaskKey: USER_TASK_KEY,
	assignee: currentUser.username,
	state: 'COMPLETED',
	completionDate: '2024-01-02T10:00:00.000Z',
});
const amountVariable = createVariable({name: 'amount', value: '100'});

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(currentUser),
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
			successResponse: HttpResponse.json(assignedTask),
		}),
		mockQueryVariablesByUserTaskEndpoint({
			successResponse: HttpResponse.json(createQueryVariablesByUserTaskResponse({items: [amountVariable]})),
		}),
	);
});

test.describe('Task details variables', () => {
	test('should validate new variable fields', async ({shadcnTaskDetailPage: taskDetailPage}) => {
		await taskDetailPage.goto(USER_TASK_KEY);
		await taskDetailPage.addVariableButton.click();
		await taskDetailPage.firstNewVariableValueInput.fill('not-json');

		await expect(taskDetailPage.missingVariableNameError).toBeVisible();
		await expect(taskDetailPage.invalidVariableValueError).toBeVisible();
		await expect(taskDetailPage.completeTaskButton).toBeDisabled();

		await taskDetailPage.firstNewVariableNameInput.fill('customer');
		await taskDetailPage.firstNewVariableValueInput.fill('{"approved":true}');

		await expect(taskDetailPage.missingVariableNameError).not.toBeVisible();
		await expect(taskDetailPage.invalidVariableValueError).not.toBeVisible();
		await expect(taskDetailPage.completeTaskButton).toBeEnabled();
	});

	test('should synchronize an assigned task variable with the JSON editor and complete the task', async ({
		network,
		shadcnTaskDetailPage: taskDetailPage,
		page,
	}) => {
		const completionSchema = completeTaskRequestBodySchema.extend({
			variables: z.object({amount: z.literal(2)}).strict(),
		});

		network.use(
			mockCompleteTaskEndpoint({
				schema: completionSchema,
				failureResponse: HttpResponse.json({error: 'Invalid completion payload'}, {status: 400}),
				successResponse: new HttpResponse(null, {status: 200}),
			}),
		);

		await taskDetailPage.goto(USER_TASK_KEY);
		await taskDetailPage.replaceVariableValue('amount', '1');
		await taskDetailPage.openJsonEditorButtons.first().click();

		await expect(taskDetailPage.jsonEditorContent('Edit Variable', '1')).toBeVisible();
		await page.keyboard.press('Delete');
		await taskDetailPage.replaceJsonEditorValue('2');
		await taskDetailPage.applyJsonEditorButton.click();

		await expect(taskDetailPage.variableValueInput('amount')).toHaveValue('2');

		network.use(
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(completedTask),
			}),
			mockQueryUserTasksEndpoint({
				successResponse: HttpResponse.json(createQueryUserTasksResponse()),
			}),
		);
		await taskDetailPage.completeTaskButton.click();

		await expect(taskDetailPage.header.notifications.getByNotificationTitle('Task completed')).toBeVisible();
		await expect(page).toHaveURL(/\/tasklist$/);
	});

	test('should show variables as read-only for an unassigned task', async ({
		network,
		shadcnTaskDetailPage: taskDetailPage,
	}) => {
		network.use(
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(
					createUserTask({userTaskKey: USER_TASK_KEY, assignee: null, state: 'CREATED'}),
				),
			}),
		);

		await taskDetailPage.goto(USER_TASK_KEY);

		await expect(taskDetailPage.variablesHeading).toBeVisible();
		await expect(taskDetailPage.variablesTable.getByText('amount')).toBeVisible();
		await expect(taskDetailPage.variablesTable.getByText('100')).toBeVisible();
		await expect(taskDetailPage.addVariableButton).toBeDisabled();
		await expect(taskDetailPage.completeTaskButton).toBeDisabled();

		await taskDetailPage.openJsonEditorButtons.first().click();

		await expect(taskDetailPage.jsonEditorDialog('View Variable')).toBeVisible();
		await expect(taskDetailPage.jsonEditorInput('View Variable')).toBeVisible();
		await expect(taskDetailPage.applyJsonEditorButton).not.toBeVisible();
	});

	test('should show variables as read-only for a completed task', async ({
		network,
		shadcnTaskDetailPage: taskDetailPage,
	}) => {
		network.use(
			mockGetUserTaskEndpoint({
				successResponse: HttpResponse.json(completedTask),
			}),
		);

		await taskDetailPage.goto(USER_TASK_KEY);

		await expect(taskDetailPage.variablesTable.getByText('amount')).toBeVisible();
		await expect(taskDetailPage.variablesTable.getByText('100')).toBeVisible();
		await expect(taskDetailPage.variableValueInput('amount')).not.toBeAttached();
		await expect(taskDetailPage.addVariableButton).not.toBeVisible();
		await expect(taskDetailPage.completeTaskButton).not.toBeVisible();
		await expect(taskDetailPage.completionLabel).toBeVisible();

		await taskDetailPage.openJsonEditorButtons.first().click();

		await expect(taskDetailPage.jsonEditorDialog('View Variable')).toBeVisible();
		await expect(taskDetailPage.applyJsonEditorButton).not.toBeVisible();
	});

	test('should load the full value of a truncated variable', async ({
		network,
		shadcnTaskDetailPage: taskDetailPage,
	}) => {
		const truncatedVariable = createVariable({
			name: 'details',
			value: '{"description":"trunc',
			isTruncated: true,
		});
		const fullVariable = createVariable({
			...truncatedVariable,
			value: '{"description":"Full value"}',
			isTruncated: false,
		});

		network.use(
			mockQueryVariablesByUserTaskEndpoint({
				successResponse: HttpResponse.json(createQueryVariablesByUserTaskResponse({items: [truncatedVariable]})),
			}),
			mockGetVariableEndpoint({
				successResponse: HttpResponse.json(fullVariable),
			}),
		);

		await taskDetailPage.goto(USER_TASK_KEY);
		await taskDetailPage.variableValueInput('details').focus();

		await expect(taskDetailPage.variableValueInput('details')).toHaveValue('{"description":"Full value"}');
	});
});
