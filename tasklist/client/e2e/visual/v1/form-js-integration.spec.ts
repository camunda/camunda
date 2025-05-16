/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from '@/fixtures/v1-visual';
import {formTask} from '@/mocks/v1/task';
import {bigFormBpmnXml} from '@/mocks/v1/bpmnXml';

test.describe('form-js integration', () => {
  test('check if Carbonization is working', async ({
    page,
    mockGetTasksRequest,
    mockGetTaskRequest,
    mockGetTaskVariablesRequest,
    mockGetFormRequest,
    mockGetProcessRequest,
  }) => {
    const MOCK_TASK = formTask({
      id: 'task123',
      assignee: 'demo',
      taskDefinitionId: 'Activity_0aecztp',
    });

    page.setViewportSize({
      width: 1920,
      height: 10000,
    });

    mockGetTasksRequest([MOCK_TASK]);
    mockGetTaskRequest(MOCK_TASK);
    mockGetTaskVariablesRequest({
      taskId: MOCK_TASK.id,
    });
    mockGetFormRequest({
      formId: 'userTaskForm_1',
      processDefinitionKey: MOCK_TASK.processDefinitionKey,
    });
    mockGetProcessRequest({
      id: MOCK_TASK.processDefinitionKey,
      name: 'Big form process',
      bpmnProcessId: 'bigFormProcess',
      version: 1,
      startEventFormId: null,
      bpmnXml: bigFormBpmnXml,
    });

    await page.goto(`/${MOCK_TASK.id}/`, {
      waitUntil: 'networkidle',
    });

    await expect(page.locator('.fjs-container')).toHaveScreenshot();
  });
});
