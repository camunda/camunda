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
	mockGetAuditLogEndpoint,
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
	state: 'CREATED',
	name: 'Review purchase order',
	processName: 'Procurement process',
	assignee: 'demo',
	candidateUsers: ['alice', 'bob'],
	candidateGroups: ['managers'],
	priority: 60,
	businessId: 'ORDER-2024-0042',
	creationDate: '2024-01-10T09:30:00.000Z',
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

test('should match the task history view', async ({network, taskDetailPage, page}) => {
	network.use(
		mockQueryUserTaskAuditLogsEndpoint({
			successResponse: HttpResponse.json(createQueryUserTaskAuditLogsResponse({items: historyEntries})),
		}),
	);

	await taskDetailPage.seedHideNotificationBanner();
	await taskDetailPage.gotoHistory(USER_TASK_KEY);
	await expect(taskDetailPage.historyTabContent.getByText('Create task')).toBeVisible();
	await expect(taskDetailPage.historyTabContent.getByText('Assign task')).toBeVisible();
	await expect(taskDetailPage.historyTabContent.getByText('Complete task')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the task history details modal', async ({network, taskDetailPage, page}) => {
	const auditLog = createAuditLog({
		auditLogKey: 'assign-log',
		operationType: 'ASSIGN',
		actorId: 'jane',
		relatedEntityKey: 'demo',
		timestamp: '2024-01-02T10:00:00.000Z',
	});

	network.use(
		mockQueryUserTaskAuditLogsEndpoint({
			successResponse: HttpResponse.json(createQueryUserTaskAuditLogsResponse({items: historyEntries})),
		}),
		mockGetAuditLogEndpoint({
			successResponse: HttpResponse.json(auditLog),
		}),
	);

	await taskDetailPage.seedHideNotificationBanner();
	await taskDetailPage.gotoHistoryDetails(USER_TASK_KEY, 'assign-log');
	await expect(taskDetailPage.historyDetailsModal.getByRole('heading', {name: 'Assign task'})).toBeVisible();
	await expect(taskDetailPage.historyDetailsModal.getByText('Assignee')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the empty task history view', async ({network, taskDetailPage, page}) => {
	network.use(
		mockQueryUserTaskAuditLogsEndpoint({
			successResponse: HttpResponse.json(createQueryUserTaskAuditLogsResponse({items: []})),
		}),
	);

	await taskDetailPage.seedHideNotificationBanner();
	await taskDetailPage.gotoHistory(USER_TASK_KEY);
	await expect(taskDetailPage.historyTabContent.getByText('No history entries found for this task')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the task history loading error', async ({network, taskDetailPage, page}) => {
	network.use(
		mockQueryUserTaskAuditLogsEndpoint({
			successResponse: new HttpResponse(null, {status: 500}),
		}),
	);

	await taskDetailPage.seedHideNotificationBanner();
	await taskDetailPage.gotoHistory(USER_TASK_KEY);
	await expect(taskDetailPage.historyLoadError).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the task history permission error', async ({network, taskDetailPage, page}) => {
	network.use(
		mockQueryUserTaskAuditLogsEndpoint({
			successResponse: new HttpResponse(null, {status: 403}),
		}),
	);

	await taskDetailPage.seedHideNotificationBanner();
	await taskDetailPage.gotoHistory(USER_TASK_KEY);
	await expect(taskDetailPage.historyForbiddenError).toBeVisible();

	await expect(page).toHaveScreenshot();
});
