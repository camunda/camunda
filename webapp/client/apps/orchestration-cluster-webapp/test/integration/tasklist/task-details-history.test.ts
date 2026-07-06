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
	mockQueryUserTaskAuditLogsEndpoint,
	mockQueryUserTasksEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createQueryUserTasksResponse, createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';
import {createAuditLog, createQueryUserTaskAuditLogsResponse} from '#/shared-test-modules/api-mocks/audit-logs';

const USER_TASK_KEY = '2251799813685281';
const task = createUserTask({
	userTaskKey: USER_TASK_KEY,
	name: 'Review purchase order',
	processName: 'Procurement process',
	assignee: 'demo',
	candidateUsers: ['alice'],
	candidateGroups: ['managers'],
});
const historyEntries = [
	createAuditLog({
		auditLogKey: 'create-log',
		operationType: 'CREATE',
		actorId: 'demo',
		timestamp: '2024-01-01T10:00:00.000Z',
	}),
	createAuditLog({
		auditLogKey: 'assign-log',
		operationType: 'ASSIGN',
		actorId: 'jane',
		relatedEntityKey: 'demo',
		timestamp: '2024-01-02T10:00:00.000Z',
	}),
	createAuditLog({
		auditLogKey: 'complete-log',
		operationType: 'COMPLETE',
		actorId: 'demo',
		timestamp: '2024-01-03T10:00:00.000Z',
	}),
];

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
			successResponse: HttpResponse.json(createQueryUserTasksResponse({items: [task]})),
		}),
		mockGetUserTaskEndpoint({
			successResponse: HttpResponse.json(task),
		}),
	);
});

test.describe('Task details history', () => {
	test('should show task history when the user opens it', async ({network, taskDetailPage, page}) => {
		network.use(
			mockQueryUserTaskAuditLogsEndpoint({
				successResponse: HttpResponse.json(createQueryUserTaskAuditLogsResponse({items: historyEntries})),
			}),
		);

		await taskDetailPage.goto(USER_TASK_KEY);
		await taskDetailPage.historyTab.click();

		await expect(page).toHaveURL(/\/tasklist\/2251799813685281\/history/);
		await expect(taskDetailPage.detailsInfo).toBeVisible();
		await expect(taskDetailPage.aside).toBeVisible();
		await expect(taskDetailPage.historyTab).toHaveAttribute('aria-current', 'page');
		await expect(taskDetailPage.historyTabContent.getByText('Create task')).toBeVisible();
		await expect(taskDetailPage.historyTabContent.getByText('Assign task')).toBeVisible();
		await expect(taskDetailPage.historyTabContent.getByText('Complete task')).toBeVisible();
		await expect(taskDetailPage.historyTabContent.getByRole('cell', {name: 'Assignee demo'})).toBeVisible();
		await expect(taskDetailPage.historyTabContent.getByText('jane')).toBeVisible();

		await taskDetailPage.gotoHistory(USER_TASK_KEY);

		await expect(taskDetailPage.historyTabContent.getByText('Create task')).toBeVisible();
		await expect(taskDetailPage.detailsInfo).toBeVisible();
	});

	test('should let the user change the history order', async ({network, taskDetailPage, page}) => {
		network.use(
			mockQueryUserTaskAuditLogsEndpoint({
				successResponse: HttpResponse.json(createQueryUserTaskAuditLogsResponse({items: historyEntries})),
			}),
		);

		await taskDetailPage.gotoHistory(USER_TASK_KEY);
		await expect(taskDetailPage.historyTabContent.getByText('Create task')).toBeVisible();

		await taskDetailPage.historyColumnHeader(/sort by operation type/i).click();
		await expect.poll(() => new URL(page.url()).searchParams.get('sort')).toBe('operationType+asc');

		await taskDetailPage.historyColumnHeader(/sort by actor/i).click();
		await expect.poll(() => new URL(page.url()).searchParams.get('sort')).toBe('actorId+asc');

		await taskDetailPage.historyColumnHeader(/sort by date/i).click();
		await expect.poll(() => new URL(page.url()).searchParams.get('sort')).toBe('timestamp+asc');

		await taskDetailPage.historyColumnHeader(/sort by date/i).click();
		await expect.poll(() => new URL(page.url()).searchParams.get('sort')).toBe(null);
	});

	test('should help the user recover when task history cannot be loaded', async ({network, taskDetailPage, page}) => {
		network.use(
			mockQueryUserTaskAuditLogsEndpoint({
				successResponse: new HttpResponse(null, {status: 500}),
			}),
		);

		await taskDetailPage.gotoHistory(USER_TASK_KEY);

		await expect(taskDetailPage.historyLoadError).toBeVisible();
		await expect(page.getByText(/could not load the task history/i)).toBeVisible();

		network.use(
			mockQueryUserTaskAuditLogsEndpoint({
				successResponse: HttpResponse.json(createQueryUserTaskAuditLogsResponse({items: historyEntries})),
			}),
		);

		await taskDetailPage.historyRetryButton.click();

		await expect(taskDetailPage.historyTabContent.getByText('Create task')).toBeVisible();
		await expect(taskDetailPage.detailsInfo).toBeVisible();
	});

	test('should tell the user when the task has no history', async ({network, taskDetailPage}) => {
		network.use(
			mockQueryUserTaskAuditLogsEndpoint({
				successResponse: HttpResponse.json(createQueryUserTaskAuditLogsResponse({items: []})),
			}),
		);

		await taskDetailPage.gotoHistory(USER_TASK_KEY);

		await expect(taskDetailPage.historyTabContent.getByText('No history entries found for this task')).toBeVisible();
		await expect(taskDetailPage.detailsInfo).toBeVisible();
	});

	test('should tell the user when they do not have permission to view task history', async ({
		network,
		page,
		taskDetailPage,
	}) => {
		network.use(
			mockQueryUserTaskAuditLogsEndpoint({
				successResponse: new HttpResponse(null, {status: 403}),
			}),
		);

		await taskDetailPage.gotoHistory(USER_TASK_KEY);

		await expect(taskDetailPage.historyForbiddenError).toBeVisible();
		await expect(page.getByText(/contact your cluster admin/i)).toBeVisible();
		await expect(page.getByRole('link', {name: /learn more about roles and permissions/i})).toBeVisible();
		await expect(taskDetailPage.historyRetryButton).not.toBeVisible();
		await expect(taskDetailPage.detailsInfo).toBeVisible();
	});
});
