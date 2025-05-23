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
import {bigFormBpmnXml} from '@/mocks/v2/bpmnXml';

test.describe('form-js integration', () => {
  test('check if Carbonization is working', async ({
    page,
    mockQueryUserTasksRequest,
    mockGetUserTaskRequest,
    mockQueryVariablesByUserTaskRequest,
    mockGetUserTaskFormRequest,
    mockGetProcessDefinitionXmlRequest,
  }) => {
    const MOCK_TASK = unassignedTask({
      userTaskKey: 'task123',
      assignee: 'demo',
      elementId: 'Activity_0aecztp',
      formKey: 'bigForm',
    });

    page.setViewportSize({
      width: 1920,
      height: 10000,
    });

    mockQueryUserTasksRequest([MOCK_TASK]);
    mockGetUserTaskRequest(MOCK_TASK);
    mockQueryVariablesByUserTaskRequest({
      userTaskKey: MOCK_TASK.userTaskKey,
    });
    mockGetUserTaskFormRequest({
      userTaskKey: MOCK_TASK.userTaskKey,
    });
    mockGetProcessDefinitionXmlRequest({
      processDefinitionKey: MOCK_TASK.processDefinitionKey,
      xml: bigFormBpmnXml,
    });

    await page.goto(`/${MOCK_TASK.userTaskKey}/`, {
      waitUntil: 'networkidle',
    });

    await expect(page.locator('.fjs-container')).toHaveScreenshot();
  });
});
