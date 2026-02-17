/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from '@/fixtures/v2-visual';
import {unassignedTask} from '@/mocks/v2/task';
import {auditLog, auditLogs} from '@/mocks/v2/auditLogs';

test.describe('task history tab', () => {
  test('empty state', async ({
    page,
    tasksPage,
    mockQueryUserTasksRequest,
    mockGetUserTaskRequest,
    mockQueryVariablesByUserTaskRequest,
    mockGetProcessDefinitionXmlRequest,
    mockQueryUserTaskAuditLogsRequest,
  }) => {
    const TASK = unassignedTask();

    mockQueryUserTasksRequest([TASK]);
    mockGetUserTaskRequest(TASK);
    mockQueryVariablesByUserTaskRequest({
      userTaskKey: TASK.userTaskKey,
    });
    mockGetProcessDefinitionXmlRequest({
      processDefinitionKey: TASK.processDefinitionKey,
    });
    mockQueryUserTaskAuditLogsRequest({
      userTaskKey: TASK.userTaskKey,
      auditLogs: [],
    });

    await tasksPage.gotoTaskDetailsHistoryTab(TASK.userTaskKey);

    await expect(
      page.getByText('No history entries found for this task'),
    ).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('history with entries', async ({
    page,
    tasksPage,
    mockQueryUserTasksRequest,
    mockGetUserTaskRequest,
    mockQueryVariablesByUserTaskRequest,
    mockGetProcessDefinitionXmlRequest,
    mockQueryUserTaskAuditLogsRequest,
  }) => {
    const TASK = unassignedTask();

    mockQueryUserTasksRequest([TASK]);
    mockGetUserTaskRequest(TASK);
    mockQueryVariablesByUserTaskRequest({
      userTaskKey: TASK.userTaskKey,
    });
    mockGetProcessDefinitionXmlRequest({
      processDefinitionKey: TASK.processDefinitionKey,
    });
    mockQueryUserTaskAuditLogsRequest({
      userTaskKey: TASK.userTaskKey,
      auditLogs,
    });

    await tasksPage.gotoTaskDetailsHistoryTab(TASK.userTaskKey);

    await expect(page.getByText('Create task')).toBeVisible();
    await expect(page.getByText('Assign task')).toBeVisible();
    await expect(page.getByText('Complete task')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('details modal', async ({
    page,
    tasksPage,
    mockQueryUserTasksRequest,
    mockGetUserTaskRequest,
    mockQueryVariablesByUserTaskRequest,
    mockGetProcessDefinitionXmlRequest,
    mockQueryUserTaskAuditLogsRequest,
    mockGetAuditLogRequest,
  }) => {
    const TASK = unassignedTask();
    const AUDIT_LOG_KEY = '12345';
    const AUDIT_LOG = auditLog({
      auditLogKey: AUDIT_LOG_KEY,
      operationType: 'ASSIGN',
      actorId: 'jane',
      timestamp: '2024-01-15T10:30:00.000Z',
      result: 'SUCCESS',
      relatedEntityKey: 'demo',
    });

    mockQueryUserTasksRequest([TASK]);
    mockGetUserTaskRequest(TASK);
    mockQueryVariablesByUserTaskRequest({
      userTaskKey: TASK.userTaskKey,
    });
    mockGetProcessDefinitionXmlRequest({
      processDefinitionKey: TASK.processDefinitionKey,
    });
    mockQueryUserTaskAuditLogsRequest({
      userTaskKey: TASK.userTaskKey,
      auditLogs: [AUDIT_LOG],
    });
    mockGetAuditLogRequest({
      auditLogKey: AUDIT_LOG_KEY,
      auditLog: AUDIT_LOG,
    });

    await tasksPage.gotoTaskDetailsHistoryTab(TASK.userTaskKey);

    await expect(page.getByText('Assign task')).toBeVisible();

    await page.getByRole('button', {name: 'Open details'}).click();

    await expect(page.getByRole('dialog')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });
});
