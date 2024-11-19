/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from './test-fixtures';
import {endpoints} from '@vzeta/camunda-api-zod-schemas/tasklist';
import {apiURLPattern} from './mocks/apiURLPattern';
import {getQueryTasksResponseMock, getTask} from './mocks/tasks';
import {multiTenancyUser} from './mocks/users';
import {bigForm} from './mocks/forms';

const MOCK_TASK_V1 = {
  id: 'task123',
  formKey: 'camunda-forms:bpmn:userTaskForm_1',
  formId: null,
  formVersion: null,
  isFormEmbedded: true,
  processDefinitionKey: '2251799813685255',
  assignee: 'demo',
  name: 'Big form task',
  taskState: 'CREATED',
  processName: 'Big form process',
  creationDate: '2023-03-03T14:16:18.441+0100',
  completionDate: null,
  priority: 50,
  taskDefinitionId: 'bigFormTask',
  processInstanceKey: '4503599627371425',
  dueDate: null,
  followUpDate: null,
  candidateGroups: null,
  candidateUsers: null,
  tenantId: 'tenantA',
  context: null,
};

test.describe('a11y', () => {
  test('have no violations', async ({page, makeAxeBuilder}) => {
    await page.route(apiURLPattern, (route) => {
      const mockTask = getTask({
        elementName: 'Big form task',
        processName: 'Big form process',
      });
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
          body: JSON.stringify(getQueryTasksResponseMock([mockTask])),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      if (route.request().url().includes(`v1/tasks/${mockTask.userTaskKey}`)) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify(MOCK_TASK_V1),
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
          body: JSON.stringify(multiTenancyUser),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      return route.continue();
    });

    await page.goto('/');
    await page.getByText('Big form process').click();

    await expect(page.getByText('Title 1')).toBeVisible();

    const results = await makeAxeBuilder().analyze();

    expect(results.violations).toHaveLength(0);
    expect(results.passes.length).toBeGreaterThan(0);
  });
});
