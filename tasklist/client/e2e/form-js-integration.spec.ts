/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from './visual-fixtures';
import {apiURLPattern} from './mocks/apiURLPattern';
import {user} from './mocks/users';
import {bigFormProcess} from './mocks/processes';
import {getQueryTasksResponseMock, getTask} from './mocks/tasks';
import {endpoints} from '@vzeta/camunda-api-zod-schemas/tasklist';
import {bigForm} from './mocks/forms';

const MOCK_TASK = {
  id: 'task123',
  formKey: 'camunda-forms:bpmn:userTaskForm_1',
  processDefinitionId: '2251799813685255',
  assignee: 'demo',
  name: 'Big form task',
  taskState: 'CREATED',
  processName: 'Big form process',
  creationDate: '2023-03-03T14:16:18.441+0100',
  completionDate: null,
  priority: 50,
  processDefinitionKey: '2251799813685255',
  taskDefinitionId: 'Activity_0aecztp',
  processInstanceKey: '4503599627371425',
  dueDate: null,
  followUpDate: null,
  candidateGroups: null,
  candidateUsers: null,
  context: null,
  formId: null,
  formVersion: null,
  isFormEmbedded: true,
  tenantId: '<default>',
  implementation: 'JOB_WORKER',
};

const mockV2Task = getTask({
  elementName: 'Big form task',
  processName: 'Big form process',
});

test.describe('form-js integration', () => {
  test('check if Carbonization is working', async ({page}) => {
    page.setViewportSize({
      width: 1920,
      height: 10000,
    });

    await page.route(apiURLPattern, (route) => {
      if (route.request().url().includes('v1/tasks/task123/variables/search')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify([]),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      if (route.request().url().includes(endpoints.queryUserTasks.getUrl())) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify(getQueryTasksResponseMock([mockV2Task])),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      if (
        route.request().url().includes(`v1/tasks/${mockV2Task.userTaskKey}`)
      ) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify(MOCK_TASK),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      if (route.request().url().includes('v1/forms/userTaskForm_1')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify(bigForm),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      if (route.request().url().includes('v1/internal/users/current')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify(user),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      if (
        route.request().url().includes('v1/internal/processes/2251799813685255')
      ) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify(bigFormProcess),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      return route.continue();
    });

    await page.goto(`/${mockV2Task.userTaskKey}/`, {
      waitUntil: 'networkidle',
    });

    await expect(page.locator('.fjs-container')).toHaveScreenshot();
  });
});
